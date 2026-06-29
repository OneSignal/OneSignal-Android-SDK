package com.onesignal.common.threading

import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

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
        try {
            withContext(Dispatchers.Main) { block() }
        } catch (e: Exception) {
            Logging.error("Exception in suspendifyOnMain", e)
        }
    }
}

/**
 * Allows a non suspending function to create a scope that can
 * call suspending functions.  This is a nonblocking call, which
 * means the scope will run on a background thread.  This will
 * return immediately!!! Also provides an optional onComplete.
 **
 * @param block A suspending lambda to be executed on the background thread.
 *              This is where you put your suspending code.
 *
 * @param onComplete An optional lambda that will be invoked on the same
 *                   background thread after [block] has finished executing.
 *                   Useful for cleanup or follow-up logic.
 */
fun suspendifyOnIO(
    block: suspend () -> Unit,
    onComplete: (() -> Unit)? = null,
) {
    suspendifyWithCompletion(useIO = true, block = block, onComplete = onComplete)
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
    suspendifyWithCompletion(useIO = true, block = block, onComplete = null)
}

/**
 * Modern utility for executing suspending code on the default dispatcher.
 * Uses OneSignal's centralized thread management for CPU-intensive operations.
 *
 * @param block The suspending code to execute
 */
fun suspendifyOnDefault(block: suspend () -> Unit) {
    suspendifyWithCompletion(useIO = false, block = block, onComplete = null)
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
        try {
            block()
        } catch (e: Exception) {
            Logging.error("Exception in suspendifyOnSerialIO", e)
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

/**
 * Modern utility for executing suspending code with completion callback.
 * Uses OneSignal's centralized thread management for better resource control.
 *
 * @param useIO Whether to use IO scope (true) or Default scope (false)
 * @param block The suspending code to execute
 * @param onComplete Optional callback to execute after completion
 */
fun suspendifyWithCompletion(
    useIO: Boolean = true,
    block: suspend () -> Unit,
    onComplete: (() -> Unit)? = null,
) {
    val launch: (suspend () -> Unit) -> Job =
        if (useIO) OneSignalDispatchers::launchOnIO else OneSignalDispatchers::launchOnDefault
    launch {
        try {
            block()
            onComplete?.invoke()
        } catch (e: Exception) {
            Logging.error("Exception in suspendifyWithCompletion", e)
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
 * @param onComplete Optional completion handler
 */
fun suspendifyWithErrorHandling(
    useIO: Boolean = true,
    block: suspend () -> Unit,
    onError: ((Exception) -> Unit)? = null,
    onComplete: (() -> Unit)? = null,
) {
    val launch: (suspend () -> Unit) -> Job =
        if (useIO) OneSignalDispatchers::launchOnIO else OneSignalDispatchers::launchOnDefault
    launch {
        try {
            block()
            onComplete?.invoke()
        } catch (e: Exception) {
            Logging.error("Exception in suspendifyWithErrorHandling", e)
            onError?.invoke(e)
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
        try {
            block()
        } catch (e: Exception) {
            Logging.error("Exception in launchOnIO", e)
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
        try {
            block()
        } catch (e: Exception) {
            Logging.error("Exception in launchOnDefault", e)
        }
    }
}
