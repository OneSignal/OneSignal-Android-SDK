package com.onesignal.debug.internal.crash

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.onesignal.otel.IOtelCrashReporter
import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.IOtelOpenTelemetryCrash
import com.onesignal.otel.OtelFactory
import com.onesignal.otel.crash.IOtelAnrDetector
import com.onesignal.otel.crash.isOneSignalAtFault
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

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
 *   backgrounded app) — recorded under a distinct exception type so it stays out of the ANR metric
 *   while remaining retained and queryable for real background regressions.
 * - If the watchdog thread's own sleep overran far beyond [checkIntervalMs], the whole process was
 *   descheduled (Doze / cached-process freeze) rather than the main thread being stuck, so the
 *   measured "block" is a freeze artifact and is suppressed.
 *
 * Every timing/classification/dedup decision is delegated to [AnrCheckEvaluator] (pure, JVM-tested),
 * and all Android touch points go through the injectable [AnrWatchdogPlatform] seam so the watchdog's
 * behavior can be exercised deterministically off-device.
 */
internal class OtelAnrDetector(
    openTelemetryCrash: IOtelOpenTelemetryCrash,
    private val logger: IOtelLogger,
    private val anrThresholdMs: Long = AnrConstants.DEFAULT_ANR_THRESHOLD_MS,
    private val checkIntervalMs: Long = AnrConstants.DEFAULT_CHECK_INTERVAL_MS,
    backgroundThresholdMs: Long = AnrConstants.DEFAULT_BACKGROUND_BLOCK_THRESHOLD_MS,
    private val isAppInForeground: () -> Boolean = { true },
    // Android touch points (main-thread Handler, stack capture) plus the monotonic clock, injectable
    // so the whole watchdog runs deterministically off-device.
    private val platform: AnrWatchdogPlatform = AndroidAnrWatchdogPlatform(),
) : IOtelAnrDetector {
    private val crashReporter: IOtelCrashReporter = OtelFactory.createCrashReporter(openTelemetryCrash, logger)
    private val isMonitoring = AtomicBoolean(false)

    private val evaluator = AnrCheckEvaluator(
        anrThresholdMs = anrThresholdMs,
        checkIntervalMs = checkIntervalMs,
        backgroundThresholdMs = backgroundThresholdMs,
        frozenSlackMs = FROZEN_PROCESS_SLACK_MS,
        dedupWindowMs = MIN_TIME_BETWEEN_ANR_REPORTS_MS,
        now = { platform.now() },
    )

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

        // Reset the baseline so a gap between construction and start() can't be read as a block.
        evaluator.resetBaseline()
        setupRunnables()
        startWatchdogThread()

        logger.info("$TAG: ✅ ANR detection started successfully")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun setupRunnables() {
        // Runnable that runs on the main thread to indicate it's responsive
        mainThreadRunnable = Runnable {
            recordHeartbeat()
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

    internal fun checkForAnr() {
        val runnable = mainThreadRunnable ?: return
        platform.postToMainThread(runnable)

        // Time the sleep itself: if our own thread oversleeps, the process was frozen, not blocked.
        val sleepStart = platform.now()
        Thread.sleep(checkIntervalMs)
        evaluateCheck(actualSleepMs = platform.now() - sleepStart)
    }

    /** Records that the main thread ran our heartbeat runnable. */
    internal fun recordHeartbeat() {
        evaluator.recordHeartbeat()
    }

    /**
     * Runs one watchdog iteration's decision (via [AnrCheckEvaluator]) and performs the side effect.
     * Split from [checkForAnr] (which owns the real sleep) so the wiring can be exercised
     * deterministically with an injected clock and platform.
     */
    internal fun evaluateCheck(actualSleepMs: Long) {
        val inForeground = resolveForeground()
        when (val result = evaluator.evaluate(actualSleepMs = actualSleepMs, inForeground = inForeground)) {
            is AnrCheckResult.Responsive -> Unit
            is AnrCheckResult.FrozenProcess ->
                logger.debug(
                    "$TAG: Skipping check — watchdog overslept ${result.actualSleepMs}ms " +
                        "(expected ${result.expectedSleepMs}ms); process was frozen, not blocked",
                )
            is AnrCheckResult.Deduped ->
                logger.debug(
                    "$TAG: Block still ongoing (${result.durationMs}ms), already reported recently (${result.sinceLastReportMs}ms ago)",
                )
            is AnrCheckResult.ForegroundAnr -> {
                logger.info("$TAG: ⚠️ ANR detected! Main thread unresponsive for ${result.durationMs}ms (foreground)")
                reportAnr(result.durationMs)
            }
            is AnrCheckResult.BackgroundWarning -> {
                logger.info("$TAG: Main thread blocked for ${result.durationMs}ms while backgrounded — recording warning, not ANR")
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
            logger.debug("$TAG: Could not resolve app state (${t.message}), assuming foreground")
            true
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
        mainThreadRunnable?.let { platform.removeFromMainThread(it) }
        mainThreadRunnable = null

        logger.info("$TAG: ✅ ANR detection stopped")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun reportAnr(unresponsiveDurationMs: Long) {
        try {
            logger.info("$TAG: Checking if ANR is OneSignal-related (unresponsive for ${unresponsiveDurationMs}ms)")

            val mainThread = platform.mainThread()
            val stackTrace = platform.mainThreadStackTrace()

            // Only report if OneSignal is at fault (uses centralized utility from otel module)
            if (!isOneSignalAtFault(stackTrace)) {
                logger.debug("$TAG: ANR is not OneSignal-related, skipping report")
                return
            }

            logger.info("$TAG: OneSignal-related ANR detected, reporting...")

            // Create an ANR exception with the stack trace
            val anrException = ApplicationNotRespondingException(
                "Application Not Responding: Main thread blocked for ${unresponsiveDurationMs}ms",
                stackTrace,
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
     * Records a backgrounded main-thread block as a retained warning rather than an ANR.
     *
     * Android raises no ANR for a backgrounded app, so this is not a user-visible crash. We route it
     * through the same retained, disk-buffered crash telemetry as [reportAnr] (so it survives
     * regardless of the remote log level and stays queryable), but tag it with a distinct exception
     * type — [BackgroundMainThreadBlockException] — so the backend segments it into its own stream and
     * keeps it out of the ANR/crash metric, exactly how ANRs are already segmented from crashes by
     * type. The exception message carries a compact stack fingerprint (top frame + first OneSignal
     * frame) for triage. Like [reportAnr], only OneSignal-induced blocks are recorded.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun reportBackgroundBlock(unresponsiveDurationMs: Long) {
        try {
            val mainThread = platform.mainThread()
            val stackTrace = platform.mainThreadStackTrace()

            if (!isOneSignalAtFault(stackTrace)) {
                logger.debug("$TAG: Background block is not OneSignal-related, skipping")
                return
            }

            val blockException = BackgroundMainThreadBlockException(
                "Background main-thread block for ${unresponsiveDurationMs}ms | ${buildBlockFingerprint(stackTrace)}",
                stackTrace,
            )

            runBlocking {
                crashReporter.saveCrash(mainThread, blockException)
            }

            logger.info("$TAG: ✅ Background block warning recorded")
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
        stackTrace: Array<StackTraceElement>,
    ) : RuntimeException(message) {
        init {
            this.stackTrace = stackTrace
        }
    }

    /**
     * Custom exception type for backgrounded main-thread blocks. A distinct type keeps these out of
     * the ANR/crash buckets on the backend while remaining queryable as their own stream.
     */
    private class BackgroundMainThreadBlockException(
        message: String,
        stackTrace: Array<StackTraceElement>,
    ) : RuntimeException(message) {
        init {
            this.stackTrace = stackTrace
        }
    }
}

/**
 * The platform touch points the watchdog needs: posting to the main thread, capturing its stack, and
 * a monotonic clock. Injecting this keeps [OtelAnrDetector] free of hard Android references at test
 * time so the watchdog logic can be driven on a plain JVM.
 */
internal interface AnrWatchdogPlatform {
    fun postToMainThread(runnable: Runnable)

    fun removeFromMainThread(runnable: Runnable)

    fun mainThread(): Thread

    fun mainThreadStackTrace(): Array<StackTraceElement>

    /**
     * Monotonic time in ms. Backed by SystemClock.uptimeMillis in production: it can't be skewed by
     * clock adjustments (NTP, manual time change, DST), it matches the clock the main Looper schedules
     * with, and it pauses during deep sleep so a dozing device doesn't accumulate phantom block time.
     */
    fun now(): Long
}

/** Production [AnrWatchdogPlatform] backed by the app's main [Looper] and [SystemClock]. */
private class AndroidAnrWatchdogPlatform : AnrWatchdogPlatform {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun postToMainThread(runnable: Runnable) {
        mainHandler.post(runnable)
    }

    override fun removeFromMainThread(runnable: Runnable) {
        mainHandler.removeCallbacks(runnable)
    }

    override fun mainThread(): Thread = Looper.getMainLooper().thread

    override fun mainThreadStackTrace(): Array<StackTraceElement> = Looper.getMainLooper().thread.stackTrace

    override fun now(): Long = SystemClock.uptimeMillis()
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
