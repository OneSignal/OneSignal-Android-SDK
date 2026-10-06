package com.onesignal.common.threading

import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Modernized ThreadUtils that leverages OneSignalDispatchers for better thread management.
 *
 * This file provides utilities for bridging non-suspending code with suspending functions,
 * now using the centralized OneSignal dispatcher system for improved resource management
 * and consistent threading behavior across the SDK.
 *
 * @see OneSignalDispatchers
 *
 * Allows a non suspending function to create a scope that can
 * call suspending functions while on the main thread.  This is a nonblocking call,
 * the scope will start on a background thread and block as it switches
 * over to the main thread context.  This will return immediately!!!
 *
 * @param block A suspending lambda to be executed on the background thread.
 *              This is where you put your suspending code.
 *
 */
fun suspendifyOnMain(block: suspend () -> Unit) {
    OneSignalDispatchers.launchOnIO {
        catchSuspendifyFailure("suspendifyOnMain") {
            withMain { block() }
        }
    }
}

/**
 * Allows a non suspending function to create a scope that can
 * call suspending functions.  This is a nonblocking call, which
 * means the scope will run on a background thread.  This will
 * return immediately!!!
 * Uses OneSignal's centralized thread management for better resource control.
 *
 * @param block The suspending code to execute
 *
 */
fun suspendifyOnIO(block: suspend () -> Unit) {
    suspendify(useIO = true, block = block)
}

/**
 * Runs short, deadline-sensitive ingress work on its isolated serial dispatcher.
 * [onSuccess] runs only when [block] completes normally, so callers never treat a failed or
 * cancelled handoff as done.
 */
fun suspendifyOnIngress(
    block: suspend () -> Unit,
    onSuccess: () -> Unit,
) {
    OneSignalDispatchers.launchOnIngress {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logging.error("Exception in suspendifyOnIngress", e)
            return@launchOnIngress
        } catch (e: LinkageError) {
            Logging.error("LinkageError in suspendifyOnIngress", e)
            return@launchOnIngress
        }
        try {
            onSuccess()
        } catch (e: Exception) {
            Logging.error("Exception in suspendifyOnIngress onSuccess", e)
        } catch (e: LinkageError) {
            Logging.error("LinkageError in suspendifyOnIngress onSuccess", e)
        }
    }
}

/**
 * Modern utility for executing suspending code on the default dispatcher.
 * Uses OneSignal's centralized thread management for CPU-intensive operations.
 *
 * @param block The suspending code to execute
 */
fun suspendifyOnDefault(block: suspend () -> Unit) {
    suspendify(useIO = false, block = block)
}

/**
 * Runs [block] on the single-thread serial IO dispatcher. Tasks from any thread execute
 * one-at-a-time in submission order — the entry point for lifecycle handlers that need to
 * preserve event ordering.
 *
 * Capture time-sensitive state (timestamps, "current" snapshots) on the caller's thread
 * before invoking — the block itself may run later under load.
 */
fun suspendifyOnSerialIO(block: suspend () -> Unit) {
    OneSignalDispatchers.launchOnSerialIO {
        catchSuspendifyFailure("suspendifyOnSerialIO") {
            block()
        }
    }
}

/**
 * Dispatches lifecycle offload work to the serial IO thread. The block is non-suspending so
 * callers can hand off plain (non-coroutine) work to the single serial IO worker, preserving
 * submission order across lifecycle events.
 */
fun runOnSerialIO(block: () -> Unit) {
    suspendifyOnSerialIO { block() }
}

private fun suspendify(
    useIO: Boolean,
    block: suspend () -> Unit,
) {
    val launch: (suspend () -> Unit) -> Job =
        if (useIO) OneSignalDispatchers::launchOnIO else OneSignalDispatchers::launchOnDefault
    launch {
        catchSuspendifyFailure("suspendify") {
            block()
        }
    }
}

