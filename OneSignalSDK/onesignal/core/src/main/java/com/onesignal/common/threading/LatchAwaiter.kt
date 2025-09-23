package com.onesignal.common.threading

import com.onesignal.common.AndroidUtils
import com.onesignal.debug.internal.logging.Logging
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A reusable class for waiting on initialization with timeout and proper error handling.
 *
 * Usage:
 * ```
 * val awaiter = InitializationAwaiter("OneSignal SDK")
 *
 * // In initialization code:
 * awaiter.complete(success = true)
 *
 * // In waiting code:
 * awaiter.waitForCompletion(timeoutMs = 10000)
 * ```
 */
class LatchAwaiter(
    private val componentName: String = "Component",
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L // 30 seconds
        const val ANDROID_ANR_TIMEOUT_MS = 5_000L // 5 seconds (conservative ANR threshold)
    }

    private val initLatch = CountDownLatch(1)
    private var isInitialized = false
    private var initializationSuccess = false
    private var initializationError: Throwable? = null

    /**
     * Mark initialization as completed with success or failure.
     * Can be called from any thread.
     */
    fun complete(
        success: Boolean,
        error: Throwable? = null,
    ) {
        synchronized(this) {
            if (isInitialized) {
                return // Already completed
            }
            isInitialized = true
            initializationSuccess = success
            initializationError = error
        }
        initLatch.countDown()
    }

    /**
     * Mark initialization as successful.
     * Can be called from any thread.
     */
    fun completeSuccess() = complete(success = true)

    /**
     * Mark initialization as failed.
     * Can be called from any thread.
     */
    fun completeFailed(error: Throwable? = null) = complete(success = false, error = error)

    /**
     * Wait for initialization to complete with timeout.
     *
     * @param timeoutMs Timeout in milliseconds. Defaults to ANDROID_ANR_TIMEOUT_MS on main thread.
     */
    fun waitForCompletion(timeoutMs: Long = getDefaultTimeout()) {
        if (isAlreadyCompleted()) {
            checkResult()
            return
        }

        val err =
            try {
                initLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
                null
            } catch (e: InterruptedException) {
                logAllThreadsInfo()
                e
            }

        if (err != null) {
            val message = createTimeoutMessage(timeoutMs)
            Logging.warn("$message $err")
        }

        checkResult()
    }

    /**
     * Reset the awaiter to wait for a new initialization.
     * Use with caution - typically you'd create a new instance instead.
     */
    fun reset() {
        synchronized(this) {
            if (initLatch.count > 0) {
                throw IllegalStateException("Cannot reset while waiting is in progress")
            }
            // Note: CountDownLatch cannot be reset, so this creates a new instance
            throw UnsupportedOperationException("InitializationAwaiter cannot be reset. Create a new instance instead.")
        }
    }

    private fun isAlreadyCompleted(): Boolean = synchronized(this) { isInitialized }

    private fun checkResult() {
        synchronized(this) {
            when {
                !initializationSuccess && initializationError != null -> {
                    throw IllegalStateException("$componentName initialization failed", initializationError)
                }
                !initializationSuccess -> {
                    throw IllegalStateException("$componentName initialization failed")
                }
                // Success case - no exception thrown
                else -> {}
            }
        }
    }

    private fun createTimeoutMessage(timeoutMs: Long): String {
        val isMainThread =
            try {
                AndroidUtils.isRunningOnMainThread()
            } catch (_: Throwable) {
                false
            }

        return if (isMainThread) {
            "Timeout waiting for $componentName initialization after ${timeoutMs}ms. " +
                "This call was made on the main thread, which can block UI. " +
                "Consider calling from a background thread or increasing the timeout."
        } else {
            "Timeout waiting for $componentName initialization after ${timeoutMs}ms."
        }
    }

    private fun logAllThreadsInfo(): String {
        val allThreads = Thread.getAllStackTraces()
        var msg = ""

        for ((thread, stack) in allThreads) {
            msg.plus("ThreadDump Thread: ${thread.name} [${thread.state}]\n")
            for (element in stack) {
                msg.plus("\tat $element\n")
            }
        }

        return msg
    }

    private fun getDefaultTimeout(): Long {
        val isMainThread =
            try {
                AndroidUtils.isRunningOnMainThread()
            } catch (_: Throwable) {
                false
            }

        return if (isMainThread) ANDROID_ANR_TIMEOUT_MS else DEFAULT_TIMEOUT_MS
    }

    /**
     * Result of trying to wait for initialization.
     */
    data class InitResult(
        val success: Boolean,
        val error: Throwable?,
        val timedOut: Boolean,
    )
}
