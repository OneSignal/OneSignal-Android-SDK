package com.onesignal.otel

import com.onesignal.otel.crash.OtelCrashUploader
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

class OtelFactoryTest : FunSpec({
    val mockPlatformProvider = mockk<IOtelPlatformProvider>(relaxed = true)
    val mockLogger = mockk<IOtelLogger>(relaxed = true)

    beforeEach {
        // Setup default values
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
        every { mockPlatformProvider.appId } returns null
        every { mockPlatformProvider.onesignalId } returns null
        every { mockPlatformProvider.pushSubscriptionId } returns null
        every { mockPlatformProvider.appState } returns "foreground"
        every { mockPlatformProvider.processUptime } returns 100
        every { mockPlatformProvider.currentThreadName } returns "main"
        every { mockPlatformProvider.crashStoragePath } returns "/test/path"
        every { mockPlatformProvider.minFileAgeForReadMillis } returns 5000L
        every { mockPlatformProvider.remoteLogLevel } returns "ERROR"
        every { mockPlatformProvider.appIdForHeaders } returns "test-app-id"
        coEvery { mockPlatformProvider.getInstallId() } returns "test-install-id"
    }

    // ===== createCrashHandler Tests =====

    test("createCrashHandler should return IOtelCrashHandler") {
        // When
        val handler = OtelFactory.createCrashHandler(mockPlatformProvider, mockLogger)

        // Then
        handler.shouldBeInstanceOf<IOtelCrashHandler>()
    }

    test("createCrashHandler should create handler with correct dependencies") {
        // When
        val handler = OtelFactory.createCrashHandler(mockPlatformProvider, mockLogger)

        // Then
        handler shouldNotBe null
        // Handler should be initializable
        handler.initialize()
    }

    test("createCrashHandler should create handler that can be initialized multiple times") {
        // Given
        val handler = OtelFactory.createCrashHandler(mockPlatformProvider, mockLogger)

        // When
        handler.initialize()
        handler.initialize() // Should not throw

        // Then - no exception thrown
    }

    // ===== createCrashUploader Tests =====

    test("createCrashUploader should return OtelCrashUploader") {
        // When
        val uploader = OtelFactory.createCrashUploader(mockPlatformProvider, mockLogger)

        // Then
        uploader shouldNotBe null
        uploader.shouldBeInstanceOf<OtelCrashUploader>()
    }

    test("createCrashUploader should create uploader with correct dependencies") {
        // When
        val uploader = OtelFactory.createCrashUploader(mockPlatformProvider, mockLogger)

        // Then
        uploader shouldNotBe null
    }

    // ===== createRemoteTelemetry Tests =====

    test("createRemoteTelemetry should return IOtelOpenTelemetryRemote") {
        // When
        val telemetry = OtelFactory.createRemoteTelemetry(mockPlatformProvider)

        // Then
        telemetry shouldNotBe null
        telemetry.shouldBeInstanceOf<IOtelOpenTelemetryRemote>()
    }

    test("createRemoteTelemetry should have logExporter") {
        // When
        val telemetry = OtelFactory.createRemoteTelemetry(mockPlatformProvider)

        // Then
        telemetry.logExporter shouldNotBe null
    }

    // ===== createCrashLocalTelemetry Tests =====

    test("createCrashLocalTelemetry should return IOtelOpenTelemetryCrash") {
        // When
        val telemetry = OtelFactory.createCrashLocalTelemetry(mockPlatformProvider)

        // Then
        telemetry shouldNotBe null
        telemetry.shouldBeInstanceOf<IOtelOpenTelemetryCrash>()
    }

    test("createCrashLocalTelemetry should be different instance from remote") {
        // When
        val localTelemetry = OtelFactory.createCrashLocalTelemetry(mockPlatformProvider)
        val remoteTelemetry = OtelFactory.createRemoteTelemetry(mockPlatformProvider)

        // Then
        localTelemetry shouldNotBe remoteTelemetry
    }

    // ===== createCrashReporter Tests =====

    test("createCrashReporter should return IOtelCrashReporter") {
        // Given
        val crashTelemetry = OtelFactory.createCrashLocalTelemetry(mockPlatformProvider)

        // When
        val reporter = OtelFactory.createCrashReporter(crashTelemetry, mockLogger)

        // Then
        reporter shouldNotBe null
        reporter.shouldBeInstanceOf<IOtelCrashReporter>()
    }

    test("createCrashReporter should work with different telemetry instances") {
        // Given
        val crashTelemetry1 = OtelFactory.createCrashLocalTelemetry(mockPlatformProvider)
        val crashTelemetry2 = OtelFactory.createCrashLocalTelemetry(mockPlatformProvider)

        // When
        val reporter1 = OtelFactory.createCrashReporter(crashTelemetry1, mockLogger)
        val reporter2 = OtelFactory.createCrashReporter(crashTelemetry2, mockLogger)

        // Then
        reporter1 shouldNotBe null
        reporter2 shouldNotBe null
        reporter1 shouldNotBe reporter2
    }

    // ===== Integration Tests =====

    test("createCrashHandler uses platform provider values correctly") {
        // Given
        every { mockPlatformProvider.appId } returns "test-app-id"
        every { mockPlatformProvider.onesignalId } returns "test-onesignal-id"

        // When
        val handler = OtelFactory.createCrashHandler(mockPlatformProvider, mockLogger)

        // Then
        handler shouldNotBe null
        handler.initialize() // Should work with provided values
    }

    test("createCrashUploader uses platform provider values correctly") {
        // Given
        every { mockPlatformProvider.appId } returns "test-app-id"
        every { mockPlatformProvider.crashStoragePath } returns "/custom/path"

        // When
        val uploader = OtelFactory.createCrashUploader(mockPlatformProvider, mockLogger)

        // Then
        uploader shouldNotBe null
    }

    test("all factory methods work with null appId") {
        // Given
        every { mockPlatformProvider.appId } returns null

        // When & Then - should not throw
        val handler = OtelFactory.createCrashHandler(mockPlatformProvider, mockLogger)
        handler shouldNotBe null

        val uploader = OtelFactory.createCrashUploader(mockPlatformProvider, mockLogger)
        uploader shouldNotBe null

        val remoteTelemetry = OtelFactory.createRemoteTelemetry(mockPlatformProvider)
        remoteTelemetry shouldNotBe null

        val localTelemetry = OtelFactory.createCrashLocalTelemetry(mockPlatformProvider)
        localTelemetry shouldNotBe null
    }
})
