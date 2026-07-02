package com.onesignal.debug.internal.logging.logger.android

import android.os.Handler
import android.os.Looper
import com.onesignal.debug.internal.crash.AnrConstants
import com.onesignal.logger.CrashData
import com.onesignal.logger.ILogAnrDetector
import com.onesignal.logger.ILogCrashReporter
import com.onesignal.logger.ILogger
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Android [ILogAnrDetector] — watchdog that detects when the main thread is blocked
 * and, if OneSignal is at fault, persists an ANR report via the `logger` module's
 * [ILogCrashReporter]. Direct analogue of `OtelAnrDetector`, but transport-agnostic.
 */
internal class AndroidLogAnrDetector(
    private val crashReporter: ILogCrashReporter,
    private val logger: ILogger,
    private val anrThresholdMs: Long = AnrConstants.DEFAULT_ANR_THRESHOLD_MS,
    private val checkIntervalMs: Long = AnrConstants.DEFAULT_CHECK_INTERVAL_MS,
) : ILogAnrDetector {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isMonitoring = AtomicBoolean(false)
    private val lastResponseTime = AtomicLong(System.currentTimeMillis())
    private val lastAnrReportTime = AtomicLong(0L)
    private var watchdogThread: Thread? = null
    private var watchdogRunnable: Runnable? = null
    private var mainThreadRunnable: Runnable? = null

    private companion object {
        const val TAG = "AndroidLogAnrDetector"
        const val MIN_TIME_BETWEEN_ANR_REPORTS_MS = 30_000L
    }

    override fun start() {
        if (isMonitoring.getAndSet(true)) {
            logger.warn("$TAG: already monitoring, skipping start")
            return
        }
        setupRunnables()
        watchdogThread = Thread(watchdogRunnable, "OneSignal-Log-ANR-Watchdog").apply {
            isDaemon = true
            start()
        }
        logger.info("$TAG: ANR detection started")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun setupRunnables() {
        mainThreadRunnable = Runnable { lastResponseTime.set(System.currentTimeMillis()) }
        watchdogRunnable = Runnable {
            while (isMonitoring.get()) {
                try {
                    checkForAnr()
                } catch (e: InterruptedException) {
                    break
                } catch (t: Throwable) {
                    logger.error("$TAG: error in watchdog: ${t.message}")
                }
            }
        }
    }

    private fun checkForAnr() {
        val runnable = mainThreadRunnable ?: return
        mainHandler.post(runnable)
        Thread.sleep(checkIntervalMs)
        val timeSinceLastResponse = System.currentTimeMillis() - lastResponseTime.get()
        if (timeSinceLastResponse > anrThresholdMs) {
            handleAnrDetected(timeSinceLastResponse)
        } else if (lastAnrReportTime.get() > 0) {
            lastAnrReportTime.set(0L)
        }
    }

    private fun handleAnrDetected(timeSinceLastResponse: Long) {
        val now = System.currentTimeMillis()
        if (now - lastAnrReportTime.get() > MIN_TIME_BETWEEN_ANR_REPORTS_MS) {
            lastAnrReportTime.set(now)
            reportAnr(timeSinceLastResponse)
        }
    }

    override fun stop() {
        if (!isMonitoring.getAndSet(false)) {
            logger.warn("$TAG: not monitoring, skipping stop")
            return
        }
        watchdogThread?.interrupt()
        watchdogThread = null
        watchdogRunnable = null
        mainThreadRunnable?.let { mainHandler.removeCallbacks(it) }
        mainThreadRunnable = null
        logger.info("$TAG: ANR detection stopped")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun reportAnr(unresponsiveDurationMs: Long) {
        try {
            val mainThread = Looper.getMainLooper().thread
            val stackTrace = mainThread.stackTrace
            if (!isOneSignalAtFault(stackTrace)) {
                logger.debug("$TAG: ANR not OneSignal-related, skipping report")
                return
            }
            val crash =
                CrashData(
                    threadName = mainThread.name,
                    exceptionType = "ApplicationNotRespondingException",
                    exceptionMessage = "Application Not Responding: Main thread blocked for ${unresponsiveDurationMs}ms",
                    stacktrace = stackTrace.joinToString("\n") { it.toString() },
                )
            runBlocking { crashReporter.saveCrash(crash) }
            logger.info("$TAG: ANR report saved")
        } catch (t: Throwable) {
            logger.error("$TAG: failed to report ANR: ${t.message}")
        }
    }
}
