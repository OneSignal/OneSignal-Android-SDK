package com.onesignal.common.threading

import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Modernized ThreadUtils that leverages OneSignalDispatchers for better thread management.
 *
 * This file provides utilities for bridging non-suspending code with suspending functions,
 * now using the centralized OneSignal dispatcher system for improved resource management
 * and consistent threading behavior across the SDK.
 *
 *
 * Deprecated functions are retained for backward compatibility but redirect to new implementations.
 * Allows a non-suspending function to create a scope that can
 * call suspending functions.  This is a blocking call, which
 * means it will not return until the suspending scope has been
 * completed.  The current thread will also be blocked until
 * the suspending scope has completed.
 *
 * Note: This can be very dangerous!! Blocking a thread (especially
 * the main thread) has the potential for a deadlock.  Consider this
 * code that is running on the main thread:
 *
 * ```
 * suspendifyOnThread {
 *   withContext(Dispatchers.Main) {
 *   }
 * }
 * ```
 *
 * The `withContext` will suspend until the main thread is available, but
 * the main thread is parked via this `suspendifyBlocking`. This will
 * never recover.
 *
 * @deprecated Use OneSignalDispatchers.runBlockingOnIO() instead
 */
@Deprecated(
    message = "Use OneSignalDispatchers.runBlockingOnIO() instead",
    replaceWith = ReplaceWith("OneSignalDispatchers.runBlockingOnIO { block() }"),
    level = DeprecationLevel.WARNING,
)
fun suspendifyBlocking(block: suspend () -> Unit) {
    OneSignalDispatchers.runBlockingOnIO { block() }
}

/**
 * Allows a non suspending function to create a scope that can
 * call suspending functions while on the main thread.  This is a nonblocking call,
 * the scope will start on a background thread and block as it switches
 * over to the main thread context.  This will return immediately!!!
 *
 * @deprecated Use OneSignalDispatchers.launchOnIO() instead
 */
@Deprecated(
    message = "Use OneSignalDispatchers.launchOnIO() instead",
    replaceWith = ReplaceWith("OneSignalDispatchers.launchOnIO { block() }"),
    level = DeprecationLevel.WARNING,
)
fun suspendifyOnMain(block: suspend () -> Unit) {
    suspendifyOnMainModern(block)
}

/**
 * Allows a non suspending function to create a scope that can
 * call suspending functions.  This is a nonblocking call, which
 * means the scope will run on a background thread.  This will
 * return immediately!!!
 *
 * @deprecated Use OneSignalDispatchers.launchOnIO() instead
 */
@Deprecated(
    message = "Use OneSignalDispatchers.launchOnIO() instead",
    replaceWith = ReplaceWith("OneSignalDispatchers.launchOnIO { block() }"),
    level = DeprecationLevel.WARNING,
)
fun suspendifyOnThread(
    priority: Int = -1,
    block: suspend () -> Unit,
) {
    suspendifyOnIO(block)
}

/**
 * Allows a non suspending function to create a scope that can
 * call suspending functions.  This is a nonblocking call, which
 * means the scope will run on a background thread.  This will
 * return immediately!!! Also provides an optional onComplete.
 *
 * @param priority The priority of the background thread. Default is -1.
 *                 Higher values indicate higher thread priority.
 *
 * @param block A suspending lambda to be executed on the background thread.
 *              This is where you put your suspending code.
 *
 * @param onComplete An optional lambda that will be invoked on the same
 *                   background thread after [block] has finished executing.
 *                   Useful for cleanup or follow-up logic.
 *
 * @deprecated Use OneSignalDispatchers.launchOnIO() instead
 **/
@Deprecated(
    message = "Use OneSignalDispatchers.launchOnIO() instead",
    replaceWith = ReplaceWith("OneSignalDispatchers.launchOnIO { block(); onComplete?.invoke() }"),
    level = DeprecationLevel.WARNING,
)
fun suspendifyOnThread(
    priority: Int = -1,
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
 *
 * @deprecated Use OneSignalDispatchers.launchOnIO() instead
 */
@Deprecated(
    message = "Use OneSignalDispatchers.launchOnIO() instead",
    replaceWith = ReplaceWith("OneSignalDispatchers.launchOnIO { block() }"),
    level = DeprecationLevel.WARNING,
)
fun suspendifyOnThread(
    name: String,
    priority: Int = -1,
    block: suspend () -> Unit,
) {
    suspendifyOnIO(block)
}

// ===============================
// Modern OneSignal Dispatcher Functions
// ===============================

/**
 * Modern utility for executing suspending code on the I/O dispatcher.
 * Uses OneSignal's centralized thread management for better resource control.
 *
 * @param block The suspending code to execute
 */
fun suspendifyOnIO(block: suspend () -> Unit) {
    OneSignalDispatchers.launchOnIO { block() }
}

/**
 * Modern utility for executing suspending code on the default dispatcher.
 * Uses OneSignal's centralized thread management for CPU-intensive operations.
 *
 * @param block The suspending code to execute
 */
fun suspendifyOnDefault(block: suspend () -> Unit) {
    OneSignalDispatchers.launchOnDefault { block() }
}

/**
 * Modern utility for executing suspending code on the main thread.
 * Uses OneSignal's centralized thread management with proper main thread switching.
 *
 * @param block The suspending code to execute
 */
fun suspendifyOnMainModern(block: suspend () -> Unit) {
    OneSignalDispatchers.launchOnIO {
        withContext(Dispatchers.Main) { block() }
    }
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
    if (useIO) {
        OneSignalDispatchers.launchOnIO {
            try {
                block()
                onComplete?.invoke()
            } catch (e: Exception) {
                Logging.error("Exception in suspendifyWithCompletion", e)
            }
        }
    } else {
        OneSignalDispatchers.launchOnDefault {
            try {
                block()
                onComplete?.invoke()
            } catch (e: Exception) {
                Logging.error("Exception in suspendifyWithCompletion", e)
            }
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
    if (useIO) {
        OneSignalDispatchers.launchOnIO {
            try {
                block()
                onComplete?.invoke()
            } catch (e: Exception) {
                Logging.error("Exception in suspendifyWithErrorHandling", e)
                onError?.invoke(e)
            }
        }
    } else {
        OneSignalDispatchers.launchOnDefault {
            try {
                block()
                onComplete?.invoke()
            } catch (e: Exception) {
                Logging.error("Exception in suspendifyWithErrorHandling", e)
                onError?.invoke(e)
            }
        }
    }
}
