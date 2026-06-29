package com.onesignal.debug.internal.crash

import android.os.Build
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.IOtelOpenTelemetryCrash
import com.onesignal.otel.IOtelPlatformProvider
import com.onesignal.otel.crash.IOtelAnrDetector
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.robolectric.annotation.Config

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class OtelAnrDetectorTest : FunSpec({

    val mockPlatformProvider = mockk<IOtelPlatformProvider>(relaxed = true)
    val mockLogger = mockk<IOtelLogger>(relaxed = true)
    val mockCrashTelemetry = mockk<IOtelOpenTelemetryCrash>(relaxed = true)

    fun setupDefaultMocks() {
        every { mockPlatformProvider.sdkBase } returns "android"
        every { mockPlatformProvider.sdkBaseVersion } returns "1.0.0"
        every { mockPlatformProvider.appPackageId } returns "com.test.app"
        every { mockPlatformProvider.appVersion } returns "1.0"
        every { mockPlatformProvider.deviceManufacturer } returns "Test"
        every { mockPlatformProvider.deviceModel } returns "TestDevice"
        every { mockPlatformProvider.osName } returns "Android"
        every { mockPlatformProvider.osVersion } returns "10"
        every { mockPlatformProvider.osBuildId } returns "TEST123"
        every { mockPlatformProvider.sdkWrapper } returns null
        every { mockPlatformProvider.sdkWrapperVersion } returns null
        every { mockPlatformProvider.appId } returns "test-app-id"
        every { mockPlatformProvider.onesignalId } returns null
        every { mockPlatformProvider.pushSubscriptionId } returns null
        every { mockPlatformProvider.appState } returns "foreground"
        every { mockPlatformProvider.processUptime } returns 100L
        every { mockPlatformProvider.currentThreadName } returns "main"
        every { mockPlatformProvider.crashStoragePath } returns "/test/path"
        every { mockPlatformProvider.minFileAgeForReadMillis } returns 5000L
        every { mockPlatformProvider.remoteLogLevel } returns "ERROR"
        every { mockPlatformProvider.appIdForHeaders } returns "test-app-id"
        every { mockPlatformProvider.apiBaseUrl } returns "https://api.onesignal.com"
        coEvery { mockPlatformProvider.getInstallId() } returns "test-install-id"
    }

    beforeEach {
        setupDefaultMocks()
    }

    // ===== Factory Function Tests =====

    test("createAnrDetector should return IOtelAnrDetector") {
        // When
        val detector = createAnrDetector(mockPlatformProvider, mockLogger)

        // Then
        detector.shouldBeInstanceOf<IOtelAnrDetector>()
    }

    test("createAnrDetector should create detector with default thresholds") {
        // When
        val detector = createAnrDetector(mockPlatformProvider, mockLogger)

        // Then
        detector shouldNotBe null
    }

    test("createAnrDetector should accept custom thresholds") {
        // When
        val detector = createAnrDetector(
            mockPlatformProvider,
            mockLogger,
            anrThresholdMs = 10_000L,
            checkIntervalMs = 2_000L
        )

        // Then
        detector shouldNotBe null
    }

    test("createAnrDetector should accept custom background threshold") {
        // When
        val detector = createAnrDetector(
            mockPlatformProvider,
            mockLogger,
            anrThresholdMs = 5_000L,
            checkIntervalMs = 2_000L,
            backgroundThresholdMs = 20_000L
        )

        // Then
        detector shouldNotBe null
    }

    // ===== Start/Stop Tests =====

    test("start should log info messages") {
        // Given
        val detector = createAnrDetector(mockPlatformProvider, mockLogger)

        // When
        detector.start()

        // Then
        verify { mockLogger.info(match { it.contains("Starting ANR detection") }) }

        // Cleanup
        detector.stop()
    }

    test("stop should log info messages") {
        // Given
        val detector = createAnrDetector(mockPlatformProvider, mockLogger)
        detector.start()

        // When
        detector.stop()

        // Then
        verify { mockLogger.info(match { it.contains("Stopping ANR detection") }) }
    }

    test("start should warn when already monitoring") {
        // Given
        val detector = createAnrDetector(mockPlatformProvider, mockLogger)
        detector.start()

        // When - start again
        detector.start()

        // Then
        verify { mockLogger.warn(match { it.contains("Already monitoring") }) }

        // Cleanup
        detector.stop()
    }

    test("stop should warn when not monitoring") {
        // Given
        val detector = createAnrDetector(mockPlatformProvider, mockLogger)

        // When - stop without starting
        detector.stop()

        // Then
        verify { mockLogger.warn(match { it.contains("Not monitoring") }) }
    }

    test("start and stop can be called multiple times safely") {
        // Given
        val detector = createAnrDetector(mockPlatformProvider, mockLogger)

        // When
        detector.start()
        detector.stop()
        detector.start()
        detector.stop()

        // Then - no exceptions thrown
    }

    // ===== OtelAnrDetector Internal Tests =====

    test("OtelAnrDetector should implement IOtelAnrDetector") {
        // Given
        val detector = OtelAnrDetector(mockCrashTelemetry, mockLogger)

        // Then
        detector.shouldBeInstanceOf<IOtelAnrDetector>()
    }

    test("OtelAnrDetector should accept custom thresholds") {
        // When
        val detector = OtelAnrDetector(
            mockCrashTelemetry,
            mockLogger,
            anrThresholdMs = 15_000L,
            checkIntervalMs = 3_000L
        )

        // Then
        detector shouldNotBe null
    }

    test("OtelAnrDetector start should initialize watchdog thread") {
        // Given
        val detector = OtelAnrDetector(
            mockCrashTelemetry,
            mockLogger,
            anrThresholdMs = 100_000L, // Very long threshold to prevent actual ANR detection
            checkIntervalMs = 100_000L // Very long interval
        )

        // When
        detector.start()

        // Then
        verify { mockLogger.info(match { it.contains("ANR detection started successfully") }) }

        // Cleanup
        detector.stop()
    }

    test("OtelAnrDetector stop should stop watchdog thread") {
        // Given
        val detector = OtelAnrDetector(
            mockCrashTelemetry,
            mockLogger,
            anrThresholdMs = 100_000L,
            checkIntervalMs = 100_000L
        )
        detector.start()

        // When
        detector.stop()

        // Then
        verify { mockLogger.info(match { it.contains("ANR detection stopped") }) }
    }

    // ===== AnrConstants Tests =====

    test("AnrConstants should have reasonable defaults") {
        // Then
        AnrConstants.DEFAULT_ANR_THRESHOLD_MS shouldBe 5_000L
        AnrConstants.DEFAULT_CHECK_INTERVAL_MS shouldBe 2_000L
        AnrConstants.DEFAULT_BACKGROUND_BLOCK_THRESHOLD_MS shouldBe 10_000L
        // The background threshold must stay above the foreground ANR threshold so backgrounded
        // blocks need to last longer before they are even recorded as a warning.
        (AnrConstants.DEFAULT_BACKGROUND_BLOCK_THRESHOLD_MS > AnrConstants.DEFAULT_ANR_THRESHOLD_MS) shouldBe true
    }

    // ===== classifyBlock Tests =====

    // Defaults mirror AnrConstants: 5s ANR, 2s check interval, 2s frozen slack, 10s background.
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
        classify(timeSinceLastResponseMs = 6_000L, inForeground = true) shouldBe BlockClassification.FOREGROUND_ANR
    }

    test("foreground block within the ANR threshold is responsive") {
        classify(timeSinceLastResponseMs = 4_000L, inForeground = true) shouldBe BlockClassification.RESPONSIVE
    }

    test("background block past the foreground threshold but below the background threshold is responsive") {
        // 7s would be a foreground ANR, but in the background it is below the 10s warning threshold.
        classify(timeSinceLastResponseMs = 7_000L, inForeground = false) shouldBe BlockClassification.RESPONSIVE
    }

    test("background block past the background threshold is a background warning, not an ANR") {
        classify(timeSinceLastResponseMs = 11_000L, inForeground = false) shouldBe BlockClassification.BACKGROUND_WARNING
    }

    test("watchdog oversleeping beyond the frozen slack is treated as a frozen process") {
        // Even a huge measured block is suppressed when our own sleep overran (Doze / freeze).
        classify(
            timeSinceLastResponseMs = 60_000L,
            inForeground = true,
            actualSleepMs = 30_000L,
        ) shouldBe BlockClassification.FROZEN_PROCESS
    }

    test("frozen-process detection wins over a foreground ANR") {
        classify(
            timeSinceLastResponseMs = 60_000L,
            inForeground = false,
            actualSleepMs = 30_000L,
        ) shouldBe BlockClassification.FROZEN_PROCESS
    }

    test("sleep overrun within the slack is not treated as frozen") {
        // 2s interval + 1.5s overrun = within the 2s slack, so a real foreground ANR still reports.
        classify(
            timeSinceLastResponseMs = 6_000L,
            inForeground = true,
            actualSleepMs = 3_500L,
        ) shouldBe BlockClassification.FOREGROUND_ANR
    }
})
