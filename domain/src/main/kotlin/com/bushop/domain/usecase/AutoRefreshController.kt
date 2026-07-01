package com.bushop.domain.usecase

/**
 * ┌─ AutoRefreshController ──────────────────────────┐
 * │  domain/ layer · Periodic refresh scheduler      │
 * │                                                   │
 * │  start(intervalMs) ─→ launches recurring fetch   │
 * │  stop() ─→ cancels coroutine job                 │
 * │  Pauses when app in background (lifecycle-aware) │
 * │  Starts on app foreground via ViewModel init     │
 * └───────────────────────────────────────────────────┘
 */

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Manages the auto-refresh timer lifecycle. */
class AutoRefreshController(
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start(
        intervalSeconds: Int,
        onRefresh: () -> Unit,
    ) {
        stop()
        if (intervalSeconds <= 0) return
        job =
            scope.launch {
                while (true) {
                    delay(intervalSeconds * 1000L)
                    ensureActive()
                    onRefresh()
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
