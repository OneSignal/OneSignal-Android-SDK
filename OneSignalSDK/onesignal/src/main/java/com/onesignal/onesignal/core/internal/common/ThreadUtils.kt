package com.onesignal.onesignal.core.internal.common

import kotlinx.coroutines.runBlocking
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
      * ThreadUtils.suspendify {
      *   withContext(Dispatchers.Main) {
      *   }
      * }
      * ```
      *
      * The `withContext` will suspend until the main thread is available, but
      * the main thread is parked via the `ThreadUtils.suspendify`. This will
      * never recover.
      */
     fun suspendify(block: suspend () -> Unit) {
         runBlocking {
             block()
         }
     }

     /**
      * Allows a non suspending function to create a scope that can
      * call suspending functions.  This is a nonblocking call, which
      * means the scope will run on a background thread.  This will
      * return immediately!!!
      */
    fun suspendifyOnThread(priority: Int = -1, block: suspend () -> Unit) {
        thread(priority = priority) {
            runBlocking {
                block()
            }
        }
    }