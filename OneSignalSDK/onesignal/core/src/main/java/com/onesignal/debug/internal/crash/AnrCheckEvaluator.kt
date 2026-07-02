package com.onesignal.debug.internal.crash

import java.util.concurrent.atomic.AtomicLong

/**
 * Pure, Android-free decision core for the ANR watchdog.
 *
 * All timing/classification/deduplication state lives here so it can be exercised deterministically
 * on the JVM (with an injected clock) without a `Handler`, `Looper`, or real background thread. The
 * Android shell ([OtelAnrDetector]) owns the thread, the real sleep, and the reporting side effects,
 * and delegates every per-iteration decision to [evaluate].
 *
 * Foreground and background blocks keep independent dedup timestamps: a stream of backgrounded
 * warnings must never suppress a genuine foreground ANR (and vice versa).
 */
internal class AnrCheckEvaluator(
    private val anrThresholdMs: Long,
    private val checkIntervalMs: Long,
    private val backgroundThresholdMs: Long,
    private val frozenSlackMs: Long,
    private val dedupWindowMs: Long,
    private val now: () -> Long,
) {
    // Monotonic timestamps (from `now`); see OtelAnrDetector for why the clock must be monotonic.
    private val lastResponseTime = AtomicLong(now())
    private val lastForegroundReportTime = AtomicLong(NEVER_REPORTED)
    private val lastBackgroundReportTime = AtomicLong(NEVER_REPORTED)

    /** Re-baselines the responsiveness clock, e.g. at start() so a construction->start gap isn't a block. */
    fun resetBaseline() {
        lastResponseTime.set(now())
    }

    /** Records that the main thread ran the heartbeat runnable. */
    fun recordHeartbeat() {
        lastResponseTime.set(now())
    }

    /**
     * Evaluates a single watchdog iteration given how long the watchdog's own sleep actually took and
     * the current app state. Returns the action the shell should take; all dedup bookkeeping is done
     * here so the shell only performs side effects.
     */
    fun evaluate(actualSleepMs: Long, inForeground: Boolean): AnrCheckResult {
        val timeSinceLastResponse = now() - lastResponseTime.get()

        return when (
            classifyBlock(
                timeSinceLastResponseMs = timeSinceLastResponse,
                actualSleepMs = actualSleepMs,
                checkIntervalMs = checkIntervalMs,
                frozenSlackMs = frozenSlackMs,
                anrThresholdMs = anrThresholdMs,
                backgroundThresholdMs = backgroundThresholdMs,
                inForeground = inForeground,
            )
        ) {
            BlockClassification.FROZEN_PROCESS -> {
                // The watchdog thread itself was descheduled (Doze / cached-process freeze). Anything
                // measured for the main thread is a freeze artifact, so re-baseline and treat as
                // responsive to avoid firing on the next iteration.
                lastResponseTime.set(now())
                AnrCheckResult.FrozenProcess(actualSleepMs = actualSleepMs, expectedSleepMs = checkIntervalMs)
            }
            BlockClassification.RESPONSIVE -> {
                clearReportTimestamps()
                AnrCheckResult.Responsive
            }
            BlockClassification.FOREGROUND_ANR ->
                reportOrDedup(timeSinceLastResponse, lastForegroundReportTime, inForeground = true)
            BlockClassification.BACKGROUND_WARNING ->
                reportOrDedup(timeSinceLastResponse, lastBackgroundReportTime, inForeground = false)
        }
    }

    private fun reportOrDedup(
        durationMs: Long,
        lastReportHolder: AtomicLong,
        inForeground: Boolean,
    ): AnrCheckResult {
        val nowMs = now()
        val lastReport = lastReportHolder.get()

        // Skip only if we actually reported this class of block recently. NEVER_REPORTED means we have
        // not reported yet (or the main thread recovered), so we must not dedup — important shortly
        // after boot when the monotonic clock is still small and `now - 0` would look "recent".
        if (lastReport != NEVER_REPORTED && nowMs - lastReport <= dedupWindowMs) {
            return AnrCheckResult.Deduped(durationMs = durationMs, sinceLastReportMs = nowMs - lastReport, inForeground = inForeground)
        }
        lastReportHolder.set(nowMs)
        return if (inForeground) {
            AnrCheckResult.ForegroundAnr(durationMs)
        } else {
            AnrCheckResult.BackgroundWarning(durationMs)
        }
    }

    private fun clearReportTimestamps() {
        // A recovered main thread should let the next real block report immediately, regardless of app state.
        lastForegroundReportTime.set(NEVER_REPORTED)
        lastBackgroundReportTime.set(NEVER_REPORTED)
    }

    companion object {
        // Sentinel meaning "no block of this class reported yet / main thread is healthy".
        private const val NEVER_REPORTED = 0L
    }
}

/** The action the Android shell should take for one watchdog check. */
internal sealed interface AnrCheckResult {
    /** Main thread responded within the applicable threshold. */
    object Responsive : AnrCheckResult

    /** The watchdog's own sleep overran — the process was frozen, not the main thread. */
    data class FrozenProcess(val actualSleepMs: Long, val expectedSleepMs: Long) : AnrCheckResult

    /** A block of this class was already reported within the dedup window; suppress. */
    data class Deduped(val durationMs: Long, val sinceLastReportMs: Long, val inForeground: Boolean) : AnrCheckResult

    /** Foreground block beyond the ANR threshold: a real, user-visible ANR to report as a crash. */
    data class ForegroundAnr(val durationMs: Long) : AnrCheckResult

    /** Background block beyond the background threshold: record as a non-fatal warning, never an ANR. */
    data class BackgroundWarning(val durationMs: Long) : AnrCheckResult
}

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
    val threshold = if (inForeground) anrThresholdMs else backgroundThresholdMs
    return when {
        actualSleepMs - checkIntervalMs > frozenSlackMs -> BlockClassification.FROZEN_PROCESS
        timeSinceLastResponseMs <= threshold -> BlockClassification.RESPONSIVE
        inForeground -> BlockClassification.FOREGROUND_ANR
        else -> BlockClassification.BACKGROUND_WARNING
    }
}

/**
 * Compact fingerprint for a captured main-thread stack: the top frame plus the first OneSignal frame.
 * Kept as a queryable summary so background blocks can be grouped/triaged without parsing the full
 * stack. Pure (operates only on the array) so it is covered by plain JVM tests.
 */
internal fun buildBlockFingerprint(stackTrace: Array<StackTraceElement>): String {
    val topFrame = stackTrace.firstOrNull()?.toString() ?: "unknown"
    val oneSignalFrame = stackTrace.firstOrNull { it.className.startsWith("com.onesignal") }?.toString() ?: "none"
    return "top=$topFrame|onesignal=$oneSignalFrame"
}
