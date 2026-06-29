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
 * - If the main thread doesn't respond within the threshold, classifies and records the block
 * - Captures the main thread's stack trace when a block is detected
 *
 * Detection is app-state aware, because a blocked main thread does not mean the same thing in
 * the foreground as in the background:
 * - Foreground block > [anrThresholdMs]: a real, user-visible ANR — reported as a crash-class ANR.
 * - Background block > [backgroundThresholdMs]: not an ANR (Android raises no ANR for a
 *   backgrounded app) — recorded as a lower-severity warning so it stays out of the ANR metric.
 * - If the watchdog thread's own sleep overran far beyond [checkIntervalMs], the whole process was
 *   descheduled (Doze / cached-process freeze) rather than the main thread being stuck, so the
 *   measured "block" is a freeze artifact and is suppressed.
 *
 * This is a standalone component that can be initialized independently of the crash handler.
 * It creates its own crash reporter to save ANR reports.
 */
internal class OtelAnrDetector(
    openTelemetryCrash: IOtelOpenTelemetryCrash,
    private val logger: IOtelLogger,
    private val anrThresholdMs: Long = AnrConstants.DEFAULT_ANR_THRESHOLD_MS,
    private val checkIntervalMs: Long = AnrConstants.DEFAULT_CHECK_INTERVAL_MS,
    private val backgroundThresholdMs: Long = AnrConstants.DEFAULT_BACKGROUND_BLOCK_THRESHOLD_MS,
    private val isAppInForeground: () -> Boolean = { true },
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

        // Minimum time between reports (to avoid duplicate reports for the same ongoing block)
        private const val MIN_TIME_BETWEEN_ANR_REPORTS_MS = 30_000L // 30 seconds

        // If the watchdog thread's own sleep overruns the check interval by more than this, the
        // process itself was frozen/descheduled rather than the main thread being blocked, so the
        // measurement is meaningless. Generous enough to tolerate GC pauses and CPU contention.
        private const val FROZEN_PROCESS_SLACK_MS = 2_000L
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

    @Suppress("TooGenericExceptionCaught")
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
        val runnable = mainThreadRunnable ?: return
        mainHandler.post(runnable)

        // Time the sleep itself: if our own thread oversleeps, the process was frozen, not blocked.
        val sleepStart = System.currentTimeMillis()
        Thread.sleep(checkIntervalMs)
        val actualSleepMs = System.currentTimeMillis() - sleepStart

        val inForeground = resolveForeground()
        val timeSinceLastResponse = System.currentTimeMillis() - lastResponseTime.get()

        when (
            classifyBlock(
                timeSinceLastResponseMs = timeSinceLastResponse,
                actualSleepMs = actualSleepMs,
                checkIntervalMs = checkIntervalMs,
                frozenSlackMs = FROZEN_PROCESS_SLACK_MS,
                anrThresholdMs = anrThresholdMs,
                backgroundThresholdMs = backgroundThresholdMs,
                inForeground = inForeground,
            )
        ) {
            BlockClassification.FROZEN_PROCESS -> {
                // The watchdog thread itself was descheduled (Doze / cached-process freeze). Anything
                // we measured for the main thread is a freeze artifact, so skip it and treat the main
                // thread as responsive to avoid firing on the next iteration.
                logger.debug(
                    "$TAG: Skipping check — watchdog overslept ${actualSleepMs}ms (expected ${checkIntervalMs}ms); process was frozen, not blocked",
                )
                lastResponseTime.set(System.currentTimeMillis())
            }
            BlockClassification.RESPONSIVE -> handleMainThreadResponsive()
            BlockClassification.FOREGROUND_ANR -> reportBlockOnce(timeSinceLastResponse, inForeground = true)
            BlockClassification.BACKGROUND_WARNING -> reportBlockOnce(timeSinceLastResponse, inForeground = false)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun resolveForeground(): Boolean =
        try {
            isAppInForeground()
        } catch (t: Throwable) {
            // Unknown state: treat as foreground so a genuine ANR is never silently downgraded.
            logger.debug("$TAG: Could not resolve app state (${t.message}), assuming foreground")
            true
        }

    private fun reportBlockOnce(timeSinceLastResponse: Long, inForeground: Boolean) {
        val now = System.currentTimeMillis()
        val timeSinceLastReport = now - lastAnrReportTime.get()

        // Only report if enough time has passed since the last report (avoid duplicates).
        if (timeSinceLastReport <= MIN_TIME_BETWEEN_ANR_REPORTS_MS) {
            logger.debug("$TAG: Block still ongoing (${timeSinceLastResponse}ms), already reported recently (${timeSinceLastReport}ms ago)")
            return
        }
        lastAnrReportTime.set(now)

        if (inForeground) {
            logger.info("$TAG: ⚠️ ANR detected! Main thread unresponsive for ${timeSinceLastResponse}ms (foreground)")
            reportAnr(timeSinceLastResponse)
        } else {
            logger.info("$TAG: Main thread blocked for ${timeSinceLastResponse}ms while backgrounded — recording warning, not ANR")
            reportBackgroundBlock(timeSinceLastResponse)
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

    @Suppress("TooGenericExceptionCaught")
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
     * Records a backgrounded main-thread block as a warning rather than an ANR.
     *
     * Android raises no ANR for a backgrounded app, so this is not a user-visible crash. We keep the
     * captured stack trace for diagnostics but route it through the warning log stream so it stays
     * out of the ANR/crash metric. Like [reportAnr], only OneSignal-induced blocks are recorded.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun reportBackgroundBlock(unresponsiveDurationMs: Long) {
        try {
            val mainThread = Looper.getMainLooper().thread
            val stackTrace = mainThread.stackTrace

            if (!com.onesignal.otel.crash.isOneSignalAtFault(stackTrace)) {
                logger.debug("$TAG: Background block is not OneSignal-related, skipping")
                return
            }

            val frames = stackTrace.joinToString("\n") { "    at $it" }
            logger.warn(
                "$TAG: OneSignal-related main-thread block for ${unresponsiveDurationMs}ms while " +
                    "backgrounded (not a user-visible ANR)\n$frames",
            )
        } catch (t: Throwable) {
            logger.error("$TAG: Failed to record background block: ${t.message} - ${t.javaClass.simpleName}")
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
 * How a watchdog check is interpreted. Kept separate from side effects so the decision is a pure,
 * deterministically testable function of the measured timings and app state.
 */
internal enum class BlockClassification {
    /** Main thread responded within the applicable threshold. */
    RESPONSIVE,

    /** The watchdog thread's own sleep overran — the process was frozen, not the main thread. */
    FROZEN_PROCESS,

    /** Foreground block beyond the ANR threshold: a real, user-visible ANR. */
    FOREGROUND_ANR,

    /** Background block beyond the background threshold: not an ANR, recorded as a warning. */
    BACKGROUND_WARNING,
}

/**
 * Pure decision for a single watchdog check.
 *
 * Frozen-process detection wins first: if the watchdog's own sleep overran [checkIntervalMs] by more
 * than [frozenSlackMs], the whole process was descheduled and any measured block is an artifact.
 * Otherwise the applicable threshold depends on app state — [anrThresholdMs] in the foreground (where
 * Android raises real ANRs) and the higher [backgroundThresholdMs] in the background.
 */
@Suppress("LongParameterList")
internal fun classifyBlock(
    timeSinceLastResponseMs: Long,
    actualSleepMs: Long,
    checkIntervalMs: Long,
    frozenSlackMs: Long,
    anrThresholdMs: Long,
    backgroundThresholdMs: Long,
    inForeground: Boolean,
): BlockClassification {
    if (actualSleepMs - checkIntervalMs > frozenSlackMs) {
        return BlockClassification.FROZEN_PROCESS
    }
    val threshold = if (inForeground) anrThresholdMs else backgroundThresholdMs
    if (timeSinceLastResponseMs <= threshold) {
        return BlockClassification.RESPONSIVE
    }
    return if (inForeground) BlockClassification.FOREGROUND_ANR else BlockClassification.BACKGROUND_WARNING
}

/**
 * Factory function to create an ANR detector for Android.
 * This is in the core module since it needs to access Android-specific classes.
 */

internal fun createAnrDetector(
    platformProvider: com.onesignal.otel.IOtelPlatformProvider,
    logger: IOtelLogger,
    anrThresholdMs: Long = AnrConstants.DEFAULT_ANR_THRESHOLD_MS,
    checkIntervalMs: Long = AnrConstants.DEFAULT_CHECK_INTERVAL_MS,
    backgroundThresholdMs: Long = AnrConstants.DEFAULT_BACKGROUND_BLOCK_THRESHOLD_MS,
): IOtelAnrDetector {
    // Use the factory to create crash local instance (keeps implementation details internal)
    val crashLocal = OtelFactory.createCrashLocalTelemetry(platformProvider)

    return OtelAnrDetector(
        crashLocal,
        logger,
        anrThresholdMs,
        checkIntervalMs,
        backgroundThresholdMs,
        // appState is computed per access. Only "background" downgrades to a warning; "unknown"
        // is treated as foreground so a genuine ANR is never silently dropped.
        isAppInForeground = { platformProvider.appState != "background" },
    )
}