/**
 * Modern utility for executing suspending code with error handling.
 * Uses OneSignal's centralized thread management with comprehensive error handling.
 *
 * @param useIO Whether to use IO scope (true) or Default scope (false)
 * @param block The suspending code to execute
 * @param onError Optional error handler
 */
fun suspendifyWithErrorHandling(
    useIO: Boolean = true,
    block: suspend () -> Unit,
    onError: ((Exception) -> Unit)? = null,
) {
    val launch: (suspend () -> Unit) -> Job =
        if (useIO) OneSignalDispatchers::launchOnIO else OneSignalDispatchers::launchOnDefault
    launch {
        try {
            block()
        } catch (e: Exception) {
            Logging.error("Exception in suspendifyWithErrorHandling", e)
            onError?.invoke(e)
        } catch (e: LinkageError) {
            Logging.error("LinkageError in suspendifyWithErrorHandling", e)
            onError?.invoke(Exception(e))
        }
    }
}

/**
 * Launch suspending code on IO dispatcher and return a Job for waiting.
 * This is useful when you need to wait for the background work to complete.
 *
 * @param block The suspending code to execute
 * @return Job that can be used to wait for completion with .join()
 */
fun launchOnIO(block: suspend () -> Unit): Job {
    return OneSignalDispatchers.launchOnIO {
        catchSuspendifyFailure("launchOnIO") {
            block()
        }
    }
}

/**
 * Launch suspending code on Default dispatcher and return a Job for waiting.
 * This is useful when you need to wait for the background work to complete.
 *
 * @param block The suspending code to execute
 * @return Job that can be used to wait for completion with .join()
 */
fun launchOnDefault(block: suspend () -> Unit): Job {
    return OneSignalDispatchers.launchOnDefault {
        catchSuspendifyFailure("launchOnDefault") {
            block()
        }
    }
}

/**
 * LinkageError is not an Exception. NoSuchMethodError and ExceptionInInitializerError
 * skip `catch (Exception)` and kill the process.
 */
private suspend fun catchSuspendifyFailure(
    label: String,
    block: suspend () -> Unit,
) {
    try {
        block()
    } catch (e: Exception) {
        Logging.error("Exception in $label", e)
    } catch (e: LinkageError) {
        Logging.error("LinkageError in $label", e)
    }
}

private val mainDispatcherReported = AtomicBoolean(false)

/**
 * Null when the main thread cannot be reached. Reading [Dispatchers.Main] proves nothing: with no
 * Main module, coroutines returns a stub that only fails when dispatched to.
 */
fun mainDispatcherOrNull(): CoroutineDispatcher? = usableMainDispatcher { Dispatchers.Main }

internal fun usableMainDispatcher(resolve: () -> CoroutineDispatcher): CoroutineDispatcher? {
    val main =
        try {
            resolve()
        } catch (e: LinkageError) {
            reportMainDispatcherUnavailable(e)
            return null
        }

    return try {
        // Probe rather than dispatch. The stub throws here, and a real Main answers without
        // scheduling anything, so a cancelled caller is never mistaken for a missing Main.
        @OptIn(ExperimentalCoroutinesApi::class)
        main.isDispatchNeeded(EmptyCoroutineContext)
        main
    } catch (e: IllegalStateException) {
        reportMainDispatcherUnavailable(e)
        null
    } catch (e: LinkageError) {
        reportMainDispatcherUnavailable(e)
        null
    }
}

private fun reportMainDispatcherUnavailable(cause: Throwable) {
    if (mainDispatcherReported.compareAndSet(false, true)) {
        Logging.error("Dispatchers.Main unavailable, skipping main thread work", cause)
    } else {
        Logging.debug("Dispatchers.Main unavailable, skipping main thread work")
    }
}

/**
 * Null when the main thread cannot be reached. A nullable [block] result is indistinguishable from
 * a skip, so callers that need to tell them apart should check [mainDispatcherOrNull] themselves.
 */
suspend fun <T> withMain(block: suspend CoroutineScope.() -> T): T? {
    val main = mainDispatcherOrNull() ?: return null
    return withContext(main, block)
}
