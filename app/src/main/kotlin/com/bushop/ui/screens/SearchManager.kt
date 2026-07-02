package com.bushop.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bushop.data.local.BusStopIndex
import com.bushop.domain.model.BusService
import com.bushop.domain.model.BusStop
import com.bushop.domain.model.BusStopEntry
import com.bushop.domain.model.DuplicateStopException
import com.bushop.domain.model.NetworkResult
import com.bushop.domain.repository.BusRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manages the add-bus-stop dialog state, search results, and stop addition logic.
 */
internal class SearchManager(
    private val viewModelScope: CoroutineScope,
    private val repository: BusRepository,
    private val busStopIndex: BusStopIndex,
    private val onApiHealthy: () -> Unit = {},
    private val onStopAdded: (code: String) -> Unit = {},
) {
    var addStopDialogVisible by mutableStateOf(false)
        private set

    var randomHint by mutableStateOf("")
        private set

    var addStopError by mutableStateOf<String?>(null)
        private set

    var addStopIsLoading by mutableStateOf(false)
        private set

    private val _searchResults = MutableStateFlow<List<BusStopEntry>>(emptyList())
    val searchResults: StateFlow<List<BusStopEntry>> = _searchResults.asStateFlow()

    var searchJob: Job? = null
        private set

    private val _recentlyAddedStop = MutableStateFlow<String?>(null)
    val recentlyAddedStop: StateFlow<String?> = _recentlyAddedStop.asStateFlow()

    fun showAddStopDialog() {
        addStopError = null
        addStopDialogVisible = true
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
        if (addStopIsLoading) return
        viewModelScope.launch {
            val formattedCode = code.trim()
            if (formattedCode.length == 5 && formattedCode.all { it.isDigit() }) {
                addStopIsLoading = true
                addStopError = null

                val localEntry = repository.findBusStopByCode(formattedCode)

                if (localEntry == null) {
                    when (val arrivalResult = getBusArrivalsSafely(formattedCode)) {
                        is NetworkResult.Error -> {
                            addStopError = "Could not verify bus stop (${arrivalResult.message})."
                            addStopIsLoading = false
                            return@launch
                        }

                        is NetworkResult.Success -> {
                            onApiHealthy()
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
                onStopAdded(formattedCode)
                addStopIsLoading = false
                hideAddStopDialog()
                _recentlyAddedStop.value = formattedCode

                if (localEntry != null) {
                    viewModelScope.launch {
                        getBusArrivalsSafely(formattedCode)
                    }
                }
            } else {
                addStopError = "Invalid bus stop code"
            }
        }
    }

    private suspend fun getBusArrivalsSafely(code: String): NetworkResult<List<BusService>> = try {
        repository.getBusArrivals(code)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        NetworkResult.Error(e.message ?: "Unexpected error", e)
    }

    fun clearNewStopHighlight() {
        _recentlyAddedStop.value = null
    }
}
