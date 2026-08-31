package com.onesignal.debug.internal.crash

import com.onesignal.logger.CrashData
import java.util.concurrent.atomic.AtomicLong

/**
 * Pure decision core for the ANR watchdog: all timing, classification and dedup state. Foreground
 * and background blocks keep independent dedup timestamps, so neither can suppress the other.
 */
internal class AnrCheckEvaluator(
    private val anrThresholdMs: Long,
    private val checkIntervalMs: Long,
    private val backgroundThresholdMs: Long,
    private val frozenSlackMs: Long,
    private val dedupWindowMs: Long,
    private val now: () -> Long,
) {
    // Monotonic timestamps (from `now`); see AndroidLogAnrDetector for why the clock must be monotonic.
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
     * Evaluates one watchdog iteration. All dedup bookkeeping happens here, so the shell only
     * performs side effects.
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
                // The watchdog thread itself was descheduled, so anything measured for the main
                // thread is a freeze artifact: re-baseline rather than fire on the next iteration.
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

        // NEVER_REPORTED must not dedup: shortly after boot the monotonic clock is still small and
        // `now - 0` would look "recent", suppressing the very first block.
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

/** How a watchdog check is interpreted; see [AnrCheckResult] for what each case means. */
internal enum class BlockClassification {
    RESPONSIVE,
    FROZEN_PROCESS,
    FOREGROUND_ANR,
    BACKGROUND_WARNING,
}

/**
 * Frozen-process detection wins first: a watchdog sleep overrunning [checkIntervalMs] by more than
 * [frozenSlackMs] means the process was descheduled, so any measured block is an artifact.
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

/** Compact fingerprint for a main-thread stack: top frame plus the first OneSignal frame. */
internal fun buildBlockFingerprint(stackTrace: Array<StackTraceElement>): String {
    val topFrame = stackTrace.firstOrNull()?.toString() ?: "unknown"
    val oneSignalFrame = stackTrace.firstOrNull { it.className.startsWith("com.onesignal") }?.toString() ?: "none"
    return "top=$topFrame|onesignal=$oneSignalFrame"
}

/** Exception type for a foreground, user-visible ANR. Dashboards key off this exact value. */
internal const val ANR_EXCEPTION_TYPE = "ApplicationNotRespondingException"

/** Exception type for a backgrounded main-thread block, which is never an ANR. */
internal const val BACKGROUND_BLOCK_EXCEPTION_TYPE = "BackgroundMainThreadBlockException"

/**
 * Must stay byte-identical to [Throwable.stackTraceToString]. ANR records come from a live thread
 * rather than a throwable, and consumers parsing `exception.stacktrace` would stop matching ANRs.
 */
internal fun formatJvmStacktrace(
    exceptionType: String,
    exceptionMessage: String,
    stackTrace: Array<StackTraceElement>,
): String {
    // Matches printStackTrace, which terminates every line with the platform separator.
    val lineSeparator = System.lineSeparator()
    return buildString {
        append(exceptionType)
        if (exceptionMessage.isNotEmpty()) {
            append(": ").append(exceptionMessage)
        }
        append(lineSeparator)
        for (frame in stackTrace) {
            append("\tat ").append(frame).append(lineSeparator)
        }
    }
}

/** Builds the fatal ANR record for a foreground block of [unresponsiveDurationMs]. */
internal fun buildAnrCrashData(
    threadName: String,
    stackTrace: Array<StackTraceElement>,
    unresponsiveDurationMs: Long,
): CrashData {
    val message = "Application Not Responding: Main thread blocked for ${unresponsiveDurationMs}ms"
    return CrashData(
        threadName = threadName,
        exceptionType = ANR_EXCEPTION_TYPE,
        exceptionMessage = message,
        stacktrace = formatJvmStacktrace(ANR_EXCEPTION_TYPE, message, stackTrace),
    )
}

/**
 * Builds the non-fatal record for a backgrounded main-thread block, with a
 * [buildBlockFingerprint] summary embedded in the message.
 */
internal fun buildBackgroundBlockCrashData(
    threadName: String,
    stackTrace: Array<StackTraceElement>,
    unresponsiveDurationMs: Long,
): CrashData {
    val message =
        "Background main-thread block for ${unresponsiveDurationMs}ms | ${buildBlockFingerprint(stackTrace)}"
    return CrashData(
        threadName = threadName,
        exceptionType = BACKGROUND_BLOCK_EXCEPTION_TYPE,
        exceptionMessage = message,
        stacktrace = formatJvmStacktrace(BACKGROUND_BLOCK_EXCEPTION_TYPE, message, stackTrace),
    )
}
