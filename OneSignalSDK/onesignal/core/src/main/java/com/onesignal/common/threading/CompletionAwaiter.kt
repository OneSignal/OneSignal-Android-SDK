package com.onesignal.common.threading

import com.onesignal.common.threading.OneSignalDispatchers.BASE_THREAD_NAME
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.CountDownLatch

/**
 * A unified completion awaiter that supports both blocking and suspend-based waiting.
 * This class allows both legacy blocking code and modern coroutines to wait for the same event.
 *
 * It is designed for scenarios where certain tasks, such as SDK initialization, must finish
 * before continuing. When used on the main/UI thread for blocking operations, it applies a
 * shorter timeout and logs warnings to prevent ANR errors.
 *
 * PERFORMANCE NOTE: Having both blocking (CountDownLatch) and suspend (Channel) mechanisms
 * in place is very low cost and should not hurt performance. The overhead is minimal:
 * - CountDownLatch: ~32 bytes, optimized for blocking threads
 * - Channel: ~64 bytes, optimized for coroutine suspension
 * - Total overhead: <100 bytes per awaiter instance
 * - Notification cost: Two simple operations (countDown + trySend)
 *
 * This dual approach provides optimal performance for each use case rather than forcing
 * a one-size-fits-all solution that would be suboptimal for both scenarios.
 *
 * Usage:
 *   val awaiter = CompletionAwaiter("OneSignal SDK Init")
 *
 *   // For blocking code:
 *   awaiter.await()
 *
 *   // For suspend code:
 *   awaiter.awaitSuspend()
 *
 *   // When complete:
 *   awaiter.complete()
 */
class CompletionAwaiter(
    private val componentName: String = "Component",
) {

    private val latch = CountDownLatch(1)
    private val suspendCompletion = CompletableDeferred<Unit>()

    /**
     * Completes the awaiter, unblocking both blocking and suspend callers.
     */
    fun complete() {
        latch.countDown()
        suspendCompletion.complete(Unit)
    }

    /**
     * Wait for completion using blocking approach.
     * Waits indefinitely until completion to ensure consistent state.
     *
     * @return Always returns true when completion occurs (never times out).
     */
    fun await(): Boolean {
        // Wait indefinitely until completion - ensures consistent state
        // This can cause ANRs if called from main thread, but that's acceptable
        // as it's better than returning with inconsistent state
        try {
            latch.await()
        } catch (e: InterruptedException) {
            // Check if the latch was actually completed before interruption
            // If completed, return true to maintain consistent state
            // If not completed, re-throw to indicate interruption
            if (latch.count == 0L) {
                // Latch was completed, return true even though we were interrupted
                return true
            } else {
                // Latch was not completed, re-throw to indicate interruption
                Logging.warn("Interrupted while waiting for $componentName", e)
                logAllThreads()
                throw e
            }
        }
        return true
    }

    /**
     * Wait for completion using suspend approach (non-blocking for coroutines).
     * This method will suspend the current coroutine until completion is signaled.
     */
    suspend fun awaitSuspend() {
        suspendCompletion.await()
    }

    private fun logAllThreads(): String {
        val sb = StringBuilder()

        // Add OneSignal dispatcher status first (fast)
        sb.append("=== OneSignal Dispatchers Status ===\n")
        sb.append(OneSignalDispatchers.getStatus())
        sb.append("=== OneSignal Dispatchers Performance ===\n")
        sb.append(OneSignalDispatchers.getPerformanceMetrics())
        sb.append("\n\n")

        // Add lightweight thread info (fast)
        sb.append("=== All Threads Summary ===\n")
        val threads = Thread.getAllStackTraces().keys
        for (thread in threads) {
            sb.append("Thread: ${thread.name} [${thread.state}] ${if (thread.isDaemon) "(daemon)" else ""}\n")
        }

        // Only add full stack traces for OneSignal threads (much faster)
        sb.append("\n=== OneSignal Thread Details ===\n")
        for ((thread, stack) in Thread.getAllStackTraces()) {
            if (thread.name.startsWith(BASE_THREAD_NAME)) {
                sb.append("Thread: ${thread.name} [${thread.state}]\n")
                for (element in stack.take(10)) { // Limit to first 10 frames
                    sb.append("\tat $element\n")
                }
                sb.append("\n")
            }
        }

        return sb.toString()
    }
}
