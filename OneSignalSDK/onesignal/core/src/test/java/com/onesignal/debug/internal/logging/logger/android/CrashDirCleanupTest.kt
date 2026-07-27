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
