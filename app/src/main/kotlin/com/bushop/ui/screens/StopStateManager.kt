package com.bushop.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bushop.domain.model.BusService
import com.bushop.domain.model.BusStopWithArrivals
import com.bushop.domain.model.NetworkResult
import com.bushop.domain.repository.BusRepository
import com.bushop.domain.usecase.BusStopUseCase
import com.bushop.domain.usecase.StopRefreshCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages the saved stops list, refresh logic, collapse state, and sort preference.
 * Owns the main combine flow that drives the UI's saved stops list.
 */
class StopStateManager(
    private val viewModelScope: CoroutineScope,
    private val repository: BusRepository,
    private val useCase: BusStopUseCase,
    private val refreshCoordinator: StopRefreshCoordinator,
    private val pinnedStops: StateFlow<Set<String>>,
    private val pinnedServices: StateFlow<Set<String>>,
    private val onApiStatusUpdate: (ApiStatus) -> Unit,
    private val consecutiveFailures: AtomicInteger,
    private val isAutoRefreshing: AtomicBoolean,
    private val onAdditionOrderChanged: (List<String>) -> Unit = {},
) {
    companion object {
        const val COLLAPSE_DEBOUNCE_MS = 500L
        const val REFRESH_COOLDOWN_MS = 400L
        private const val DEGRADED_THRESHOLD = 3
        private const val DOWN_THRESHOLD = 10
    }

    private val _savedStops = MutableStateFlow<List<BusStopWithArrivals>>(emptyList())
    val savedStops: StateFlow<List<BusStopWithArrivals>> = _savedStops.asStateFlow()

    private var saveCollapseJob: Job? = null

    var isRefreshing by mutableStateOf(false)
        private set

    var lastUpdatedAll by mutableStateOf(0L)
        private set

    private val _sortByEarliest = MutableStateFlow(false)
    val sortByEarliest: StateFlow<Boolean> = _sortByEarliest.asStateFlow()

    /** Replace the saved stops list (used by PinManager via MainViewModel). */
    fun replaceSavedStops(list: List<BusStopWithArrivals>) {
        _savedStops.value = list
    }

    /** Internal helper that the outside can call to get a snapshot of the current list. */
    fun currentSavedStops(): List<BusStopWithArrivals> = _savedStops.value

    /** Remove a stop and return it (for optimistic removal with rollback). */
    fun removeStopImmediately(code: String): BusStopWithArrivals? {
        val removedStop = _savedStops.value.find { it.busStop.code == code }
        _savedStops.value = _savedStops.value.filter { it.busStop.code != code }
        return removedStop
    }

    /** Add a stop back (for rollback after failed repository removal). */
    fun addStopBack(stop: BusStopWithArrivals) {
        _savedStops.value = _savedStops.value + stop
    }

    init {
        // Main combine flow: merges saved stops, cached services, timestamps,
        // collapsed state, sort order, and pin state into the UI list.
        viewModelScope.launch {
            val baseFlow =
                combine(
                    repository.savedBusStops,
                    repository.cachedBusServices,
                    repository.cachedTimestamps,
                    repository.collapsedStopsFlow,
                    _sortByEarliest,
                ) { stops, cached, timestamps, collapsedStops, sortByEarliest ->
                    val mergedStops =
                        stops.map { stop ->
                            val cachedServices = cached[stop.code] ?: emptyList()
                            val existing = _savedStops.value.find { it.busStop.code == stop.code }
                            BusStopWithArrivals(
                                busStop = stop,
                                services = cachedServices,
                                isLoading = existing?.isLoading ?: false,
                                error = existing?.error,
                                isOffline = existing?.isOffline ?: false,
                                lastUpdated = existing?.lastUpdated ?: 0L,
                                cachedAt = timestamps[stop.code] ?: 0L,
                                isCollapsed = existing?.isCollapsed ?: false,
                                isPinned = existing?.isPinned ?: (stop.code in pinnedStops.value),
                            )
                        }
                    useCase.applyPersistedCollapsedState(mergedStops, collapsedStops) to sortByEarliest
                }
            combine(baseFlow, pinnedServices) { (stops, sortByEarliest), pinned ->
                stops.map { stopWithArrivals ->
                    val pinnedForStop = pinnedServiceNosForStop(stopWithArrivals.busStop.code)
                    stopWithArrivals.copy(
                        services =
                        useCase.sortServicesWithPins(
                            stopWithArrivals.services,
                            pinnedForStop,
                            sortByEarliest,
                        ),
                    )
                }
            }.collect { list ->
                onAdditionOrderChanged(list.map { it.busStop.code })
                lastUpdatedAll = list.maxOfOrNull { it.lastUpdated } ?: lastUpdatedAll
                val pinnedFirst = list.sortedByDescending { it.isPinned }
                _savedStops.value = pinnedFirst
                if (pinnedFirst.isNotEmpty() && !isAutoRefreshing.get() && pinnedFirst.any { it.services.isEmpty() || it.isStale }) {
                    refreshAll(isAutoRefresh = true)
                }
            }
        }
    }

    private fun pinnedServiceNosForStop(stopCode: String): Set<String> = pinnedServices.value
        .filter { it.startsWith("$stopCode:") }
        .map { it.substringAfter(":") }
        .toSet()

    private suspend fun getBusArrivalsSafely(code: String): NetworkResult<List<BusService>> = try {
        repository.getBusArrivals(code)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        NetworkResult.Error(e.message ?: "Unexpected error", e)
    }

    private suspend fun refreshArrivalsInternal(
        code: String,
        isAutoRefresh: Boolean,
    ) {
        if (!isAutoRefresh) {
            val idx = _savedStops.value.indexOfFirst { it.busStop.code == code }
            if (idx == -1) return
            _savedStops.value =
                _savedStops.value.toMutableList().apply {
                    this[idx] = this[idx].copy(isLoading = true, error = null, isOffline = false)
                }
        }

        val result = getBusArrivalsSafely(code)

        val index = _savedStops.value.indexOfFirst { it.busStop.code == code }
        if (index == -1) return

        when (result) {
            is NetworkResult.Error -> {
                val isOffline = result.exception is IOException
                val failures = consecutiveFailures.incrementAndGet()
                onApiStatusUpdate(
                    when {
                        failures >= DOWN_THRESHOLD -> ApiStatus.Down
                        failures >= DEGRADED_THRESHOLD -> ApiStatus.Degraded
                        else -> ApiStatus.Healthy
                    },
                )
                if (!isAutoRefresh) {
                    _savedStops.value =
                        _savedStops.value.toMutableList().apply {
                            this[index] =
                                this[index].copy(
                                    isLoading = false,
                                    error = if (isOffline) null else result.message,
                                    isOffline = isOffline,
                                )
                        }
                } else {
                    _savedStops.value =
                        _savedStops.value.toMutableList().apply {
                            this[index] = this[index].copy(isLoading = false)
                        }
                }
            }

            is NetworkResult.Success -> {
                consecutiveFailures.set(0)
                onApiStatusUpdate(ApiStatus.Healthy)
                val pinnedForStop = pinnedServiceNosForStop(code)
                val sortedServices = useCase.sortServicesWithPins(result.data, pinnedForStop, _sortByEarliest.value)
                _savedStops.value =
                    _savedStops.value.toMutableList().apply {
                        this[index] =
                            this[index].copy(
                                services = sortedServices,
                                isLoading = false,
                                error = null,
                                isOffline = false,
                                lastUpdated = System.currentTimeMillis(),
                            )
                    }
                lastUpdatedAll = System.currentTimeMillis()
            }
        }
    }

    fun refreshArrivals(
        code: String,
        isAutoRefresh: Boolean = false,
    ) {
        viewModelScope.launch {
            if (refreshCoordinator.tryRefresh(code, isAutoRefresh)) {
                refreshArrivalsInternal(code, isAutoRefresh)
            }
        }
    }

    fun refreshAll(isAutoRefresh: Boolean = false) {
        if (isAutoRefresh) {
            if (!isAutoRefreshing.compareAndSet(false, true)) return
            viewModelScope.launch {
                try {
                    refreshCoordinator.refreshAllConcurrent(
                        codes = _savedStops.value.map { it.busStop.code },
                        isAutoRefresh = true,
                        refreshBlock = { refreshArrivalsInternal(it, true) },
                    )
                } finally {
                    isAutoRefreshing.set(false)
                }
            }
            return
        }
        isRefreshing = true
        lastUpdatedAll = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                refreshCoordinator.refreshAllConcurrent(
                    codes = _savedStops.value.map { it.busStop.code },
                    isAutoRefresh = false,
                    refreshBlock = { refreshArrivalsInternal(it, false) },
                )
                delay(REFRESH_COOLDOWN_MS)
            } finally {
                isRefreshing = false
            }
        }
    }

    fun moveStop(
        code: String,
        delta: Int,
    ) {
        if (delta == 0) return
        val list = _savedStops.value.toMutableList()
        val fromIdx = list.indexOfFirst { it.busStop.code == code }
        if (fromIdx == -1) return
        val item = list.removeAt(fromIdx)
        val toIdx = (fromIdx + delta).coerceIn(0, list.size)
        if (fromIdx == toIdx) return
        list.add(toIdx, item)
        _savedStops.value = list
        onAdditionOrderChanged(list.map { it.busStop.code })
        viewModelScope.launch { repository.reorderStops(list.map { it.busStop }) }
    }

    fun toggleSortOrder() {
        val newValue = !_sortByEarliest.value
        _sortByEarliest.value = newValue
        viewModelScope.launch {
            repository.setSortByEarliest(newValue)
        }
    }

    fun toggleCollapse(code: String) {
        val (updated, collapsedCodes) = useCase.toggleCollapsed(_savedStops.value, code)
        _savedStops.value = updated
        persistCollapsedStops(collapsedCodes.toSet())
    }

    fun collapseStop(code: String) {
        val (updated, collapsedCodes) = useCase.collapseStop(_savedStops.value, code)
        _savedStops.value = updated
        persistCollapsedStops(collapsedCodes)
    }

    private fun persistCollapsedStops(collapsedCodes: Set<String>) {
        saveCollapseJob?.cancel()
        saveCollapseJob =
            viewModelScope.launch {
                delay(COLLAPSE_DEBOUNCE_MS)
                repository.setCollapsedStops(collapsedCodes.toSet())
            }
    }
}
