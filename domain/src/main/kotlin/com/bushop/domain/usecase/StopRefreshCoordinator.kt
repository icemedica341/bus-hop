package com.bushop.domain.usecase

/**
 * ┌─ StopRefreshCoordinator ─────────────────────────┐
 * │  domain/ layer · Coordinated refresh logic       │
 * │                                                   │
 * │  refreshStop() ─→ fetch + update single stop     │
 * │  Cooldown per stop (prevents rapid refetch)      │
 * │  Concurrent batching for multi-stop updates      │
 * │  Thread-safe (ConcurrentHashMap backed)          │
 * └───────────────────────────────────────────────────┘
 */

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Manages per-stop cooldown and concurrency for refresh operations. */
class StopRefreshCoordinator(
    private val cooldownMs: Long = 30_000L,
    private val maxConcurrent: Int = 5,
) {
    private val lastRefreshTimestamps = ConcurrentHashMap<String, Long>()
    private val refreshMutexes = ConcurrentHashMap<String, Mutex>()

    private fun cooldownMutex(code: String): Mutex = refreshMutexes.getOrPut(code) { Mutex() }

    /** Attempt a refresh for [code] respecting cooldown. Returns false if skipped. */
    suspend fun tryRefresh(
        code: String,
        isAutoRefresh: Boolean,
    ): Boolean {
        return cooldownMutex(code).withLock {
            val now = System.currentTimeMillis()
            val lastRefresh = lastRefreshTimestamps[code] ?: 0L
            if (!isAutoRefresh && now - lastRefresh < cooldownMs) return@withLock false
            lastRefreshTimestamps[code] = now
            true
        }
    }

    /** Remove stored mutexes for codes that are no longer in use. */
    fun cleanupMutexes(activeCodes: Set<String>) {
        refreshMutexes.keys.removeAll { it !in activeCodes }
    }

    /** Refresh all [codes] in batches of [maxConcurrent]. */
    suspend fun refreshAllConcurrent(
        codes: List<String>,
        isAutoRefresh: Boolean,
        refreshBlock: suspend (String) -> Unit,
    ) = coroutineScope {
        codes.chunked(maxConcurrent).forEach { batch ->
            batch
                .map { code ->
                    async {
                        if (tryRefresh(code, isAutoRefresh)) {
                            refreshBlock(code)
                        }
                    }
                }.awaitAll()
        }
    }
}
