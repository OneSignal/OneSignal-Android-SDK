package com.onesignal.debug.internal.crash

import kotlinx.coroutines.runBlocking

/**
 * Purpose: Writes any crashes involving OneSignal to a file where they can
 *          later be send to OneSignal to help improve reliability.
 * NOTE: For future refactors, code is written assuming this is a singleton
 */
internal class OneSignalCrashHandler(
    private val _crashReporter: IOneSignalCrashReporter,
) : Thread.UncaughtExceptionHandler {
    private var existingHandler: Thread.UncaughtExceptionHandler? = null
    private val seenThrowables: MutableList<Throwable> = mutableListOf()

    init {
        existingHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Ensure we never attempt to process the same throwable instance
        // more than once. This would only happen if there was another crash
        // handler and was faulty in a specific way.
        synchronized(seenThrowables) {
            if (seenThrowables.contains(throwable)) {
                return
            }
            seenThrowables.add(throwable)
        }

        // TODO: Catch anything we may throw and print only to logcat
        // TODO: Also send a stop command to OneSignalCrashUploader,
        //   give a bit of time to finish and then call existingHandler.
        //   * This way the app doesn't have to open a 2nd time to get the
        //     crash report and should help prevent duplicated reports.
        if (!isOneSignalAtFault(throwable)) {
            existingHandler?.uncaughtException(thread, throwable)
            return
        }

        /**
         * NOTE: The order and running sequentially is important as:
         * The existingHandler.uncaughtException can immediately terminate the
         * process, either directly (if this is Android's
         * KillApplicationHandler) OR the app's handler / 3rd party SDK (either
         * directly or more likely, by it calling Android's
         * KillApplicationHandler).
         * Given this, we can't parallelize the existingHandler work with ours.
         * The safest thing is to try to finish our work as fast as possible
         * (including ensuring our logging write buffers are flushed) then call
         * the existingHandler so any crash handlers the app also has gets the
         * crash even too.
         *
         * NOTE: addShutdownHook() isn't a workaround as it doesn't fire for
         * Process.killProcess, which KillApplicationHandler calls.
         */
        runBlocking { _crashReporter.saveCrash(thread, throwable) }
        existingHandler?.uncaughtException(thread, throwable)
    }
}

internal fun isOneSignalAtFault(throwable: Throwable): Boolean =
    throwable.stackTrace.any { it.className.startsWith("com.onesignal") }
