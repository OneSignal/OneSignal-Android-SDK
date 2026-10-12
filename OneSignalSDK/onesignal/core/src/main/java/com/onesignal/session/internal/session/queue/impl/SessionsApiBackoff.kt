package com.onesignal.session.internal.session.queue.impl

import com.onesignal.core.internal.time.ITime
import kotlin.random.Random

/**
 * Exponential backoff with jitter after a failed send. A backend `Retry-After` is a floor that
 * [skipFailureDelay] never shortens.
 */
internal class SessionsApiBackoff(
    private val time: ITime,
) {
    internal var jitter: () -> Double = { Random.nextDouble(MIN_JITTER, 1.0) }

    // elapsedRealtime millis.
    @Volatile private var retryAt = 0L

    @Volatile private var retryAfterAt = 0L

    @Volatile private var consecutiveFailures = 0

    val waitMillis: Long
        get() = maxOf(retryAt, retryAfterAt) - time.elapsedRealtimeMillis

    val isWaitingOnRetryAfter: Boolean
        get() = retryAfterAt > time.elapsedRealtimeMillis

    fun recordFailure(retryAfterSeconds: Int?) {
        val now = time.elapsedRealtimeMillis
        val delay = (BASE_DELAY_MS shl consecutiveFailures.coerceAtMost(MAX_DOUBLINGS)).coerceAtMost(MAX_DELAY_MS)
        consecutiveFailures++
        retryAt = now + (delay * jitter()).toLong()
        retryAfterAt = now + (retryAfterSeconds ?: 0).coerceIn(0, MAX_RETRY_AFTER_SECONDS) * MILLIS_PER_SECOND
    }

    fun recordSuccess() {
        consecutiveFailures = 0
    }

    /** Returns true if a failure delay was skipped. */
    fun skipFailureDelay(): Boolean {
        if (retryAt <= time.elapsedRealtimeMillis) return false
        retryAt = 0L
        return true
    }

    private companion object {
        const val BASE_DELAY_MS = 5_000L
        const val MAX_DELAY_MS = 300_000L
        const val MAX_DOUBLINGS = 6
        const val MIN_JITTER = 0.5
        const val MAX_RETRY_AFTER_SECONDS = 3600
        const val MILLIS_PER_SECOND = 1000L
    }
}
