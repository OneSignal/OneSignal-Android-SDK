package com.onesignal.otel.crash

import com.onesignal.otel.IOtelCrashReporter
import com.onesignal.otel.IOtelLogger
import kotlinx.coroutines.runBlocking

/**
 * Purpose: Writes any crashes involving OneSignal to a file where they can
 *          later be send to OneSignal to help improve reliability.
 * NOTE: For future refactors, code is written assuming this is a singleton
 *
 * This should be initialized as early as possible, before any other initialization
 * that might crash. All fields must be pre-populated before initialization.
 */
internal class OtelCrashHandler(
    private val crashReporter: IOtelCrashReporter,
    private val logger: IOtelLogger,
) : Thread.UncaughtExceptionHandler, com.onesignal.otel.IOtelCrashHandler {
    private var existingHandler: Thread.UncaughtExceptionHandler? = null
    private val seenThrowables: MutableList<Throwable> = mutableListOf()
    private var initialized = false

    override fun initialize() {
        if (initialized) {
            logger.warn("OtelCrashHandler already initialized, skipping")
            return
        }
        logger.info("OtelCrashHandler: Setting up uncaught exception handler...")
        existingHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
        initialized = true
        logger.info("OtelCrashHandler: ✅ Successfully initialized and registered as default uncaught exception handler")
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Ensure we never attempt to process the same throwable instance
        // more than once. This would only happen if there was another crash
        // handler and was faulty in a specific way.
        synchronized(seenThrowables) {
            if (seenThrowables.contains(throwable)) {
                logger.warn("OtelCrashHandler: Ignoring duplicate throwable instance")
                return
            }
            seenThrowables.add(throwable)
        }

        logger.info("OtelCrashHandler: Uncaught exception detected - ${throwable.javaClass.simpleName}: ${throwable.message}")

        // Check if this is an ANR exception (though standalone ANR detector already handles ANRs)
        // This would only catch ANRs if they're thrown as exceptions, which is rare
        val isAnr = throwable.javaClass.simpleName.contains("ApplicationNotResponding", ignoreCase = true) ||
            throwable.message?.contains("Application Not Responding", ignoreCase = true) == true

        // NOTE: Future improvements:
        // - Catch anything we may throw and print only to logcat
        // - Send a stop command to OneSignalCrashUploader, give a bit of time to finish
        //   and then call existingHandler. This way the app doesn't have to open a 2nd
        //   time to get the crash report and should help prevent duplicated reports.
        // NOTE: ANRs are typically detected by the standalone OtelAnrDetector, which only
        // reports OneSignal-related ANRs. This handler would only catch ANRs if they're
        // thrown as exceptions (unlikely), and we still check if OneSignal is at fault.
        if (!isAnr && !isOneSignalAtFault(throwable)) {
            logger.debug("OtelCrashHandler: Crash is not OneSignal-related, delegating to existing handler")
            existingHandler?.uncaughtException(thread, throwable)
            return
        }

        if (isAnr) {
            logger.info("OtelCrashHandler: ANR exception caught (unusual - ANRs are usually detected by standalone detector)")
        }

        logger.info("OtelCrashHandler: OneSignal-related crash detected, saving crash report...")

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
        try {
            runBlocking { crashReporter.saveCrash(thread, throwable) }
            logger.info("OtelCrashHandler: Crash report saved successfully")
        } catch (e: RuntimeException) {
            // If crash reporting fails, at least try to log it
            logger.error("OtelCrashHandler: Failed to save crash report: ${e.message} - ${e.javaClass.simpleName}")
        } catch (e: java.io.IOException) {
            // Handle IO errors specifically
            logger.error("OtelCrashHandler: IO error saving crash report: ${e.message}")
        } catch (e: IllegalStateException) {
            // Handle illegal state errors
            logger.error("OtelCrashHandler: Illegal state error saving crash report: ${e.message}")
        }
        logger.info("OtelCrashHandler: Delegating to existing crash handler")
        existingHandler?.uncaughtException(thread, throwable)
    }
}

/**
 * Checks if a throwable's stack trace indicates OneSignal is at fault.
 * Centralized logic used by both crash handler and ANR detector.
 */
internal fun isOneSignalAtFault(throwable: Throwable): Boolean =
    isOneSignalAtFault(throwable.stackTrace)

/**
 * Helper function to check if a stack trace indicates OneSignal is at fault.
 * Centralized logic used by both crash handler and ANR detector.
 * Made public so it can be accessed from core module.
 */
fun isOneSignalAtFault(stackTrace: Array<StackTraceElement>): Boolean =
    stackTrace.any { it.className.startsWith("com.onesignal") }
