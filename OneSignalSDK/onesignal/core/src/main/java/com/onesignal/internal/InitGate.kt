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
            if (finishIfUnfinished) failIfUnfinished(owned)
            // A retry woken by this attempt's FAILED may already have claimed the slot.
            initThread.compareAndSet(current, null)
        }
    }

    // Keyed by attempt, not thread: a retry may start before this attempt's thread leaves [runInit].
    private fun failIfUnfinished(owned: Int) {
        val abandoned = synchronized(lock) { state == InitState.IN_PROGRESS && attempt == owned }
        if (abandoned) complete(InitState.FAILED)
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

    /** Waits out in-progress attempts, including a retry that starts while waiting. True only on SUCCESS. */
    suspend fun await(operation: SdkOperation? = null): Boolean {
        while (true) {
            val (observed, signal) = synchronized(lock) { state to completion }
            if (observed != InitState.IN_PROGRESS || isOnInitThread) return observed == InitState.SUCCESS
            awaitAttempt(signal, operation)
        }
    }

    /** Blocking form of [awaitReady]. On the main thread this blocks until init finishes. */
    fun isReady(
        operation: SdkOperation,
        member: String? = null,
        log: Boolean = true,
    ): Boolean {
        if (isSuccess) return true
        val ready = if (state == InitState.IN_PROGRESS && !isOnInitThread) awaitBlocking(operation) else isSuccess
        if (!ready && log) logNotReady(target(operation, member))
        return ready
    }

    // runBlocking throws on an interrupted thread, so it is only entered when there is something to wait for.
    private fun awaitBlocking(operation: SdkOperation): Boolean =
        try {
            // Dispatcher-less: resumes on the caller's own event loop, never on a busy IO worker.
            runBlocking { await(operation) }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
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
        when {
            state == InitState.FAILED -> "'$target' ignored: initWithContext failed."
            isOnInitThread -> "'$target' ignored: called from inside initWithContext."
            state == InitState.IN_PROGRESS -> "'$target' ignored: the thread was interrupted while waiting for initWithContext."
            else -> "'$target' ignored: call initWithContext with a valid appId first."
        }

    fun logQueued(
        operation: SdkOperation,
        member: String,
    ) {
        Logging.warn("'${target(operation, member)}' queued until initWithContext succeeds.")
    }

    private fun logNotReady(target: String) {
        val message = notReadyMessage(target)
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
) {
    Logging.debug("Waiting for init to complete...")
    val startTime = System.currentTimeMillis()
    signal.await()
    val elapsed = System.currentTimeMillis() - startTime
    val before = operation?.let { " before '$it'" } ?: ""
    Logging.debug("OneSignalImpV2 initialization completed$before (took ${elapsed}ms)")
}

private fun target(
    operation: SdkOperation,
    member: String?,
) = member?.let { "$operation.$it" } ?: "$operation"
