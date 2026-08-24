package com.onesignal.debug.internal.logging.logger.android

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * Pure-JVM coverage for crash-dir ownership / inventory helpers. Runs without Robolectric so Jacoco
 * counts these lines (Roboelectric Android shells are not attributed in this project's reports).
 */
class CrashDirCleanupTest : FunSpec({

    val now = 100_000L

    test("isOwnedCrashFile recognizes only the logger suffix") {
        isOwnedCrashFile("123-abc.otlp") shouldBe true
        isOwnedCrashFile("1784621689841") shouldBe false
        isOwnedCrashFile("stale.tmp") shouldBe false
    }

    test("selectUnrecognizedEntries keeps owned and too-young foreign files") {
        val entries =
            listOf(
                CrashDirEntry("123-abc.otlp", lastModifiedMs = now - 60_000),
                CrashDirEntry("too-young-legacy", lastModifiedMs = now - 100),
                CrashDirEntry("stale-legacy", lastModifiedMs = now - 10_000),
                CrashDirEntry("stale.tmp", lastModifiedMs = now - 60_000),
            )

        val selected =
            selectUnrecognizedEntries(
                entries = entries,
                nowMs = now,
                minAgeMillis = 5_000,
            )

        selected.map { it.name } shouldBe listOf("stale-legacy", "stale.tmp")
    }

    test("selectUnrecognizedEntries is empty when only owned records exist") {
        val selected =
            selectUnrecognizedEntries(
                entries = listOf(CrashDirEntry("123-abc.otlp", lastModifiedMs = now - 60_000)),
                nowMs = now,
                minAgeMillis = 0,
            )

        selected shouldBe emptyList()
    }

    // ===== selectExpiredOwnedEntries =====

    fun owned(name: String, ageMs: Long, bytes: Long = 1L) =
        CrashDirEntry(name, lastModifiedMs = now - ageMs, lengthBytes = bytes)

    test("selectExpiredOwnedEntries takes only owned records strictly past the ceiling") {
        val entries =
            listOf(
                owned("1-a.otlp", ageMs = CRASH_MAX_READ_AGE_MILLIS + 1),
                owned("2-b.otlp", ageMs = CRASH_MAX_READ_AGE_MILLIS - 1),
                CrashDirEntry("legacy", lastModifiedMs = now - CRASH_MAX_READ_AGE_MILLIS * 2),
            )

        selectExpiredOwnedEntries(entries, nowMs = now).map { it.name } shouldBe listOf("1-a.otlp")
    }

    test("selectExpiredOwnedEntries treats a record at exactly the ceiling as still readable") {
        val entries = listOf(owned("1-a.otlp", ageMs = CRASH_MAX_READ_AGE_MILLIS))

        selectExpiredOwnedEntries(entries, nowMs = now) shouldBe emptyList()
    }

    test("selectExpiredOwnedEntries ignores records whose mtime is in the future") {
        // A backwards clock step must not look like extreme age in either direction.
        val entries = listOf(owned("1-a.otlp", ageMs = -CRASH_MAX_READ_AGE_MILLIS * 2))

        selectExpiredOwnedEntries(entries, nowMs = now) shouldBe emptyList()
    }

    test("selectExpiredOwnedEntries is empty for an empty directory") {
        selectExpiredOwnedEntries(emptyList(), nowMs = now) shouldBe emptyList()
    }

    // ===== selectOverflowOwnedEntries =====

    test("selectOverflowOwnedEntries returns nothing while within both caps") {
        val entries = (1..3).map { owned("$it-a.otlp", ageMs = it * 1_000L) }

        selectOverflowOwnedEntries(entries) shouldBe emptyList()
    }

    test("selectOverflowOwnedEntries evicts oldest-first past the count cap") {
        val entries = (1..CRASH_MAX_RECORD_COUNT + 2).map { owned("$it-a.otlp", ageMs = it * 1_000L) }

        val evicted = selectOverflowOwnedEntries(entries)

        // Oldest has the largest age, so the two highest indices go, returned oldest-first.
        evicted.map { it.name } shouldBe
            listOf("${CRASH_MAX_RECORD_COUNT + 2}-a.otlp", "${CRASH_MAX_RECORD_COUNT + 1}-a.otlp")
    }

    test("selectOverflowOwnedEntries never touches foreign entries") {
        val entries =
            (1..CRASH_MAX_RECORD_COUNT + 1).map { owned("$it-a.otlp", ageMs = it * 1_000L) } +
                CrashDirEntry("legacy", lastModifiedMs = now - 999_000L)

        selectOverflowOwnedEntries(entries).none { it.name == "legacy" } shouldBe true
    }

    test("an oversized record is evicted alone and does not displace the rest") {
        // The regression this guards: treating the first over-budget record as a cutoff
        // evicted every older record too, so one bad payload lost the whole backlog.
        val entries =
            listOf(
                owned("5-newest.otlp", ageMs = 1_000, bytes = 10),
                owned("4-huge.otlp", ageMs = 2_000, bytes = CRASH_MAX_RECORD_BYTES + 1),
                owned("3-small.otlp", ageMs = 3_000, bytes = 10),
                owned("2-small.otlp", ageMs = 4_000, bytes = 10),
            )

        selectOverflowOwnedEntries(entries).map { it.name } shouldBe listOf("4-huge.otlp")
    }

    test("a record that does not fit the remaining budget is skipped, not treated as a cutoff") {
        // Four records just under the per-record cap fill most of the budget. The next one
        // cannot fit, but a smaller, *older* one still can — proving the loop skips rather
        // than stopping at the first record that overflows.
        val nearCap = CRASH_MAX_RECORD_BYTES - 12_288
        val entries =
            (1..4).map { owned("${10 - it}-fills.otlp", ageMs = it * 1_000L, bytes = nearCap) } +
                owned("5-does-not-fit.otlp", ageMs = 5_000, bytes = 200_000) +
                owned("4-still-fits.otlp", ageMs = 6_000, bytes = 40_000)

        selectOverflowOwnedEntries(entries).map { it.name } shouldBe listOf("5-does-not-fit.otlp")
    }

    test("keepName retains the just-written record even when it sorts oldest") {
        // A backwards clock step can make a fresh write look older than its siblings.
        val entries =
            (1..CRASH_MAX_RECORD_COUNT).map { owned("$it-a.otlp", ageMs = it * 1_000L) } +
                owned("fresh-a.otlp", ageMs = 999_000)

        val evicted = selectOverflowOwnedEntries(entries, keepName = "fresh-a.otlp")

        evicted.none { it.name == "fresh-a.otlp" } shouldBe true
        evicted.map { it.name } shouldBe listOf("${CRASH_MAX_RECORD_COUNT}-a.otlp")
    }

    test("keepName retains an oversized just-written record") {
        val entries = listOf(owned("fresh-a.otlp", ageMs = 1_000, bytes = CRASH_MAX_RECORD_BYTES + 1))

        selectOverflowOwnedEntries(entries, keepName = "fresh-a.otlp") shouldBe emptyList()
    }

    test("equal timestamps break the tie on the millis embedded in the name") {
        // Coarse filesystem timestamps collapse mtimes; the name preserves write order.
        val entries =
            listOf(
                CrashDirEntry("100-a.otlp", lastModifiedMs = now, lengthBytes = 10),
                CrashDirEntry("300-c.otlp", lastModifiedMs = now, lengthBytes = 10),
                CrashDirEntry("200-b.otlp", lastModifiedMs = now, lengthBytes = 10),
            )

        val evicted = selectOverflowOwnedEntries(entries, maxCount = 2)

        evicted.map { it.name } shouldBe listOf("100-a.otlp")
    }

    test("selectOverflowOwnedEntries is empty for an empty directory") {
        selectOverflowOwnedEntries(emptyList()) shouldBe emptyList()
    }

    test("formatCrashDirInventory reports empty directories") {
        formatCrashDirInventory(
            label = "before-upload",
            path = "/cache/crashes",
            entries = emptyList(),
            nowMs = now,
            maxSample = 20,
        ) shouldBe "OneSignal: Crash storage inventory [before-upload] (/cache/crashes): empty"
    }

    test("formatCrashDirInventory counts otlp vs legacy and bounds the sample") {
        val entries =
            (1..25).map { index ->
                val name = if (index <= 3) "$index.otlp" else "legacy-$index"
                CrashDirEntry(name, lastModifiedMs = now - index * 1_000L, lengthBytes = index.toLong())
            }

        val line =
            formatCrashDirInventory(
                label = "after-cleanup",
                path = "/cache/crashes",
                entries = entries,
                nowMs = now,
                maxSample = 5,
            )

        line shouldContain "total=25 otlp=3 legacy=22"
        line shouldContain "name=1.otlp"
        line shouldContain "…(+20 more)"
        line shouldNotContain "name=legacy-25"
    }
})
