package com.bushop.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Retry a suspend [block] up to [maxRetries] times with exponential backoff + jitter.
 * Re-throws [CancellationException] immediately — does not suppress cancellation.
 */
suspend fun <T> retrySuspend(
    maxRetries: Int = 2,
    initialDelayMs: Long = 1000,
    block: suspend () -> Result<T>,
): Result<T> {
    var lastError: Throwable? = null
    repeat(maxRetries + 1) { attempt ->
        try {
            val result = block()
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
        }
        if (attempt < maxRetries) {
            val baseDelay = initialDelayMs * (1 shl attempt)
            val jitter = Random.nextLong(0, baseDelay / 2)
            delay(baseDelay + jitter)
        }
    }
    return Result.failure(lastError ?: Exception("Max retries exceeded"))
}
