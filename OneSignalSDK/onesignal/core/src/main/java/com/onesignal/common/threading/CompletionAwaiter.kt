package com.onesignal.common.threading

import com.onesignal.common.AndroidUtils
import com.onesignal.common.threading.OneSignalDispatchers.BASE_THREAD_NAME
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L // 30 seconds
        const val ANDROID_ANR_TIMEOUT_MS = 4_800L // Conservative ANR threshold
    }

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
     * Wait for completion using blocking approach
     *
     * @param timeoutToLog Timeout in milliseconds to log a message if not
     *   completed in time. Does NOT have any effect on logic.
     */
    fun awaitAndLogIfOverTimeout(timeoutToLog: Long = getDefaultTimeout()) {
        logIfOverTimeout(timeoutToLog)
        latch.await()
    }

    private fun logIfOverTimeout(timeoutMs: Long) {
        launchOnDefault {
            try {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Logging.warn("$componentName is taking longer that normal!", e)
                logAllThreads()
                Logging.warn(createTimeoutMessage(timeoutMs))
            }
        }
    }

    /**
     * Wait for completion using suspend approach (non-blocking for coroutines).
     * This method will suspend the current coroutine until completion is signaled.
     */
    suspend fun awaitSuspend() {
        suspendCompletion.await()
    }

    private fun getDefaultTimeout(): Long = if (AndroidUtils.isRunningOnMainThread()) ANDROID_ANR_TIMEOUT_MS else DEFAULT_TIMEOUT_MS

    private fun createTimeoutMessage(timeoutMs: Long): String =
        if (AndroidUtils.isRunningOnMainThread()) {
            "Timeout waiting for $componentName after ${timeoutMs}ms on the main thread. " +
                "This can cause ANRs. Consider calling from a background thread."
        } else {
            "Timeout waiting for $componentName after ${timeoutMs}ms."
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
