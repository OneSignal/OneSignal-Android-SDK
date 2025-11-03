package com.onesignal.debug.internal.crash

import android.util.Log
import kotlinx.coroutines.runBlocking

// NOTE: For future refactors, code is written assuming this is a singleton
internal class OneSignalCrashHandler(
    private val _crashReporter: IOneSignalCrashReporter,
) : IOneSignalCrashHandler,
    Thread.UncaughtExceptionHandler {
    private val existingHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    // TODO: Write the code to call this after we get the appId
    // Recommend we only create an instance after getting a appId, otherwise there
    // is no point setting up the handler.
    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // TODO: Catch anything we may throw and silence it (print only to logcat)
        // TODO: Add stackoverflow loop prevention
        Log.e("OSCrashHandling", "uncaughtException TOP")
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
        runBlocking { _crashReporter.sendCrash(thread, throwable) }
        existingHandler?.uncaughtException(thread, throwable)
    }
}

internal fun isOneSignalAtFault(throwable: Throwable): Boolean =
    throwable.stackTrace.any { it.className.startsWith("com.onesignal") }
