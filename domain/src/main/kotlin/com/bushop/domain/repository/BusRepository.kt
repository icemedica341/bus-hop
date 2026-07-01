package com.bushop.domain.repository

/**
 * ┌─ BusRepository ──────────────────────────────────┐
 * │  domain/ layer · Repository interface            │
 * │                                                   │
 * │  getArrivals(code) ─→ Flow<BusStopWithArrivals>  │
 * │  savedStops flow, pin/collapse/delete operations │
 * │  Theme/color preferences read/write              │
 * │  Search, nearby stops, drag hint state           │
 * │  Implemented by BusRepositoryImpl in data/       │
 * └───────────────────────────────────────────────────┘
 */

import com.bushop.domain.model.BusService
import com.bushop.domain.model.BusStop
import com.bushop.domain.model.BusStopEntry
import com.bushop.domain.model.ColorSchemeOption
import com.bushop.domain.model.NetworkResult
import com.bushop.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Repository interface for bus stop data. Pure domain — no Android dependencies. */
interface BusRepository {
    val savedBusStops: Flow<List<BusStop>>
    val cachedBusServices: Flow<Map<String, List<BusService>>>
    val cachedTimestamps: Flow<Map<String, Long>>
    val themeModeFlow: Flow<ThemeMode>
    val colorSchemeOptionFlow: Flow<ColorSchemeOption>
    val collapsedStopsFlow: Flow<Set<String>>
    val isIndexReady: StateFlow<Boolean>
    val autoRefreshInterval: Flow<Int>

    suspend fun getAutoRefreshIntervalOnce(): Int

    suspend fun setAutoRefreshInterval(seconds: Int)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setColorSchemeOption(option: ColorSchemeOption)

    suspend fun setCollapsedStops(stops: Set<String>)

    val sortByEarliestFlow: Flow<Boolean>

    suspend fun setSortByEarliest(enabled: Boolean)

    val pinnedServicesFlow: Flow<Set<String>>

    suspend fun savePinnedServices(pinned: Set<String>)

    val pinnedStopsFlow: Flow<Set<String>>

    suspend fun savePinnedStops(pinned: Set<String>)

    val hasSeenHintFlow: Flow<Boolean>

    suspend fun saveHintSeen(seen: Boolean)

    suspend fun addBusStop(stop: BusStop): Result<Unit>

    suspend fun removeBusStop(code: String)

    suspend fun reorderStops(stops: List<BusStop>)

    suspend fun getBusArrivals(busStopCode: String): NetworkResult<List<BusService>>

    // ── Search (delegated to BusStopIndex) ──
    fun searchBusStops(query: String): List<BusStopEntry>

    fun findBusStopByCode(code: String): BusStopEntry?

    fun findNearbyStops(
        lat: Double,
        lng: Double,
        radiusKm: Double = 0.5,
    ): List<BusStopEntry>
}
