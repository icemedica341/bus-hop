package com.bushop.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bushop.data.local.BusStopIndex
import com.bushop.domain.api.UpdateChecker
import com.bushop.domain.model.BusStop
import com.bushop.domain.model.BusStopEntry
import com.bushop.domain.model.BusStopWithArrivals
import com.bushop.domain.model.ColorSchemeOption
import com.bushop.domain.model.DuplicateStopException
import com.bushop.domain.model.NetworkResult
import com.bushop.domain.model.ThemeMode
import com.bushop.domain.repository.BusRepository
import com.bushop.domain.usecase.AutoRefreshController
import com.bushop.domain.usecase.BusStopUseCase
import com.bushop.domain.usecase.StopRefreshCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Tracks the health of the external bus arrival API. */
enum class ApiStatus { Healthy, Degraded, Down }

class MainViewModel(
    application: android.app.Application,
    private val repository: BusRepository,
    private val busStopIndex: BusStopIndex,
    private val updateChecker: UpdateChecker,
    private val useCase: BusStopUseCase = BusStopUseCase(),
    private val refreshCoordinator: StopRefreshCoordinator = StopRefreshCoordinator(),
) : AndroidViewModel(application) {
    /** Delegated theme and colour-scheme management. */
    val themeManager = ThemeManager(viewModelScope, repository)

    /** Delegated update check, download, and install. */
    val updateManager =
        UpdateManager(viewModelScope, updateChecker, repository) { msg -> _snackbarMessage.tryEmit(msg) }

    companion object {
        private const val DEFAULT_AUTO_REFRESH_INTERVAL = 30
        private const val COLLAPSE_DEBOUNCE_MS = 500L
        private const val REFRESH_COOLDOWN_MS = 400L
        private const val DEGRADED_THRESHOLD = 3
        private const val DOWN_THRESHOLD = 10
    }

    private val isAutoRefreshing = AtomicBoolean(false)
    private var searchJob: Job? = null

    private val autoRefreshController = AutoRefreshController(viewModelScope)

    private val _pinnedServices = MutableStateFlow<Set<String>>(emptySet())
    val pinnedServices: StateFlow<Set<String>> = _pinnedServices.asStateFlow()

    private val _pinnedStops = MutableStateFlow<Set<String>>(emptySet())
    val pinnedStops: StateFlow<Set<String>> = _pinnedStops.asStateFlow()

    private val _savedStops = MutableStateFlow<List<BusStopWithArrivals>>(emptyList())
    val savedStops: StateFlow<List<BusStopWithArrivals>> = _savedStops.asStateFlow()

    var addStopDialogVisible by mutableStateOf(false)
        private set

    var randomHint by mutableStateOf("")
        private set

    var addStopError by mutableStateOf<String?>(null)
        private set

    private var saveCollapseJob: Job? = null
    var autoRefreshIntervalSeconds by mutableStateOf(DEFAULT_AUTO_REFRESH_INTERVAL)
        private set

    private val _sortByEarliest = MutableStateFlow(false)
    val sortByEarliest: StateFlow<Boolean> = _sortByEarliest.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    var addStopIsLoading by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var lastUpdatedAll by mutableStateOf(0L)
        private set

    // ── API health tracking ──

    private val _apiStatus = MutableStateFlow(ApiStatus.Healthy)
    val apiStatus: StateFlow<ApiStatus> = _apiStatus.asStateFlow()

    private val consecutiveFailures = AtomicInteger(0)

    fun dismissApiBanner() {
        _apiStatus.value = ApiStatus.Healthy
    }

    // ── Theme (delegated to ThemeManager) ──

    val themeModeFlow: StateFlow<ThemeMode> get() = themeManager.themeModeFlow
    val colorSchemeOptionFlow: StateFlow<ColorSchemeOption> get() = themeManager.colorSchemeOptionFlow

    fun setThemeMode(mode: ThemeMode) = themeManager.setThemeMode(mode)

    fun setColorSchemeOption(option: ColorSchemeOption) = themeManager.setColorSchemeOption(option)

    // ── Index readiness ──

    val isIndexReady: StateFlow<Boolean> = repository.isIndexReady

    private val _searchResults = MutableStateFlow<List<BusStopEntry>>(emptyList())
    val searchResults: StateFlow<List<BusStopEntry>> = _searchResults.asStateFlow()

    private var additionOrder: List<String> = emptyList()

    private val _recentlyAddedStop = MutableStateFlow<String?>(null)
    val recentlyAddedStop: StateFlow<String?> = _recentlyAddedStop.asStateFlow()

    fun clearNewStopHighlight() {
        _recentlyAddedStop.value = null
    }

    // ── Nearby stops ──

    var nearbyStops by mutableStateOf<List<BusStopEntry>>(emptyList())
        private set
    var isLoadingNearby by mutableStateOf(false)
        private set
    var nearbyError by mutableStateOf<String?>(null)
        private set

    fun clearNearby() {
        nearbyStops = emptyList()
        nearbyError = null
    }

    fun findNearbyStops() {
        if (isLoadingNearby) return
        isLoadingNearby = true
        nearbyError = null
        viewModelScope.launch {
            try {
                val ctx = getApplication<android.app.Application>()
                val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
                val providers = lm?.getProviders(true) ?: emptyList()
                if (providers.isEmpty()) {
                    nearbyError = "Location is disabled. Enable GPS or Wi-Fi scanning."
                    return@launch
                }
                val location = providers.firstNotNullOfOrNull { lm?.getLastKnownLocation(it) }
                if (location == null) {
                    nearbyError = "Could not get current location. Try again later."
                    return@launch
                }
                nearbyStops = repository.findNearbyStops(location.latitude, location.longitude)
                if (nearbyStops.isEmpty()) {
                    nearbyError = "No bus stops found nearby."
                }
            } catch (e: SecurityException) {
                nearbyError = "Location permission denied."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                nearbyError = "Could not find nearby stops. Please try again."
            } finally {
                isLoadingNearby = false
            }
        }
    }

    // ── Update checker (delegated to UpdateManager) ──

    var updateInfo get() = updateManager.updateInfo
        set(v) {
            updateManager.updateInfo = v
        }
    var isCheckingUpdate get() = updateManager.isCheckingUpdate
        set(v) {
            updateManager.isCheckingUpdate = v
        }
    var isDownloadingUpdate get() = updateManager.isDownloadingUpdate
        set(v) {
            updateManager.isDownloadingUpdate = v
        }
    var hasSeenDragHint get() = updateManager.hasSeenDragHint
        set(v) {
            updateManager.hasSeenDragHint = v
        }

    fun dismissHint() = updateManager.dismissHint()

    fun checkForUpdate() = updateManager.checkForUpdate()

    fun downloadAndInstallUpdate() = updateManager.downloadAndInstallUpdate()

    init {
        // Restore persisted preferences from delegates
        themeManager.observePersisted()
        updateManager.observePersisted()

        viewModelScope.launch {
            try {
                autoRefreshIntervalSeconds = repository.getAutoRefreshIntervalOnce()
                if (autoRefreshIntervalSeconds > 0) {
                    autoRefreshController.start(autoRefreshIntervalSeconds) { refreshAll(isAutoRefresh = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // ignored
            }
        }
        viewModelScope.launch {
            try {
                repository.pinnedServicesFlow.collect { pinned ->
                    _pinnedServices.value = pinned
                }
            } catch (
                e: CancellationException,
            ) {
                throw e
            } catch (_: Exception) {
                // flow ended
            }
        }
        viewModelScope.launch {
            try {
                repository.pinnedStopsFlow.collect { pinned ->
                    _pinnedStops.value = pinned
                }
            } catch (
                e: CancellationException,
            ) {
                throw e
            } catch (_: Exception) {
                // flow ended
            }
        }

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
                                isPinned = existing?.isPinned ?: (stop.code in _pinnedStops.value),
                            )
                        }
                    useCase.applyPersistedCollapsedState(mergedStops, collapsedStops) to sortByEarliest
                }
            combine(baseFlow, _pinnedServices) { (stops, sortByEarliest), pinned ->
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
                additionOrder = list.map { it.busStop.code }
                lastUpdatedAll = list.maxOfOrNull { it.lastUpdated } ?: lastUpdatedAll
                val pinnedFirst = list.sortedByDescending { it.isPinned }
                _savedStops.value = pinnedFirst
                if (pinnedFirst.isNotEmpty() && !isAutoRefreshing.get() && pinnedFirst.any { it.services.isEmpty() || it.isStale }) {
                    refreshAll(isAutoRefresh = true)
                }
            }
        }
    }

    fun showAddStopDialog() {
        addStopError = null
        addStopDialogVisible = true
        // Pick a random stop from the index once it's loaded (no copy, O(1))
        viewModelScope.launch {
            repository.isIndexReady.first { it }
            busStopIndex.randomEntry()?.let {
                randomHint = "${it.code} (${it.name})"
            }
        }
    }

    fun searchBusStops(query: String) {
        addStopError = null
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch(Dispatchers.Default) {
                val results = repository.searchBusStops(query)
                _searchResults.value = results
            }
    }

    fun findBusStopByCode(code: String) = repository.findBusStopByCode(code)

    fun hideAddStopDialog() {
        addStopDialogVisible = false
        addStopError = null
        addStopIsLoading = false
    }

    fun addBusStop(
        code: String,
        name: String = "",
    ) {
        // Guard against rapid double-taps
        if (addStopIsLoading) return

        viewModelScope.launch {
            val formattedCode = code.trim()
            if (formattedCode.length == 5 && formattedCode.all { it.isDigit() }) {
                addStopIsLoading = true
                addStopError = null

                // Try local lookup first — allows offline save for known stops
                val localEntry = repository.findBusStopByCode(formattedCode)

                if (localEntry == null) {
                    // Not found locally — network validation required
                    when (val arrivalResult = getBusArrivalsSafely(formattedCode)) {
                        is NetworkResult.Error -> {
                            addStopError = "Could not verify bus stop (${arrivalResult.message})."
                            addStopIsLoading = false
                            return@launch
                        }

                        is NetworkResult.Success -> {
                            consecutiveFailures.set(0)
                            _apiStatus.value = ApiStatus.Healthy
                        }
                    }
                }

                val stopName = name.ifBlank { localEntry?.name ?: "" }
                val result =
                    try {
                        repository.addBusStop(BusStop(code = formattedCode, name = stopName))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        addStopError = "Failed to save bus stop. Please try again."
                        addStopIsLoading = false
                        return@launch
                    }
                if (result.isFailure) {
                    if (result.exceptionOrNull() is DuplicateStopException) {
                        addStopError = "Bus stop already exists"
                    } else {
                        addStopError = result.exceptionOrNull()?.message ?: "Failed to add stop"
                    }
                    addStopIsLoading = false
                    return@launch
                }
                collapseStop(formattedCode)
                addStopIsLoading = false
                hideAddStopDialog()
                _recentlyAddedStop.value = formattedCode

                // Background validation for locally-found stops — failure is OK
                if (localEntry != null) {
                    viewModelScope.launch {
                        getBusArrivalsSafely(formattedCode)
                        // Result intentionally ignored — stop remains saved
                    }
                }
            } else {
                addStopError = "Invalid bus stop code"
            }
        }
    }

    private suspend fun getBusArrivalsSafely(code: String): NetworkResult<List<com.bushop.domain.model.BusService>> =
        try {
            repository.getBusArrivals(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Unexpected error", e)
        }

    fun removeBusStop(code: String) {
        // Update state immediately (don't wait for DataStore flow)
        val removedStop = _savedStops.value.find { it.busStop.code == code }
        _savedStops.value = _savedStops.value.filter { it.busStop.code != code }
        viewModelScope.launch {
            try {
                repository.removeBusStop(code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (removedStop != null) {
                    _savedStops.value = _savedStops.value + removedStop
                }
                _snackbarMessage.tryEmit("Failed to remove bus stop")
            }
        }
    }

    /** Multi-position move (used for final drop in free-form drag). */
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
        additionOrder = list.map { it.busStop.code }
        viewModelScope.launch { repository.reorderStops(list.map { it.busStop }) }
    }

    private suspend fun refreshArrivalsInternal(
        code: String,
        isAutoRefresh: Boolean,
    ) {
        // Set loading state before API call (for manual refresh only)
        if (!isAutoRefresh) {
            val idx = _savedStops.value.indexOfFirst { it.busStop.code == code }
            if (idx == -1) return
            _savedStops.value =
                _savedStops.value.toMutableList().apply {
                    // Creates a new list + copies one element — acceptable for infrequent mutations
                    this[idx] = this[idx].copy(isLoading = true, error = null, isOffline = false)
                }
        }

        val result = getBusArrivalsSafely(code)

        // Re-compute index — list may have changed during suspension
        val index = _savedStops.value.indexOfFirst { it.busStop.code == code }
        if (index == -1) return

        when (result) {
            is NetworkResult.Error -> {
                val isOffline = result.exception is IOException
                val failures = consecutiveFailures.incrementAndGet()
                _apiStatus.value =
                    when {
                        failures >= DOWN_THRESHOLD -> ApiStatus.Down
                        failures >= DEGRADED_THRESHOLD -> ApiStatus.Degraded
                        else -> _apiStatus.value
                    }
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
                _apiStatus.value = ApiStatus.Healthy
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
        // Manual refresh: always show visual feedback, skip only the API call if in cooldown
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

    fun setAutoRefreshInterval(seconds: Int) {
        autoRefreshIntervalSeconds = seconds
        viewModelScope.launch {
            repository.setAutoRefreshInterval(seconds)
        }
        if (seconds > 0) {
            autoRefreshController.start(seconds) { refreshAll(isAutoRefresh = true) }
        } else {
            autoRefreshController.stop()
        }
    }

    fun toggleThemeMode() = themeManager.toggleThemeMode()

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

    private fun collapseStop(code: String) {
        val (updated, collapsedCodes) = useCase.collapseStop(_savedStops.value, code)
        _savedStops.value = updated
        persistCollapsedStops(collapsedCodes)
    }

    private fun persistCollapsedStops(collapsedCodes: Set<String>) {
        // Debounce collapse state persistence (500ms)
        saveCollapseJob?.cancel()
        saveCollapseJob =
            viewModelScope.launch {
                delay(COLLAPSE_DEBOUNCE_MS)
                repository.setCollapsedStops(collapsedCodes.toSet())
            }
    }

    fun togglePinService(
        stopCode: String,
        serviceNo: String,
    ) {
        val key = "$stopCode:$serviceNo"
        val wasPinned = key in _pinnedServices.value
        val updated =
            _pinnedServices.value.toMutableSet().apply {
                if (contains(key)) remove(key) else add(key)
            }
        _pinnedServices.value = updated
        viewModelScope.launch {
            repository.savePinnedServices(updated)
        }
        _snackbarMessage.tryEmit(
            if (!wasPinned) {
                "Pinned bus $serviceNo"
            } else {
                "Unpinned bus $serviceNo"
            },
        )
    }

    /** Get service numbers pinned for a specific stop (strip the "$code:" prefix). */
    private fun pinnedServiceNosForStop(stopCode: String): Set<String> =
        _pinnedServices.value
            .filter { it.startsWith("$stopCode:") }
            .map { it.substringAfter(":") }
            .toSet()

    fun isServicePinned(
        stopCode: String,
        serviceNo: String,
    ): Boolean = "$stopCode:$serviceNo" in _pinnedServices.value

    fun togglePin(code: String) {
        val index = _savedStops.value.indexOfFirst { it.busStop.code == code }
        if (index != -1) {
            val wasPinned = _savedStops.value[index].isPinned
            val nowPinned = !wasPinned
            _savedStops.value =
                _savedStops.value
                    .toMutableList()
                    .apply {
                        this[index] = this[index].copy(isPinned = nowPinned)
                    }.let { list ->
                        useCase.applyPinning(list, wasPinned, additionOrder)
                    }
            // Persist pinned state
            val updatedPinned =
                _pinnedStops.value.toMutableSet().apply {
                    if (nowPinned) add(code) else remove(code)
                }
            _pinnedStops.value = updatedPinned
            viewModelScope.launch { repository.savePinnedStops(updatedPinned) }

            val stopName =
                _savedStops.value
                    .find { it.busStop.code == code }
                    ?.busStop
                    ?.name ?: code
            _snackbarMessage.tryEmit(
                if (!wasPinned) {
                    "Pinned stop $stopName"
                } else {
                    "Unpinned stop $stopName"
                },
            )
        }
    }

    // ── Lifecycle-aware auto-refresh ──

    fun pauseAutoRefresh() {
        autoRefreshController.stop()
    }

    fun resumeAutoRefresh() {
        // Refresh immediately when app comes to foreground
        refreshAll(isAutoRefresh = true)
        // Then restart the timer for subsequent refreshes
        if (autoRefreshIntervalSeconds > 0) {
            autoRefreshController.start(autoRefreshIntervalSeconds) { refreshAll(isAutoRefresh = true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshController.stop()
    }

    class Factory(
        private val application: android.app.Application,
        private val repository: BusRepository,
        private val busStopIndex: BusStopIndex,
        private val updateChecker: UpdateChecker,
        private val useCase: BusStopUseCase = BusStopUseCase(),
        private val refreshCoordinator: StopRefreshCoordinator = StopRefreshCoordinator(),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(application, repository, busStopIndex, updateChecker, useCase, refreshCoordinator) as T
    }
}
