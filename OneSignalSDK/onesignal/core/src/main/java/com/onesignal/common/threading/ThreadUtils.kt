package com.onesignal.common.threading

import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

/**
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
 */
fun suspendifyBlocking(block: suspend () -> Unit) {
    runBlocking {
        block()
    }
}

/**
 * Allows a non suspending function to create a scope that can
 * call suspending functions while on the main thread.  This is a nonblocking call,
 * the scope will start on a background thread and block as it switches
 * over to the main thread context.  This will return immediately!!!
 */
fun suspendifyOnMain(block: suspend () -> Unit) {
    thread {
        try {
            runBlocking {
                withContext(Dispatchers.Main) {
                    block()
                }
            }
        } catch (e: Exception) {
            Logging.error("Exception on thread with switch to main", e)
        }
    }
}

/**
 * Allows a non suspending function to create a scope that can
 * call suspending functions.  This is a nonblocking call, which
 * means the scope will run on a background thread.  This will
 * return immediately!!!
 */
fun suspendifyOnThread(
    priority: Int = -1,
    block: suspend () -> Unit,
) {
    suspendifyOnThread(priority, block, null)
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
 **/
fun suspendifyOnThread(
    priority: Int = -1,
    block: suspend () -> Unit,
    onComplete: (() -> Unit)? = null,
) {
    suspendifyOnThread<Unit>(
        priority = priority,
        block = {
            block()
        },
        onComplete = {
            onComplete?.invoke()
        }
    )}

/**
 * Allows a non-suspending function to execute a suspending lambda on a background thread,
 * and optionally receive the result (or error) via a callback once it completes.
 *
 * This is a non-blocking, fire-and-forget helper that internally runs the suspending
 * [block] inside a `runBlocking {}` context on a new thread. It immediately returns
 * to the caller while the work continues in the background.
 *
 * @param priority The priority of the background thread. Default is -1.
 *                 Higher values indicate higher thread priority.
 *
 * @param block A suspending lambda to be executed on the background thread and return a value.
 *
 * @param onComplete An optional callback invoked after [block] finishes.
 *                   It receives a [Result] containing either the value returned by [block],
 *                   or an exception if one occurred.
 *                   Note: This runs on the same background thread, not the main thread.
 */
fun <T> suspendifyOnThread(
    priority: Int = -1,
    block: suspend () -> T,
    onComplete: ((Result<T>) -> Unit)? = null,
) {
    thread(priority = priority) {
        val result = try {
            val value = runBlocking { block() }
            Result.success(value)
        } catch (e: Exception) {
            Result.failure(e)
        }

        try {
            onComplete?.invoke(result)
        } catch (e: Exception) {
            Logging.error("Exception during onComplete callback", e)
        }
    }
}

/**
 * Allows a non suspending function to create a scope that can
 * call suspending functions.  This is a nonblocking call, which
 * means the scope will run on a background thread.  This will
 * return immediately!!!
 */
fun suspendifyOnThread(
    name: String,
    priority: Int = -1,
    block: suspend () -> Unit,
) {
    thread(name = name, priority = priority) {
        try {
            runBlocking {
                block()
            }
        } catch (e: Exception) {
            Logging.error("Exception on thread '$name'", e)
        }
    }
}
