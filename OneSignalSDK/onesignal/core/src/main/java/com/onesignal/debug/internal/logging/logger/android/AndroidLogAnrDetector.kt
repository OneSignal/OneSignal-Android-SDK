package com.onesignal.debug.internal.logging.logger.android

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.onesignal.debug.internal.crash.AnrCheckEvaluator
import com.onesignal.debug.internal.crash.AnrCheckResult
import com.onesignal.debug.internal.crash.AnrConstants
import com.onesignal.debug.internal.crash.buildAnrCrashData
import com.onesignal.debug.internal.crash.buildBackgroundBlockCrashData
import com.onesignal.logger.ILogAnrDetector
import com.onesignal.logger.ILogCrashReporter
import com.onesignal.logger.ILogger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android [ILogAnrDetector]: owns the watchdog thread and the reporting side effects. Every
 * timing, classification and dedup decision belongs in [AnrCheckEvaluator], not here.
 */
internal class AndroidLogAnrDetector(
    private val crashReporter: ILogCrashReporter,
    private val logger: ILogger,
    private val anrThresholdMs: Long = AnrConstants.DEFAULT_ANR_THRESHOLD_MS,
    private val checkIntervalMs: Long = AnrConstants.DEFAULT_CHECK_INTERVAL_MS,
    backgroundThresholdMs: Long = AnrConstants.DEFAULT_BACKGROUND_BLOCK_THRESHOLD_MS,
    private val isAppInForeground: () -> Boolean = { true },
) : ILogAnrDetector {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isMonitoring = AtomicBoolean(false)

    private val evaluator =
        AnrCheckEvaluator(
            anrThresholdMs = anrThresholdMs,
            checkIntervalMs = checkIntervalMs,
            backgroundThresholdMs = backgroundThresholdMs,
            frozenSlackMs = FROZEN_PROCESS_SLACK_MS,
            dedupWindowMs = MIN_TIME_BETWEEN_ANR_REPORTS_MS,
            // Monotonic clock: SystemClock.uptimeMillis matches the main Looper's scheduling
            // clock and can't be skewed by NTP/time changes.
            now = { SystemClock.uptimeMillis() },
        )

    private var watchdogThread: Thread? = null
    private var watchdogRunnable: Runnable? = null
    private var mainThreadRunnable: Runnable? = null

    private companion object {
        const val TAG = "AndroidLogAnrDetector"
        const val MIN_TIME_BETWEEN_ANR_REPORTS_MS = 30_000L
        const val FROZEN_PROCESS_SLACK_MS = 2_000L
    }

    override fun start() {
        if (isMonitoring.getAndSet(true)) {
            logger.warn("$TAG: already monitoring, skipping start")
            return
        }
        // Reset the baseline so a gap between construction and start() can't be read as a block.
        evaluator.resetBaseline()
        setupRunnables()
        watchdogThread =
            Thread(watchdogRunnable, "OneSignal-Log-ANR-Watchdog").apply {
                isDaemon = true
                start()
            }
        logger.info("$TAG: ANR detection started")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun setupRunnables() {
        mainThreadRunnable = Runnable { evaluator.recordHeartbeat() }
        watchdogRunnable =
            Runnable {
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
        // Time the sleep itself: if our own thread oversleeps, the process was frozen, not blocked.
        val sleepStart = SystemClock.uptimeMillis()
        Thread.sleep(checkIntervalMs)
        evaluateCheck(actualSleepMs = SystemClock.uptimeMillis() - sleepStart)
    }

    private fun evaluateCheck(actualSleepMs: Long) {
        when (val result = evaluator.evaluate(actualSleepMs = actualSleepMs, inForeground = resolveForeground())) {
            is AnrCheckResult.Responsive -> Unit
            is AnrCheckResult.FrozenProcess ->
                logger.debug(
                    "$TAG: watchdog overslept ${result.actualSleepMs}ms (expected ${result.expectedSleepMs}ms); " +
                        "process was frozen, not blocked",
                )
            is AnrCheckResult.Deduped ->
                logger.debug(
                    "$TAG: block still ongoing (${result.durationMs}ms), already reported ${result.sinceLastReportMs}ms ago",
                )
            is AnrCheckResult.ForegroundAnr -> {
                logger.info("$TAG: ANR detected! Main thread unresponsive for ${result.durationMs}ms (foreground)")
                reportAnr(result.durationMs)
            }
            is AnrCheckResult.BackgroundWarning -> {
                logger.info("$TAG: main thread blocked ${result.durationMs}ms while backgrounded — warning, not ANR")
                reportBackgroundBlock(result.durationMs)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun resolveForeground(): Boolean =
        try {
            isAppInForeground()
        } catch (t: Throwable) {
            // Unknown state: treat as foreground so a genuine ANR is never silently downgraded.
            logger.debug("$TAG: could not resolve app state (${t.message}), assuming foreground")
            true
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
            crashReporter.saveCrash(buildAnrCrashData(mainThread.name, stackTrace, unresponsiveDurationMs))
            logger.info("$TAG: ANR report saved")
        } catch (t: Throwable) {
            logger.error("$TAG: failed to report ANR: ${t.message}")
        }
    }

    /** Non-fatal, under a distinct exception type, so backgrounded blocks stay out of the ANR metric. */
    @Suppress("TooGenericExceptionCaught")
    private fun reportBackgroundBlock(unresponsiveDurationMs: Long) {
        try {
            val mainThread = Looper.getMainLooper().thread
            val stackTrace = mainThread.stackTrace
            if (!isOneSignalAtFault(stackTrace)) {
                logger.debug("$TAG: background block not OneSignal-related, skipping")
                return
            }
            crashReporter.saveNonFatal(buildBackgroundBlockCrashData(mainThread.name, stackTrace, unresponsiveDurationMs))
            logger.info("$TAG: background block warning recorded")
        } catch (t: Throwable) {
            logger.error("$TAG: failed to record background block: ${t.message}")
        }
    }
}
