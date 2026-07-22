package com.onesignal.debug.internal.crash

import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.IOtelOpenTelemetryCrash
import com.onesignal.otel.crash.IOtelAnrDetector
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import io.mockk.verify

/**
 * Pure-JVM tests for the Android ANR watchdog shell. The Android touch points (main-thread Handler,
 * main-thread stack capture) are injected via [AnrWatchdogPlatform], and the monotonic clock via a
 * fake, so the whole watchdog runs off-device without Robolectric — which also means JaCoCo actually
 * measures this code. Pure decision logic is covered separately in [AnrCheckEvaluatorTest].
 */
class OtelAnrDetectorTest : FunSpec({

    val oneSignalStack = arrayOf(
        StackTraceElement("android.os.MessageQueue", "nativePollOnce", "MessageQueue.java", 1),
        StackTraceElement("com.onesignal.core.Foo", "bar", "Foo.kt", 42),
    )
    val nonOneSignalStack = arrayOf(
        StackTraceElement("android.os.MessageQueue", "nativePollOnce", "MessageQueue.java", 1),
        StackTraceElement("com.example.App", "onCreate", "App.kt", 7),
    )

    class FakeClock(var nowMs: Long = 1_000L) {
        fun advance(ms: Long) { nowMs += ms }
    }

    class FakePlatform(
        var stack: Array<StackTraceElement>,
        private val clock: FakeClock,
        private val runPostsSynchronously: Boolean = true,
    ) : AnrWatchdogPlatform {
        override fun postToMainThread(runnable: Runnable) {
            // Simulate a responsive main thread that immediately runs the heartbeat.
            if (runPostsSynchronously) runnable.run()
        }

        override fun removeFromMainThread(runnable: Runnable) = Unit

        override fun mainThread(): Thread = Thread.currentThread()

        override fun mainThreadStackTrace(): Array<StackTraceElement> = stack

        override fun now(): Long = clock.nowMs
    }

    fun buildDetector(
        clock: FakeClock,
        logger: IOtelLogger,
        platform: FakePlatform,
        inForeground: () -> Boolean = { true },
        checkIntervalMs: Long = 2_000L,
    ): OtelAnrDetector = OtelAnrDetector(
        mockk<IOtelOpenTelemetryCrash>(relaxed = true),
        logger,
        anrThresholdMs = 5_000L,
        checkIntervalMs = checkIntervalMs,
        backgroundThresholdMs = 10_000L,
        isAppInForeground = inForeground,
        platform = platform,
    )

    test("OtelAnrDetector implements IOtelAnrDetector") {
        val clock = FakeClock()
        val detector = buildDetector(clock, mockk(relaxed = true), FakePlatform(oneSignalStack, clock))
        detector.shouldBeInstanceOf<IOtelAnrDetector>()
    }

    // ===== evaluateCheck: decision -> side effect wiring =====

    test("a fresh heartbeat keeps a foreground check responsive (no ANR)") {
        val clock = FakeClock(5_000L)
        val logger = mockk<IOtelLogger>(relaxed = true)
        val detector = buildDetector(clock, logger, FakePlatform(oneSignalStack, clock))

        detector.recordHeartbeat()
        clock.advance(4_000L)
        detector.evaluateCheck(actualSleepMs = 2_000L)

        verify(exactly = 0) { logger.info(match { it.contains("Main thread unresponsive") }) }
    }

    test("a stale heartbeat past the threshold reports and saves a foreground ANR") {
        val clock = FakeClock(1_000L)
        val logger = mockk<IOtelLogger>(relaxed = true)
        val detector = buildDetector(clock, logger, FakePlatform(oneSignalStack, clock))

        detector.recordHeartbeat()
        clock.advance(6_000L)
        detector.evaluateCheck(actualSleepMs = 2_000L)

        verify(exactly = 1) { logger.info(match { it.contains("Main thread unresponsive") && it.contains("foreground") }) }
        verify { logger.info(match { it.contains("ANR report saved successfully") }) }
    }

    test("a foreground ANR whose stack is not OneSignal-related is not reported") {
        val clock = FakeClock(1_000L)
        val logger = mockk<IOtelLogger>(relaxed = true)
        val detector = buildDetector(clock, logger, FakePlatform(nonOneSignalStack, clock))

        detector.recordHeartbeat()
        clock.advance(6_000L)
        detector.evaluateCheck(actualSleepMs = 2_000L)

        verify { logger.debug(match { it.contains("not OneSignal-related") }) }
        verify(exactly = 0) { logger.info(match { it.contains("ANR report saved successfully") }) }
    }

    test("recovering with a heartbeat returns the detector to responsive") {
        val clock = FakeClock(1_000L)
        val logger = mockk<IOtelLogger>(relaxed = true)
        val detector = buildDetector(clock, logger, FakePlatform(oneSignalStack, clock))

        detector.recordHeartbeat()
        clock.advance(6_000L)
        detector.evaluateCheck(actualSleepMs = 2_000L) // fires once

        clock.advance(1_000L)
        detector.recordHeartbeat() // main thread recovers
        detector.evaluateCheck(actualSleepMs = 2_000L) // responsive again

        verify(exactly = 1) { logger.info(match { it.contains("Main thread unresponsive") }) }
    }

    test("a background block past the background threshold records a warning, not an ANR") {
        val clock = FakeClock(1_000L)
        val logger = mockk<IOtelLogger>(relaxed = true)
        val detector = buildDetector(clock, logger, FakePlatform(oneSignalStack, clock), inForeground = { false })

        detector.recordHeartbeat()
        clock.advance(11_000L)
        detector.evaluateCheck(actualSleepMs = 2_000L)

        verify(exactly = 1) { logger.info(match { it.contains("backgrounded") && it.contains("warning") }) }
        verify { logger.info(match { it.contains("Background block warning recorded") }) }
        verify(exactly = 0) { logger.info(match { it.contains("Main thread unresponsive") }) }
    }

    test("a background block below the background threshold stays responsive") {
        val clock = FakeClock(1_000L)
        val logger = mockk<IOtelLogger>(relaxed = true)
        val detector = buildDetector(clock, logger, FakePlatform(oneSignalStack, clock), inForeground = { false })

        detector.recordHeartbeat()
        clock.advance(7_000L) // would be a foreground ANR, but below the 10s background threshold
        detector.evaluateCheck(actualSleepMs = 2_000L)

        verify(exactly = 0) { logger.info(match { it.contains("Main thread unresponsive") }) }
        verify(exactly = 0) { logger.info(match { it.contains("backgrounded") }) }
    }

    test("a background block whose stack is not OneSignal-related is skipped") {
        val clock = FakeClock(1_000L)
        val logger = mockk<IOtelLogger>(relaxed = true)
        val detector = buildDetector(clock, logger, FakePlatform(nonOneSignalStack, clock), inForeground = { false })

        detector.recordHeartbeat()
        clock.advance(11_000L)
        detector.evaluateCheck(actualSleepMs = 2_000L)

        verify { logger.debug(match { it.contains("Background block is not OneSignal-related") }) }
        verify(exactly = 0) { logger.info(match { it.contains("Background block warning recorded") }) }
    }

    test("a watchdog oversleep is reported as a frozen process and resets the baseline") {
        val clock = FakeClock(1_000L)
        val logger = mockk<IOtelLogger>(relaxed = true)
        val detector = buildDetector(clock, logger, FakePlatform(oneSignalStack, clock))

        detector.recordHeartbeat()
        clock.advance(60_000L) // huge apparent block...
        detector.evaluateCheck(actualSleepMs = 30_000L) // ...but our own sleep overran => frozen

        verify(exactly = 1) { logger.debug(match { it.contains("frozen") }) }
        verify(exactly = 0) { logger.info(match { it.contains("Main thread unresponsive") }) }

        detector.evaluateCheck(actualSleepMs = 2_000L)
        verify(exactly = 0) { logger.info(match { it.contains("Main thread unresponsive") }) }
    }

    test("an ongoing block is reported only once within the dedup window") {
        val clock = FakeClock(1_000L)
        val logger = mockk<IOtelLogger>(relaxed = true)
        val detector = buildDetector(clock, logger, FakePlatform(oneSignalStack, clock))

        detector.recordHeartbeat()
        clock.advance(6_000L)
        detector.evaluateCheck(actualSleepMs = 2_000L) // first report

        clock.advance(2_000L) // still blocked, within the 30s dedup window
        detector.evaluateCheck(actualSleepMs = 2_000L) // deduped

        verify(exactly = 1) { logger.info(match { it.contains("Main thread unresponsive") }) }
        verify { logger.debug(match { it.contains("already reported recently") }) }
    }

    test("an unknown app state is treated as foreground so a real ANR is never dropped") {
        val clock = FakeClock(1_000L)
        val logger = mockk<IOtelLogger>(relaxed = true)
        val detector = buildDetector(clock, logger, FakePlatform(oneSignalStack, clock), inForeground = { error("no state") })

        detector.recordHeartbeat()
        clock.advance(6_000L)
        detector.evaluateCheck(actualSleepMs = 2_000L)

        verify { logger.debug(match { it.contains("Could not resolve app state") }) }
        verify(exactly = 1) { logger.info(match { it.contains("Main thread unresponsive") && it.contains("foreground") }) }
    }

    // ===== start / stop lifecycle (real watchdog thread, fake platform + clock) =====

    test("start then stop drives the watchdog loop and logs lifecycle") {
        val clock = FakeClock(1_000L)
        val logger = mockk<IOtelLogger>(relaxed = true)
        // Small interval so the real watchdog thread iterates a few times before we stop it.
        val detector = buildDetector(clock, logger, FakePlatform(oneSignalStack, clock), checkIntervalMs = 20L)

        detector.start()
        Thread.sleep(120L) // let the watchdog run a handful of responsive checks
        detector.stop()

        verify { logger.info(match { it.contains("ANR detection started successfully") }) }
        verify { logger.info(match { it.contains("ANR detection stopped") }) }
    }

    test("start twice warns about already monitoring") {
        val clock = FakeClock()
        val logger = mockk<IOtelLogger>(relaxed = true)
        val detector = buildDetector(clock, logger, FakePlatform(oneSignalStack, clock), checkIntervalMs = 100_000L)

        detector.start()
        detector.start()
        verify { logger.warn(match { it.contains("Already monitoring") }) }

        detector.stop()
    }

    test("stop without start warns about not monitoring") {
        val clock = FakeClock()
        val logger = mockk<IOtelLogger>(relaxed = true)
        val detector = buildDetector(clock, logger, FakePlatform(oneSignalStack, clock))

        detector.stop()
        verify { logger.warn(match { it.contains("Not monitoring") }) }
    }

    // ===== AnrConstants =====

    test("AnrConstants should have reasonable defaults") {
        AnrConstants.DEFAULT_ANR_THRESHOLD_MS shouldBe 5_000L
        AnrConstants.DEFAULT_CHECK_INTERVAL_MS shouldBe 2_000L
        AnrConstants.DEFAULT_BACKGROUND_BLOCK_THRESHOLD_MS shouldBe 10_000L
        // The background threshold must stay above the foreground ANR threshold so backgrounded
        // blocks need to last longer before they are even recorded as a warning.
        (AnrConstants.DEFAULT_BACKGROUND_BLOCK_THRESHOLD_MS > AnrConstants.DEFAULT_ANR_THRESHOLD_MS) shouldBe true
    }
})
