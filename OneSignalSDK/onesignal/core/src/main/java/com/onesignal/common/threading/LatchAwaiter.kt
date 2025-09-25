package com.onesignal.common.threading

import com.onesignal.common.AndroidUtils
import com.onesignal.debug.internal.logging.Logging
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * This class allows blocking execution until asynchronous initialization or completion is signaled, with support for configurable timeouts and detailed logging for troubleshooting.
 * It is designed for scenarios where certain tasks, such as SDK initialization, must finish before continuing.
 * When used on the main/UI thread, it applies a shorter timeout and logs a thread stack trace to warn developers, helping to prevent Application Not Responding (ANR) errors caused by blocking the UI thread.
 *
 * Usage:
 *   val awaiter = LatchAwaiter("OneSignal SDK Init")
 *   awaiter.release() // when done
 *   awaiter.awaitOrThrow() // or await() to just check
 */
class LatchAwaiter(
    private val componentName: String = "Component",
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L // 30 seconds
        const val ANDROID_ANR_TIMEOUT_MS = 4_800L // Conservative ANR threshold
    }

    private val latch = CountDownLatch(1)

    /**
     * Releases the latch to unblock any waiting threads.
     */
    fun release() {
        latch.countDown()
    }

    /**
     * Wait for the latch to be released with an optional timeout.
     *
     * @return true if latch was released before timeout, false otherwise.
     */
    fun await(timeoutMs: Long = getDefaultTimeout()): Boolean {
        val completed =
            try {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Logging.warn("Interrupted while waiting for $componentName", e)
                logAllThreadsInfo()
                false
            }

        if (!completed) {
            val message = createTimeoutMessage(timeoutMs)
            Logging.warn(message)
        }

        return completed
    }

    /**
     * Wait for the latch and throw if not completed in time.
     */
    fun awaitOrThrow(timeoutMs: Long = getDefaultTimeout()) {
        if (!await(timeoutMs)) {
            throw IllegalStateException("$componentName initialization timeout after ${timeoutMs}ms")
        }
    }

    private fun getDefaultTimeout(): Long {
        return if (AndroidUtils.isRunningOnMainThread()) ANDROID_ANR_TIMEOUT_MS else DEFAULT_TIMEOUT_MS
    }

    private fun createTimeoutMessage(timeoutMs: Long): String {
        val isMainThread =
            try {
                AndroidUtils.isRunningOnMainThread()
            } catch (_: Throwable) {
                false
            }

        return if (isMainThread) {
            "Timeout waiting for $componentName after ${timeoutMs}ms on the main thread. " +
                "This can cause ANRs. Consider calling from a background thread."
        } else {
            "Timeout waiting for $componentName after ${timeoutMs}ms."
        }
    }

    private fun logAllThreadsInfo(): String {
        val allThreads = Thread.getAllStackTraces()
        val sb = StringBuilder()
        for ((thread, stack) in allThreads) {
            sb.append("ThreadDump Thread: ${thread.name} [${thread.state}]\n")
            for (element in stack) {
                sb.append("\tat $element\n")
            }
        }

        val output = sb.toString()
        Logging.debug("Thread dump:\n$output")
        return output
    }
}
