package com.onesignal.debug.internal.crash

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val DEDUP_WINDOW_MS = 60_000L

private fun anr(timestampMs: Long) = ProcessExitCandidate(timestampMs = timestampMs, isAnr = true)

private fun other(timestampMs: Long) = ProcessExitCandidate(timestampMs = timestampMs, isAnr = false)

class ProcessExitAnrSelectorTest : FunSpec({
    val selector = ProcessExitAnrSelector(dedupWindowMs = DEDUP_WINDOW_MS)

    test("reports an unseen ANR and advances the watermark past it") {
        val result = selector.select(listOf(anr(5_000)), lastProcessedMs = 0, lastWatchdogReportMs = null)

        result.toReport.map { it.timestampMs } shouldBe listOf(5_000L)
        result.newWatermarkMs shouldBe 5_000L
    }

    test("the same ANR is not reported again on the next launch") {
        val first = selector.select(listOf(anr(5_000)), lastProcessedMs = 0, lastWatchdogReportMs = null)
        // The OS keeps returning it; only the watermark stops the re-report.
        val second =
            selector.select(listOf(anr(5_000)), lastProcessedMs = first.newWatermarkMs!!, lastWatchdogReportMs = null)

        second.toReport shouldBe emptyList()
        second.newWatermarkMs shouldBe null
    }

    test("a non-ANR exit advances the watermark without being reported") {
        // Otherwise every launch re-examines the same user-requested kill forever.
        val result = selector.select(listOf(other(9_000)), lastProcessedMs = 0, lastWatchdogReportMs = null)

        result.toReport shouldBe emptyList()
        result.newWatermarkMs shouldBe 9_000L
    }

    test("the watermark advances past the newest exit even when an older one is reported") {
        val result =
            selector.select(listOf(anr(5_000), other(9_000)), lastProcessedMs = 0, lastWatchdogReportMs = null)

        result.toReport.map { it.timestampMs } shouldBe listOf(5_000L)
        result.newWatermarkMs shouldBe 9_000L
    }

    test("an ANR the watchdog already saved is not reported twice") {
        val result =
            selector.select(listOf(anr(60_000)), lastProcessedMs = 0, lastWatchdogReportMs = 30_000)

        result.toReport shouldBe emptyList()
        // Still consumed: suppressed is not the same as unseen.
        result.newWatermarkMs shouldBe 60_000L
    }

    test("an ANR well after the last watchdog report is still reported") {
        val result =
            selector.select(listOf(anr(200_000)), lastProcessedMs = 0, lastWatchdogReportMs = 30_000)

        result.toReport.map { it.timestampMs } shouldBe listOf(200_000L)
    }

    test("a watchdog report after the exit does not suppress it") {
        // Clock skew or a later session: only a report at or before the death can be about it.
        val result =
            selector.select(listOf(anr(10_000)), lastProcessedMs = 0, lastWatchdogReportMs = 90_000)

        result.toReport.map { it.timestampMs } shouldBe listOf(10_000L)
    }

    test("multiple unseen ANRs are reported oldest first") {
        val result =
            selector.select(
                listOf(anr(30_000), anr(10_000), anr(20_000)),
                lastProcessedMs = 0,
                lastWatchdogReportMs = null,
            )

        result.toReport.map { it.timestampMs } shouldBe listOf(10_000L, 20_000L, 30_000L)
    }

    test("nothing at all leaves the watermark alone") {
        val result = selector.select(emptyList(), lastProcessedMs = 4_000, lastWatchdogReportMs = null)

        result.toReport shouldBe emptyList()
        result.newWatermarkMs shouldBe null
    }
})
