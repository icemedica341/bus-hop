package com.bushop.ui.screens

import com.bushop.domain.model.BusStopWithArrivals
import com.bushop.domain.repository.BusRepository
import com.bushop.domain.usecase.BusStopUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages pinned services and pinned stops state.
 * Receives pin state from repository flows and provides mutation methods.
 */
internal class PinManager(
    private val viewModelScope: CoroutineScope,
    private val repository: BusRepository,
    private val snackbarMessage: MutableSharedFlow<String>,
) {
    private val _pinnedServices = MutableStateFlow<Set<String>>(emptySet())
    val pinnedServices: StateFlow<Set<String>> = _pinnedServices.asStateFlow()

    private val _pinnedStops = MutableStateFlow<Set<String>>(emptySet())
    val pinnedStops: StateFlow<Set<String>> = _pinnedStops.asStateFlow()

    init {
        // Observe persisted pin state from repository
        viewModelScope.launch {
            try {
                repository.pinnedServicesFlow.collect { pinned ->
                    _pinnedServices.value = pinned
                }
            } catch (e: CancellationException) {
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // flow ended
            }
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
        snackbarMessage.tryEmit(
            if (!wasPinned) {
                "Pinned bus $serviceNo"
            } else {
                "Unpinned bus $serviceNo"
            },
        )
    }

    /** Get service numbers pinned for a specific stop (strip the "$code:" prefix). */
    fun pinnedServiceNosForStop(stopCode: String): Set<String> = _pinnedServices.value
        .filter { it.startsWith("$stopCode:") }
        .map { it.substringAfter(":") }
        .toSet()

    fun isServicePinned(
        stopCode: String,
        serviceNo: String,
    ): Boolean = "$stopCode:$serviceNo" in _pinnedServices.value

    /**
     * Toggles the pinned state of a stop.
     * @return the updated saved stops list, or the original list if the stop was not found.
     */
    fun togglePin(
        code: String,
        savedStops: List<BusStopWithArrivals>,
        additionOrder: List<String>,
        useCase: BusStopUseCase,
    ): List<BusStopWithArrivals> {
        val index = savedStops.indexOfFirst { it.busStop.code == code }
        if (index == -1) return savedStops

        val wasPinned = savedStops[index].isPinned
        val nowPinned = !wasPinned
        val updatedStops =
            savedStops
                .toMutableList()
                .apply {
                    this[index] = this[index].copy(isPinned = nowPinned)
                }.let { list ->
                    useCase.applyPinning(list, wasPinned, additionOrder)
                }

        val updatedPinned =
            _pinnedStops.value.toMutableSet().apply {
                if (nowPinned) add(code) else remove(code)
            }
        _pinnedStops.value = updatedPinned
        viewModelScope.launch { repository.savePinnedStops(updatedPinned) }

        val stopName =
            savedStops
                .find { it.busStop.code == code }
                ?.busStop
                ?.name ?: code
        snackbarMessage.tryEmit(
            if (!wasPinned) {
                "Pinned stop $stopName"
            } else {
                "Unpinned stop $stopName"
            },
        )
        return updatedStops
    }
}
