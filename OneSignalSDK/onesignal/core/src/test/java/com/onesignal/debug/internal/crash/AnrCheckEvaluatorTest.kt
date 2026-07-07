package com.onesignal.debug.internal.crash

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Pure-JVM tests for the ANR decision core. These run without Robolectric so the logic is exercised
 * on a real JVM (and therefore counted by coverage), unlike the Android shell in [OtelAnrDetector].
 *
 * Defaults mirror AnrConstants: 5s foreground ANR, 2s check interval, 2s frozen slack, 10s background
 * warning, 30s dedup window.
 */
class AnrCheckEvaluatorTest : FunSpec({

    class FakeClock(var nowMs: Long = 1_000L) {
        fun advance(ms: Long) { nowMs += ms }
    }

    fun evaluator(clock: FakeClock) = AnrCheckEvaluator(
        anrThresholdMs = 5_000L,
        checkIntervalMs = 2_000L,
        backgroundThresholdMs = 10_000L,
        frozenSlackMs = 2_000L,
        dedupWindowMs = 30_000L,
        now = { clock.nowMs },
    )

    // ===== classifyBlock (pure) =====

    fun classify(
        timeSinceLastResponseMs: Long,
        inForeground: Boolean,
        actualSleepMs: Long = 2_000L,
    ): BlockClassification = classifyBlock(
        timeSinceLastResponseMs = timeSinceLastResponseMs,
        actualSleepMs = actualSleepMs,
        checkIntervalMs = 2_000L,
        frozenSlackMs = 2_000L,
        anrThresholdMs = 5_000L,
        backgroundThresholdMs = 10_000L,
        inForeground = inForeground,
    )

    test("foreground block past the ANR threshold is a foreground ANR") {
        classify(6_000L, inForeground = true) shouldBe BlockClassification.FOREGROUND_ANR
    }

    test("foreground block within the ANR threshold is responsive") {
        classify(4_000L, inForeground = true) shouldBe BlockClassification.RESPONSIVE
    }

    test("background block below the background threshold is responsive even past the fg threshold") {
        classify(7_000L, inForeground = false) shouldBe BlockClassification.RESPONSIVE
    }

    test("background block past the background threshold is a background warning") {
        classify(11_000L, inForeground = false) shouldBe BlockClassification.BACKGROUND_WARNING
    }

    test("watchdog oversleeping beyond the frozen slack is a frozen process") {
        classify(60_000L, inForeground = true, actualSleepMs = 30_000L) shouldBe BlockClassification.FROZEN_PROCESS
    }

    test("frozen-process detection wins over a background warning") {
        classify(60_000L, inForeground = false, actualSleepMs = 30_000L) shouldBe BlockClassification.FROZEN_PROCESS
    }

    test("sleep overrun within the slack is not treated as frozen") {
        classify(6_000L, inForeground = true, actualSleepMs = 3_500L) shouldBe BlockClassification.FOREGROUND_ANR
    }

    // ===== evaluate: responsive / report paths =====

    test("a fresh heartbeat keeps a foreground check responsive") {
        val clock = FakeClock(5_000L)
        val e = evaluator(clock)

        e.recordHeartbeat()
        clock.advance(4_000L)

        e.evaluate(actualSleepMs = 2_000L, inForeground = true).shouldBeInstanceOf<AnrCheckResult.Responsive>()
    }

    test("a stale heartbeat past the threshold reports a foreground ANR") {
        val clock = FakeClock(1_000L)
        val e = evaluator(clock)

        e.recordHeartbeat()
        clock.advance(6_000L)

        val r = e.evaluate(actualSleepMs = 2_000L, inForeground = true)
        r.shouldBeInstanceOf<AnrCheckResult.ForegroundAnr>()
        r.durationMs shouldBe 6_000L
    }

    test("a background block past the background threshold is a warning, not an ANR") {
        val clock = FakeClock(1_000L)
        val e = evaluator(clock)

        e.recordHeartbeat()
        clock.advance(11_000L)

        e.evaluate(actualSleepMs = 2_000L, inForeground = false).shouldBeInstanceOf<AnrCheckResult.BackgroundWarning>()
    }

    test("a watchdog oversleep is reported as frozen and re-baselines so the next check is responsive") {
        val clock = FakeClock(1_000L)
        val e = evaluator(clock)

        e.recordHeartbeat()
        clock.advance(60_000L)
        e.evaluate(actualSleepMs = 30_000L, inForeground = true).shouldBeInstanceOf<AnrCheckResult.FrozenProcess>()

        // Baseline was reset to now, so a normal on-time check is responsive again.
        e.evaluate(actualSleepMs = 2_000L, inForeground = true).shouldBeInstanceOf<AnrCheckResult.Responsive>()
    }

    // ===== dedup =====

    test("an ongoing foreground block is deduped within the window, then reports again after it") {
        val clock = FakeClock(1_000L)
        val e = evaluator(clock)

        e.recordHeartbeat()
        clock.advance(6_000L)
        e.evaluate(actualSleepMs = 2_000L, inForeground = true).shouldBeInstanceOf<AnrCheckResult.ForegroundAnr>()

        clock.advance(2_000L) // still blocked, within the 30s dedup window
        e.evaluate(actualSleepMs = 2_000L, inForeground = true).shouldBeInstanceOf<AnrCheckResult.Deduped>()

        clock.advance(30_001L) // past the dedup window
        e.evaluate(actualSleepMs = 2_000L, inForeground = true).shouldBeInstanceOf<AnrCheckResult.ForegroundAnr>()
    }

    test("foreground and background dedup are independent — a bg warning never suppresses a fg ANR") {
        val clock = FakeClock(1_000L)
        val e = evaluator(clock)

        // Background warning fires first and sets the background dedup timestamp.
        e.recordHeartbeat()
        clock.advance(11_000L)
        e.evaluate(actualSleepMs = 2_000L, inForeground = false).shouldBeInstanceOf<AnrCheckResult.BackgroundWarning>()

        // Immediately after (well within the dedup window), a foreground block must still report.
        e.evaluate(actualSleepMs = 2_000L, inForeground = true).shouldBeInstanceOf<AnrCheckResult.ForegroundAnr>()
    }

    test("recovering with a heartbeat clears dedup so the next block reports immediately") {
        val clock = FakeClock(1_000L)
        val e = evaluator(clock)

        e.recordHeartbeat()
        clock.advance(6_000L)
        e.evaluate(actualSleepMs = 2_000L, inForeground = true).shouldBeInstanceOf<AnrCheckResult.ForegroundAnr>()

        // Main thread recovers.
        clock.advance(1_000L)
        e.recordHeartbeat()
        e.evaluate(actualSleepMs = 2_000L, inForeground = true).shouldBeInstanceOf<AnrCheckResult.Responsive>()

        // A new block right away (within the old dedup window) still reports because recovery cleared it.
        clock.advance(6_000L)
        e.evaluate(actualSleepMs = 2_000L, inForeground = true).shouldBeInstanceOf<AnrCheckResult.ForegroundAnr>()
    }

    test("the very first block shortly after boot is not suppressed by the sentinel timestamp") {
        // Clock starts small (near boot); `now - NEVER_REPORTED` would look "recent" if dedup didn't
        // special-case the sentinel. It must still report.
        val clock = FakeClock(500L)
        val e = evaluator(clock)

        e.recordHeartbeat()
        clock.advance(6_000L)
        e.evaluate(actualSleepMs = 2_000L, inForeground = true).shouldBeInstanceOf<AnrCheckResult.ForegroundAnr>()
    }

    // ===== fingerprint =====

    test("buildBlockFingerprint surfaces the top frame and the first OneSignal frame") {
        val stack = arrayOf(
            StackTraceElement("android.os.MessageQueue", "nativePollOnce", "MessageQueue.java", 1),
            StackTraceElement("com.onesignal.core.Foo", "bar", "Foo.kt", 42),
            StackTraceElement("com.example.App", "onCreate", "App.kt", 7),
        )

        val fp = buildBlockFingerprint(stack)

        fp shouldBe "top=android.os.MessageQueue.nativePollOnce(MessageQueue.java:1)|" +
            "onesignal=com.onesignal.core.Foo.bar(Foo.kt:42)"
    }

    test("buildBlockFingerprint reports none when no OneSignal frame is present") {
        val stack = arrayOf(
            StackTraceElement("android.os.MessageQueue", "nativePollOnce", "MessageQueue.java", 1),
        )

        buildBlockFingerprint(stack) shouldBe "top=android.os.MessageQueue.nativePollOnce(MessageQueue.java:1)|onesignal=none"
    }

    test("buildBlockFingerprint handles an empty stack") {
        buildBlockFingerprint(emptyArray()) shouldBe "top=unknown|onesignal=none"
    }
})
