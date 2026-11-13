package com.onesignal.internal

import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference

/**
 * Init state for [OneSignalImpV2] and the waits on it. Never throws: callers that need the SDK
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

    // Guarded by [lock]. Each ignored call logs ERROR once per attempt, so a polling getter can't flood
    // remote logging; repeats go to DEBUG.
    private val loggedTargets = HashSet<String>()

    // Guarded by [lock]; bumped by [tryStart].
    private var attempt = 0

    // A wait on this thread while IN_PROGRESS would wait on itself (e.g. a listener replayed during init).
    private val initThread = AtomicReference<Thread?>(null)

    private val earlyCalls = EarlyCalls()

    val isSuccess: Boolean get() = state == InitState.SUCCESS

    private val isOnInitThread: Boolean get() = state == InitState.IN_PROGRESS && initThread.get() === Thread.currentThread()

    // Every attempt must end: one left IN_PROGRESS blocks every sync accessor forever.
    // [finishIfUnfinished] is false only for the caller's pre-dispatch work, which completes itself.
    fun runInit(finishIfUnfinished: Boolean = true, block: () -> Boolean): Boolean {
        val current = Thread.currentThread()
        val owned = synchronized(lock) { attempt }
        initThread.set(current)
        try {
            return block()
        } catch (e: LinkageError) {
            Logging.error("OneSignal: initWithContext hit a LinkageError; marking init FAILED", e)
            addFailureCause(e)
            return false
        } finally {
            // Keyed by attempt, not thread: a retry may start before this attempt's thread leaves runInit.
            if (finishIfUnfinished) {
                val abandoned = synchronized(lock) { state == InitState.IN_PROGRESS && attempt == owned }
                if (abandoned) complete(InitState.FAILED)
            }
            // A retry woken by this attempt's FAILED may already have claimed the slot.
            initThread.compareAndSet(current, null)
        }
    }

    /** Starts a new attempt and returns true, or returns false if one is running or has succeeded. */
    fun tryStart(): Boolean =
        synchronized(lock) {
            if (state.isSDKAccessible()) return false
            failure = IllegalStateException("OneSignal initWithContext failed.")
            failureTraceLogged = false
            loggedTargets.clear()
            attempt++
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
        // Also on FAILED: remote logging is usually up by then, and init may never succeed.
        earlyCalls.logAndClear()
    }

    /** Waits out in-progress attempts, including a retry that starts while waiting. Returns the state that ended the wait. */
    suspend fun await(operation: SdkOperation? = null): InitState {
        while (true) {
            val (observed, signal) = synchronized(lock) { state to completion }
            if (observed != InitState.IN_PROGRESS || isOnInitThread) return observed
            awaitAttempt(signal, operation) { state }
        }
    }

    // On the main thread this blocks until init finishes. On the init thread it returns not ready,
    // so login, logout, and tag calls from an init callback are ignored. Registrations stay queued.
    fun isReady(
        operation: SdkOperation,
        member: String? = null,
        log: Boolean = true,
    ): Boolean {
        if (isSuccess) return true
        // runBlocking throws on an interrupted thread, so it is only entered when there is something to wait for.
        val outcome =
            if (state == InitState.IN_PROGRESS && !isOnInitThread) {
                try {
                    // Dispatcher-less: resumes on the caller's own event loop, never on a busy IO worker.
                    runBlocking { await(operation) }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    InitState.IN_PROGRESS
                }
            } else {
                state
            }
        if (outcome != InitState.SUCCESS && log) logNotReady(target(operation, member), outcome)
        return outcome == InitState.SUCCESS
    }

    suspend fun awaitReady(
        operation: SdkOperation,
        member: String? = null,
        log: Boolean = true,
    ): Boolean {
        val outcome = await(operation)
        if (outcome == InitState.SUCCESS) return true
        if (log) logNotReady(target(operation, member), outcome)
        return false
    }

    fun notReadyMessage(target: String, observed: InitState = state): String =
        when {
            observed == InitState.FAILED -> "'$target' ignored: initWithContext failed."
            observed == InitState.IN_PROGRESS && isOnInitThread -> "'$target' ignored: called from inside initWithContext."
            observed == InitState.IN_PROGRESS -> "'$target' ignored: the thread was interrupted while waiting for initWithContext."
            else -> "'$target' ignored: call initWithContext with a valid appId first."
        }

    fun logQueued(
        operation: SdkOperation,
        member: String,
    ) {
        Logging.warn("'${target(operation, member)}' queued until initWithContext succeeds.")
    }

    internal fun logNotReady(target: String, observed: InitState = state): String {
        val message = notReadyMessage(target, observed)
        var first = false
        val trace =
            synchronized(lock) {
                if (state == InitState.NOT_STARTED) earlyCalls.record(target)
                first = loggedTargets.add(target)
                failure.takeIf { first && state == InitState.FAILED && !failureTraceLogged }?.also { failureTraceLogged = true }
            }
        when {
            !first -> Logging.debug(message)
            trace == null -> Logging.error(message)
            else -> Logging.error(message, trace)
        }
        return message
    }
}

// Distinct calls dropped before any init attempt, logged once when an attempt ends, since remote logging
// starts only inside init. Later drops log themselves; queued registrations are not counted.
private class EarlyCalls {
    private val calls = LinkedHashSet<String>()

    fun record(target: String) {
        synchronized(calls) { if (calls.size < MAX_EARLY_CALLS) calls.add(target) }
    }

    fun logAndClear() {
        val logged = synchronized(calls) { calls.toList().also { calls.clear() } }
        if (logged.isEmpty()) return
        Logging.error("OneSignal calls made before initWithContext were ignored: ${logged.joinToString()}. Call initWithContext first.")
    }

    private companion object {
        const val MAX_EARLY_CALLS = 20
    }
}

private suspend fun awaitAttempt(
    signal: CompletableDeferred<Unit>,
    operation: SdkOperation?,
    state: () -> InitState,
) {
    Logging.debug("Waiting for init to complete...")
    val startTime = System.currentTimeMillis()
    signal.await()
    val elapsed = System.currentTimeMillis() - startTime
    val before = operation?.let { " before '$it'" } ?: ""
    Logging.debug("OneSignalImpV2 initialization finished$before (${state()}, took ${elapsed}ms)")
}

private fun target(
    operation: SdkOperation,
    member: String?,
) = member?.let { "$operation.$it" } ?: "$operation"
