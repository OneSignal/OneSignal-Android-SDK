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
                CrashDirEntry("123-abc.otlp", lastModifiedMs = now - 60_000, lengthBytes = 1L),
                CrashDirEntry("too-young-legacy", lastModifiedMs = now - 100, lengthBytes = 1L),
                CrashDirEntry("stale-legacy", lastModifiedMs = now - 10_000, lengthBytes = 1L),
                CrashDirEntry("stale.tmp", lastModifiedMs = now - 60_000, lengthBytes = 1L),
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
                entries = listOf(CrashDirEntry("123-abc.otlp", lastModifiedMs = now - 60_000, lengthBytes = 1L)),
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
                CrashDirEntry("legacy", lastModifiedMs = now - CRASH_MAX_READ_AGE_MILLIS * 2, lengthBytes = 1L),
            )

        selectExpiredOwnedEntries(entries, nowMs = now).map { it.name } shouldBe listOf("1-a.otlp")
    }

    test("selectExpiredOwnedEntries treats a record at exactly the ceiling as still readable") {
        val entries = listOf(owned("1-a.otlp", ageMs = CRASH_MAX_READ_AGE_MILLIS))

        selectExpiredOwnedEntries(entries, nowMs = now) shouldBe emptyList()
    }

    test("selectExpiredOwnedEntries ignores a plausible backwards clock step") {
        // The clock moved back an hour since the record was written. It is a real, recent,
        // uploadable crash — it just has to wait for the clock to agree it is old.
        val entries = listOf(owned("1-a.otlp", ageMs = -60L * 60 * 1000))

        selectExpiredOwnedEntries(entries, nowMs = now) shouldBe emptyList()
    }

    test("selectExpiredOwnedEntries reclaims a record dated past the window into the future") {
        // Beyond a full retention window ahead of now, no clock correction brings it back: the
        // read gate (now - mtime >= minAge) can never pass, so the record is unreadable for life
        // while still holding a count slot and budget. Expiry is the only thing that removes it.
        val entries = listOf(owned("1-a.otlp", ageMs = -(CRASH_MAX_READ_AGE_MILLIS + 1)))

        selectExpiredOwnedEntries(entries, nowMs = now).map { it.name } shouldBe listOf("1-a.otlp")
    }

    test("selectExpiredOwnedEntries leaves a record exactly one window into the future") {
        // The boundary belongs to the backwards-clock case, matching the past-side ceiling.
        val entries = listOf(owned("1-a.otlp", ageMs = -CRASH_MAX_READ_AGE_MILLIS))

        selectExpiredOwnedEntries(entries, nowMs = now) shouldBe emptyList()
    }

    test("selectExpiredOwnedEntries is empty for an empty directory") {
        selectExpiredOwnedEntries(emptyList(), nowMs = now) shouldBe emptyList()
    }

    // ===== selectOverflowOwnedEntries =====

    test("selectOverflowOwnedEntries returns nothing while within both caps") {
        val entries = (1..3).map { owned("$it-a.otlp", ageMs = it * 1_000L) }

        selectOverflowOwnedEntries(entries, nowMs = now) shouldBe emptyList()
    }

    test("selectOverflowOwnedEntries evicts oldest-first past the count cap") {
        val entries = (1..CRASH_MAX_RECORD_COUNT + 2).map { owned("$it-a.otlp", ageMs = it * 1_000L) }

        val evicted = selectOverflowOwnedEntries(entries, nowMs = now)

        // Oldest has the largest age, so the two highest indices go, returned oldest-first.
        evicted.map { it.name } shouldBe
            listOf("${CRASH_MAX_RECORD_COUNT + 2}-a.otlp", "${CRASH_MAX_RECORD_COUNT + 1}-a.otlp")
    }

    test("selectOverflowOwnedEntries never touches foreign entries") {
        val entries =
            (1..CRASH_MAX_RECORD_COUNT + 1).map { owned("$it-a.otlp", ageMs = it * 1_000L) } +
                CrashDirEntry("legacy", lastModifiedMs = now - 999_000L, lengthBytes = 1L)

        selectOverflowOwnedEntries(entries, nowMs = now).none { it.name == "legacy" } shouldBe true
    }

    test("an oversized record is retained but cannot displace the rest") {
        // Size alone is never grounds for eviction — deleting a captured crash without ever
        // attempting to upload it is worse than keeping it. What size limits is budget claim.
        val entries =
            listOf(
                owned("5-newest.otlp", ageMs = 1_000, bytes = 10),
                owned("4-huge.otlp", ageMs = 2_000, bytes = CRASH_MAX_TOTAL_BYTES * 2),
                owned("3-small.otlp", ageMs = 3_000, bytes = 10),
                owned("2-small.otlp", ageMs = 4_000, bytes = 10),
            )

        selectOverflowOwnedEntries(entries, nowMs = now) shouldBe emptyList()
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

        selectOverflowOwnedEntries(entries, nowMs = now).map { it.name } shouldBe listOf("5-does-not-fit.otlp")
    }

    test("keepName retains the just-written record even when it sorts oldest") {
        // A backwards clock step can make a fresh write look older than its siblings.
        val entries =
            (1..CRASH_MAX_RECORD_COUNT).map { owned("$it-a.otlp", ageMs = it * 1_000L) } +
                owned("fresh-a.otlp", ageMs = 999_000)

        val evicted = selectOverflowOwnedEntries(entries, nowMs = now, keepName = "fresh-a.otlp")

        evicted.none { it.name == "fresh-a.otlp" } shouldBe true
        evicted.map { it.name } shouldBe listOf("${CRASH_MAX_RECORD_COUNT}-a.otlp")
    }

    test("an oversized keepName does not evict the pending backlog") {
        // The regression this guards: charging keepName its full length started the budget
        // over cap, so every sibling failed the remaining-budget check and the entire backlog
        // was deleted — then the uploader dropped the oversized record too. A single-entry
        // directory cannot observe this, which is why the case above did not catch it.
        val backlog = (1..4).map { owned("$it-small.otlp", ageMs = it * 10_000L, bytes = 400_000) }
        val entries = backlog + owned("fresh-a.otlp", ageMs = 1_000, bytes = CRASH_MAX_TOTAL_BYTES * 2)

        val evicted = selectOverflowOwnedEntries(entries, nowMs = now, keepName = "fresh-a.otlp")

        // The oversized record claims only its capped share, leaving room for the backlog.
        evicted.none { it.name == "fresh-a.otlp" } shouldBe true
        evicted.map { it.name } shouldBe listOf("4-small.otlp")
    }

    test("equal timestamps break the tie on the millis embedded in the name") {
        // Coarse filesystem timestamps collapse mtimes; the name preserves write order.
        val entries =
            listOf(
                CrashDirEntry("100-a.otlp", lastModifiedMs = now, lengthBytes = 10),
                CrashDirEntry("300-c.otlp", lastModifiedMs = now, lengthBytes = 10),
                CrashDirEntry("200-b.otlp", lastModifiedMs = now, lengthBytes = 10),
            )

        val evicted = selectOverflowOwnedEntries(entries, nowMs = now, maxCount = 2)

        evicted.map { it.name } shouldBe listOf("100-a.otlp")
    }

    test("a future-dated record is evicted before any record that could still upload") {
        // The write path enforces caps without running expiry first, so ordering has to make this
        // call on its own. Left unranked, the future record sorts newest, keeps its slot forever,
        // and pushes out genuine records that are still uploadable.
        val entries =
            listOf(
                owned("9-zombie.otlp", ageMs = -(CRASH_MAX_READ_AGE_MILLIS + 1)),
                owned("300-a.otlp", ageMs = 1_000),
                owned("200-b.otlp", ageMs = 2_000),
                owned("100-c.otlp", ageMs = 3_000),
            )

        val evicted = selectOverflowOwnedEntries(entries, nowMs = now, maxCount = 2)

        evicted.map { it.name } shouldBe listOf("9-zombie.otlp", "100-c.otlp")
    }

    test("a modestly future-dated record still ranks among the newest") {
        // Only an unrecoverable date is written off. An ordinary backwards clock step leaves a
        // real, recent record that must keep its place ahead of older ones.
        val entries =
            listOf(
                owned("9-clock-skew.otlp", ageMs = -60_000),
                owned("300-a.otlp", ageMs = 1_000),
                owned("100-c.otlp", ageMs = 3_000),
            )

        val evicted = selectOverflowOwnedEntries(entries, nowMs = now, maxCount = 2)

        evicted.map { it.name } shouldBe listOf("100-c.otlp")
    }

    test("selectOverflowOwnedEntries is empty for an empty directory") {
        selectOverflowOwnedEntries(emptyList(), nowMs = now) shouldBe emptyList()
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

    test("formatCrashDirInventory treats a negative sample size as zero") {
        // A logging helper on a crash-adjacent path must not be the thing that throws.
        val line =
            formatCrashDirInventory(
                label = "after-cleanup",
                path = "/cache/crashes",
                entries = listOf(owned("1-a.otlp", ageMs = 1_000)),
                nowMs = now,
                maxSample = -1,
            )

        line shouldContain "total=1 otlp=1 legacy=0"
        line shouldContain "…(+1 more)"
    }
})
