package com.onesignal.debug.internal.crash

import android.os.Handler
import android.os.Looper
import com.onesignal.otel.IOtelCrashReporter
import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.IOtelOpenTelemetryCrash
import com.onesignal.otel.OtelFactory
import com.onesignal.otel.crash.IOtelAnrDetector
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Android-specific implementation of ANR detection.
 *
 * Uses a watchdog pattern to monitor the main thread:
 * - Posts a message to the main thread every check interval
 * - If the main thread doesn't respond within the ANR threshold, reports an ANR
 * - Captures the main thread's stack trace when ANR is detected
 *
 * This is a standalone component that can be initialized independently of the crash handler.
 * It creates its own crash reporter to save ANR reports.
 */
internal class OtelAnrDetector(
    openTelemetryCrash: IOtelOpenTelemetryCrash,
    private val logger: IOtelLogger,
    private val anrThresholdMs: Long = AnrConstants.DEFAULT_ANR_THRESHOLD_MS,
    private val checkIntervalMs: Long = AnrConstants.DEFAULT_CHECK_INTERVAL_MS,
) : IOtelAnrDetector {
    private val crashReporter: IOtelCrashReporter = OtelFactory.createCrashReporter(openTelemetryCrash, logger)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isMonitoring = AtomicBoolean(false)
    private val lastResponseTime = AtomicLong(System.currentTimeMillis())
    private val lastAnrReportTime = AtomicLong(0L)
    private var watchdogThread: Thread? = null
    private var watchdogRunnable: Runnable? = null
    private var mainThreadRunnable: Runnable? = null

    companion object {
        private const val TAG = "OtelAnrDetector"

        // Minimum time between ANR reports (to avoid duplicate reports for the same ANR)
        private const val MIN_TIME_BETWEEN_ANR_REPORTS_MS = 30_000L // 30 seconds
    }

    override fun start() {
        if (isMonitoring.getAndSet(true)) {
            logger.warn("$TAG: Already monitoring for ANRs, skipping start")
            return
        }

        logger.info("$TAG: Starting ANR detection (threshold: ${anrThresholdMs}ms, check interval: ${checkIntervalMs}ms)")

        setupRunnables()
        startWatchdogThread()

        logger.info("$TAG: ✅ ANR detection started successfully")
    }

    private fun setupRunnables() {
        // Runnable that runs on the main thread to indicate it's responsive
        mainThreadRunnable = Runnable {
            lastResponseTime.set(System.currentTimeMillis())
        }

        // Runnable that runs on the watchdog thread to check for ANRs
        watchdogRunnable = Runnable {
            while (isMonitoring.get()) {
                try {
                    checkForAnr()
                } catch (e: InterruptedException) {
                    // Thread was interrupted, stop monitoring
                    logger.info("$TAG: Watchdog thread interrupted, stopping ANR detection")
                    break
                } catch (t: Throwable) {
                    logger.error("$TAG: Error in ANR watchdog: ${t.message} - ${t.javaClass.simpleName}")
                }
            }
        }
    }

    private fun checkForAnr() {
        // Post a message to the main thread
        mainHandler.post(mainThreadRunnable!!)

        // Wait for the check interval
        Thread.sleep(checkIntervalMs)

        // Check if main thread responded
        val timeSinceLastResponse = System.currentTimeMillis() - lastResponseTime.get()
        if (timeSinceLastResponse > anrThresholdMs) {
            handleAnrDetected(timeSinceLastResponse)
        } else {
            handleMainThreadResponsive()
        }
    }

    private fun handleAnrDetected(timeSinceLastResponse: Long) {
        // Main thread hasn't responded - ANR detected!
        val now = System.currentTimeMillis()
        val timeSinceLastReport = now - lastAnrReportTime.get()

        // Only report if enough time has passed since last report (avoid duplicates)
        if (timeSinceLastReport > MIN_TIME_BETWEEN_ANR_REPORTS_MS) {
            logger.warn("$TAG: ⚠️ ANR detected! Main thread unresponsive for ${timeSinceLastResponse}ms")
            lastAnrReportTime.set(now)
            reportAnr(timeSinceLastResponse)
        } else {
            logger.debug("$TAG: ANR still ongoing (${timeSinceLastResponse}ms), but already reported recently (${timeSinceLastReport}ms ago)")
        }
    }

    private fun handleMainThreadResponsive() {
        // Main thread is responsive - reset ANR report time so we can detect new ANRs
        if (lastAnrReportTime.get() > 0) {
            lastAnrReportTime.set(0L)
            logger.debug("$TAG: Main thread recovered, ready to detect new ANRs")
        }
    }

    private fun startWatchdogThread() {
        // Start the watchdog thread
        watchdogThread = Thread(watchdogRunnable, "OneSignal-ANR-Watchdog")
        watchdogThread?.isDaemon = true
        watchdogThread?.start()
    }

    override fun stop() {
        if (!isMonitoring.getAndSet(false)) {
            logger.warn("$TAG: Not monitoring, skipping stop")
            return
        }

        logger.info("$TAG: Stopping ANR detection...")

        // Interrupt the watchdog thread to stop it
        watchdogThread?.interrupt()
        watchdogThread = null
        watchdogRunnable = null
        // Remove pending callbacks before nulling to prevent execution after stop
        mainThreadRunnable?.let { mainHandler.removeCallbacks(it) }
        mainThreadRunnable = null

        logger.info("$TAG: ✅ ANR detection stopped")
    }

    private fun reportAnr(unresponsiveDurationMs: Long) {
        try {
            logger.info("$TAG: Checking if ANR is OneSignal-related (unresponsive for ${unresponsiveDurationMs}ms)")

            // Get the main thread's stack trace
            val mainThread = Looper.getMainLooper().thread
            val stackTrace = mainThread.stackTrace

            // Only report if OneSignal is at fault (uses centralized utility from otel module)
            val isOneSignalAtFault = com.onesignal.otel.crash.isOneSignalAtFault(stackTrace)

            if (!isOneSignalAtFault) {
                logger.debug("$TAG: ANR is not OneSignal-related, skipping report")
                return
            }

            logger.info("$TAG: OneSignal-related ANR detected, reporting...")

            // Create an ANR exception with the stack trace
            val anrException = ApplicationNotRespondingException(
                "Application Not Responding: Main thread blocked for ${unresponsiveDurationMs}ms",
                stackTrace
            )

            // Report it as a crash (but mark it as ANR)
            runBlocking {
                crashReporter.saveCrash(mainThread, anrException)
            }

            logger.info("$TAG: ✅ ANR report saved successfully")
        } catch (t: Throwable) {
            logger.error("$TAG: Failed to report ANR: ${t.message} - ${t.javaClass.simpleName}")
        }
    }

    /**
     * Custom exception type for ANRs.
     * This allows us to distinguish ANRs from regular crashes in the crash reporting system.
     */
    private class ApplicationNotRespondingException(
        message: String,
        stackTrace: Array<StackTraceElement>
    ) : RuntimeException(message) {
        init {
            this.stackTrace = stackTrace
        }
    }
}

// Use the centralized isOneSignalAtFault from otel module

/**
 * Factory function to create an ANR detector for Android.
 * This is in the core module since it needs to access Android-specific classes.
 */

internal fun createAnrDetector(
    platformProvider: com.onesignal.otel.IOtelPlatformProvider,
    logger: IOtelLogger,
    anrThresholdMs: Long = AnrConstants.DEFAULT_ANR_THRESHOLD_MS,
    checkIntervalMs: Long = AnrConstants.DEFAULT_CHECK_INTERVAL_MS,
): IOtelAnrDetector {
    // Use the factory to create crash local instance (keeps implementation details internal)
    val crashLocal = OtelFactory.createCrashLocalTelemetry(platformProvider)

    return OtelAnrDetector(
        crashLocal,
        logger,
        anrThresholdMs,
        checkIntervalMs
    )
}
