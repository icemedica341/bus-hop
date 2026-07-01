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
import com.bushop.domain.model.BusStopEntry
import com.bushop.domain.model.BusStopWithArrivals
import com.bushop.domain.model.ColorSchemeOption
import com.bushop.domain.model.ThemeMode
import com.bushop.domain.repository.BusRepository
import com.bushop.domain.usecase.AutoRefreshController
import com.bushop.domain.usecase.BusStopUseCase
import com.bushop.domain.usecase.StopRefreshCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * ┌─ MainViewModel ──────────────────────────────────┐
 * │  app/ layer · MVVM ViewModel                     │
 * │                                                   │
 * │  savedStops ─→ list state for UI                 │
 * │  BusStopUseCase ─→ business logic                │
 * │  ThemeManager ─→ theme mode + color scheme       │
 * │  UpdateManager ─→ APK update check + install     │
 * │  apiStatus ─→ degraded/down health banner        │
 * │  FeatureFlag ─→ runtime toggles                  │
 * │  sortByEarliest ─→ persisted sort preference     │
 * │                                                   │
 * │  Delegates to SearchManager, PinManager,         │
 * │  StopStateManager for focused state domains.     │
 * └───────────────────────────────────────────────────┘
 */

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
        private const val DEGRADED_THRESHOLD = 3
        private const val DOWN_THRESHOLD = 10
    }

    private val isAutoRefreshing = AtomicBoolean(false)

    private val autoRefreshController = AutoRefreshController(viewModelScope)

    // ── Snackbar message bus (shared across managers) ──

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    // ── Addition order tracking (coordinator between StopStateManager & PinManager) ──

    private var additionOrder: List<String> = emptyList()

    // ── API health tracking ──

    private val _apiStatus = MutableStateFlow(ApiStatus.Healthy)
    val apiStatus: StateFlow<ApiStatus> = _apiStatus.asStateFlow()

    private val consecutiveFailures = AtomicInteger(0)

    fun dismissApiBanner() {
        _apiStatus.value = ApiStatus.Healthy
    }

    // ── Delegated state managers ──

    /** Manages add-stop dialog, search, and stop addition. */
    val searchManager: SearchManager = SearchManager(
        viewModelScope,
        repository,
        busStopIndex,
        onApiHealthy = {
            consecutiveFailures.set(0)
            _apiStatus.value = ApiStatus.Healthy
        },
        onStopAdded = { code -> stopStateManager.collapseStop(code) },
    )

    /** Manages pinned services and pinned stops. */
    val pinManager: PinManager = PinManager(
        viewModelScope,
        repository,
        _snackbarMessage,
    )

    /** Manages saved stops list, refresh, collapse, sort. */
    val stopStateManager: StopStateManager = StopStateManager(
        viewModelScope,
        repository,
        useCase,
        refreshCoordinator,
        pinManager.pinnedStops,
        pinManager.pinnedServices,
        _snackbarMessage,
        onApiStatusUpdate = { status -> _apiStatus.value = status },
        consecutiveFailures,
        isAutoRefreshing,
        onAdditionOrderChanged = { additionOrder = it },
    )

    // ── Theme (delegated to ThemeManager) ──

    val themeModeFlow: StateFlow<ThemeMode> get() = themeManager.themeModeFlow
    val colorSchemeOptionFlow: StateFlow<ColorSchemeOption> get() = themeManager.colorSchemeOptionFlow

    fun setThemeMode(mode: ThemeMode) = themeManager.setThemeMode(mode)

    fun setColorSchemeOption(option: ColorSchemeOption) = themeManager.setColorSchemeOption(option)

    // ── Index readiness ──

    val isIndexReady: StateFlow<Boolean> = repository.isIndexReady

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

    // ── Auto-refresh ──

    var autoRefreshIntervalSeconds by mutableStateOf(DEFAULT_AUTO_REFRESH_INTERVAL)
        private set

    // ── Delegates to SearchManager ──

    val addStopDialogVisible: Boolean get() = searchManager.addStopDialogVisible
    val randomHint: String get() = searchManager.randomHint
    val addStopError: String? get() = searchManager.addStopError
    val addStopIsLoading: Boolean get() = searchManager.addStopIsLoading
    val searchResults: StateFlow<List<BusStopEntry>> get() = searchManager.searchResults
    val recentlyAddedStop: StateFlow<String?> get() = searchManager.recentlyAddedStop

    fun showAddStopDialog() = searchManager.showAddStopDialog()
    fun searchBusStops(query: String) = searchManager.searchBusStops(query)
    fun findBusStopByCode(code: String) = searchManager.findBusStopByCode(code)
    fun hideAddStopDialog() = searchManager.hideAddStopDialog()
    fun addBusStop(code: String, name: String = "") = searchManager.addBusStop(code, name)
    fun clearNewStopHighlight() = searchManager.clearNewStopHighlight()

    // ── Delegates to PinManager ──

    val pinnedServices: StateFlow<Set<String>> get() = pinManager.pinnedServices
    val pinnedStops: StateFlow<Set<String>> get() = pinManager.pinnedStops

    fun togglePinService(
        stopCode: String,
        serviceNo: String,
    ) = pinManager.togglePinService(stopCode, serviceNo)

    fun isServicePinned(
        stopCode: String,
        serviceNo: String,
    ): Boolean = pinManager.isServicePinned(stopCode, serviceNo)

    fun togglePin(code: String) {
        val current = stopStateManager.currentSavedStops()
        val updated = pinManager.togglePin(code, current, additionOrder, useCase)
        if (updated !== current) {
            stopStateManager.replaceSavedStops(updated)
        }
    }

    // ── Delegates to StopStateManager ──

    val savedStops: StateFlow<List<BusStopWithArrivals>> get() = stopStateManager.savedStops
    val sortByEarliest: StateFlow<Boolean> get() = stopStateManager.sortByEarliest
    val isRefreshing: Boolean get() = stopStateManager.isRefreshing
    val lastUpdatedAll: Long get() = stopStateManager.lastUpdatedAll

    fun moveStop(
        code: String,
        delta: Int,
    ) = stopStateManager.moveStop(code, delta)

    fun refreshArrivals(
        code: String,
        isAutoRefresh: Boolean = false,
    ) = stopStateManager.refreshArrivals(code, isAutoRefresh)

    fun refreshAll(isAutoRefresh: Boolean = false) = stopStateManager.refreshAll(isAutoRefresh)

    fun toggleSortOrder() = stopStateManager.toggleSortOrder()

    fun toggleCollapse(code: String) = stopStateManager.toggleCollapse(code)

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

    // ── Stop removal (uses stopStateManager for list mutation) ──

    fun removeBusStop(code: String) {
        val removedStop = stopStateManager.removeStopImmediately(code)
        viewModelScope.launch {
            try {
                repository.removeBusStop(code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (removedStop != null) {
                    stopStateManager.addStopBack(removedStop)
                }
                _snackbarMessage.tryEmit("Failed to remove bus stop")
            }
        }
    }

    // ── Lifecycle-aware auto-refresh ──

    fun pauseAutoRefresh() {
        autoRefreshController.stop()
    }

    fun resumeAutoRefresh() {
        refreshAll(isAutoRefresh = true)
        if (autoRefreshIntervalSeconds > 0) {
            autoRefreshController.start(autoRefreshIntervalSeconds) { refreshAll(isAutoRefresh = true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshController.stop()
    }

    // ── Init ──

    init {
        // Restore persisted preferences from delegates
        themeManager.observePersisted()
        updateManager.observePersisted()

        // Managers initialise their own flow collection in their init blocks,
        // which are triggered by property evaluation above.

        // Start auto-refresh if previously configured
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
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(application, repository, busStopIndex, updateChecker, useCase, refreshCoordinator) as T
    }
}
