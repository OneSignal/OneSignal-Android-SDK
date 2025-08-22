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
 */
fun suspendifyOnThread(
    priority: Int = -1,
    block: suspend () -> Unit,
    onComplete: (() -> Unit)? = null,
) {
    thread(priority = priority) {
        try {
            runBlocking { block() }
            onComplete?.invoke()
        } catch (e: Exception) {
            Logging.error("Exception on thread", e)
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
