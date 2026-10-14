package com.onesignal.internal

import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

/**
 * Init state for [OneSignalImp] and the waits on it. Never throws: callers that need the SDK
 * get `false` and a log line when init has not started or has failed.
 */
internal class InitGate {
    private val lock = Any()

    // Replaced by every new attempt, so a retry after FAILED never reuses a completed signal.
    @Volatile
    private var completion = CompletableDeferred<Unit>()

    @Volatile
    var state: InitState = InitState.NOT_STARTED
        private set

    // Created by the call that started the attempt, so its stack points at the app's init call.
    @Volatile
    var failure: Exception? = null
        private set

    @Volatile
    private var failureTraceLogged = false

    val isSuccess: Boolean get() = state == InitState.SUCCESS

    /** Starts a new attempt and returns true, or returns false if one is running or has succeeded. */
    fun tryStart(): Boolean =
        synchronized(lock) {
            if (state.isSDKAccessible()) return false
            failure = IllegalStateException("OneSignal initWithContext failed.")
            failureTraceLogged = false
            state = InitState.IN_PROGRESS
            completion = CompletableDeferred()
            true
        }

    fun addFailureCause(cause: Throwable) {
        failure?.addSuppressed(cause)
    }

    // State and signal change together, so a waiter can't wake up holding the next attempt's signal.
    fun complete(terminal: InitState) {
        require(terminal == InitState.SUCCESS || terminal == InitState.FAILED) {
            "complete requires a terminal state, got $terminal"
        }
        synchronized(lock) {
            state = terminal
            completion.complete(Unit)
        }
    }

    /** Waits out in-progress attempts, including a retry that starts while waiting. True only on SUCCESS. */
    suspend fun await(operation: SdkOperation? = null): Boolean {
        while (true) {
            val (observed, signal) = synchronized(lock) { state to completion }
            when (observed) {
                InitState.SUCCESS -> return true
                InitState.NOT_STARTED, InitState.FAILED -> return false
                InitState.IN_PROGRESS -> awaitAttempt(signal, operation)
            }
        }
    }

    /** Blocking form of [awaitReady]. On the main thread this blocks until init finishes. */
    fun isReady(
        operation: SdkOperation,
        member: String? = null,
        log: Boolean = true,
    ): Boolean {
        if (isSuccess) return true
        // Dispatcher-less: resumes on the caller's own event loop, never on a busy IO worker.
        return runBlocking { awaitReady(operation, member, log) }
    }

    suspend fun awaitReady(
        operation: SdkOperation,
        member: String? = null,
        log: Boolean = true,
    ): Boolean {
        if (await(operation)) return true
        if (log) logNotReady(target(operation, member))
        return false
    }

    fun notReadyMessage(target: String): String =
        if (state == InitState.FAILED) {
            "'$target' ignored: initWithContext failed."
        } else {
            "'$target' ignored: call initWithContext with a valid appId first."
        }

    fun logQueued(
        operation: SdkOperation,
        member: String,
    ) {
        Logging.warn("'${target(operation, member)}' queued until initWithContext succeeds.")
    }

    private fun logNotReady(target: String) {
        val message = notReadyMessage(target)
        val trace = failure.takeIf { state == InitState.FAILED && !failureTraceLogged }
        if (trace == null) {
            Logging.error(message)
            return
        }
        failureTraceLogged = true
        Logging.error(message, trace)
    }

    private suspend fun awaitAttempt(
        signal: CompletableDeferred<Unit>,
        operation: SdkOperation?,
    ) {
        Logging.debug("Waiting for init to complete...")
        val startTime = System.currentTimeMillis()
        signal.await()
        val elapsed = System.currentTimeMillis() - startTime
        val before = operation?.let { " before '$it'" } ?: ""
        Logging.debug("OneSignalImp initialization completed$before (took ${elapsed}ms)")
    }
}

private fun target(
    operation: SdkOperation,
    member: String?,
) = member?.let { "$operation.$it" } ?: "$operation"
