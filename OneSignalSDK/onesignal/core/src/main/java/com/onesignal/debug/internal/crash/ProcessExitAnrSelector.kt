package com.onesignal.debug.internal.crash

/** One process-exit record, reduced to what the selection decision needs. */
internal data class ProcessExitCandidate(
    val timestampMs: Long,
    val isAnr: Boolean,
)

/** What the reader should do for one pass. */
internal data class ProcessExitSelection(
    val toReport: List<ProcessExitCandidate>,
    /**
     * Advances past every exit seen, not just the reported ones, so a non-ANR exit is not
     * re-examined forever. Null when there was nothing to advance past.
     */
    val newWatermarkMs: Long?,
)

/**
 * Decides which process exits to report. `getHistoricalProcessExitReasons` returns history
 * rather than a queue, so without the watermark every launch re-reports the same ANR forever.
 */
internal class ProcessExitAnrSelector(
    private val dedupWindowMs: Long,
) {
    /**
     * @param exits every exit the OS still retains, any order
     * @param lastProcessedMs watermark from the previous pass; 0 on a fresh install
     * @param lastWatchdogReportMs when the live watchdog last saved an ANR, or null if never
     */
    fun select(
        exits: List<ProcessExitCandidate>,
        lastProcessedMs: Long,
        lastWatchdogReportMs: Long?,
    ): ProcessExitSelection {
        val unseen = exits.filter { it.timestampMs > lastProcessedMs }
        // Off the full list: a pass that reports nothing must still not re-examine these.
        val newWatermark = unseen.maxOfOrNull { it.timestampMs }
        val toReport =
            unseen
                .filter { it.isAnr }
                .filterNot { alreadyReportedByWatchdog(it, lastWatchdogReportMs) }
                .sortedBy { it.timestampMs }
        return ProcessExitSelection(toReport = toReport, newWatermarkMs = newWatermark)
    }

    /**
     * The watchdog may have saved this same hang before the OS killed the process, so its record
     * is already on disk waiting to upload. Double-reporting one ANR is worse than missing it,
     * because it corrupts counts rather than leaving them low.
     */
    private fun alreadyReportedByWatchdog(
        exit: ProcessExitCandidate,
        lastWatchdogReportMs: Long?,
    ): Boolean {
        if (lastWatchdogReportMs == null) return false
        return exit.timestampMs - lastWatchdogReportMs in 0..dedupWindowMs
    }
}
