package com.onesignal.common.threading

import com.onesignal.common.AndroidUtils
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
     * Throws IllegalStateException if timeout occurs or initialization fails.
     *
     * @param timeoutMs Timeout in milliseconds. Defaults to ANDROID_ANR_TIMEOUT_MS on main thread.
     * @throws IllegalStateException if timeout occurs, initialization fails, or thread is interrupted
     */
    fun waitForCompletion(timeoutMs: Long = getDefaultTimeout()) {
        if (isAlreadyCompleted()) {
            checkResult()
            return
        }

        val awaitCompleted =
            try {
                initLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                throw IllegalStateException("Interrupted while waiting for $componentName initialization", e)
            }

        if (!awaitCompleted) {
            throw IllegalStateException(createTimeoutMessage(timeoutMs))
        }

        checkResult()
    }

    /**
     * Wait for initialization and return success status instead of throwing.
     *
     * @param timeoutMs Timeout in milliseconds
     * @return InitResult containing success status and optional error
     */
    fun tryWaitForCompletion(timeoutMs: Long = getDefaultTimeout()): InitResult {
        return try {
            waitForCompletion(timeoutMs)
            InitResult(success = true, error = null, timedOut = false)
        } catch (e: IllegalStateException) {
            val timedOut = e.message?.contains("Timeout") == true
            InitResult(success = false, error = e, timedOut = timedOut)
        }
    }

    /**
     * Check if initialization has already completed.
     */
    fun isCompleted(): Boolean = synchronized(this) { isInitialized }

    /**
     * Check if initialization was successful (only valid after completion).
     */
    fun wasSuccessful(): Boolean = synchronized(this) { isInitialized && initializationSuccess }

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
