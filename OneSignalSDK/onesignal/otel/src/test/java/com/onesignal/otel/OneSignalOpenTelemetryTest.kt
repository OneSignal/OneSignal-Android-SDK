package com.onesignal.otel

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.LogRecordBuilder
import kotlinx.coroutines.runBlocking

class OneSignalOpenTelemetryTest : FunSpec({
    val mockPlatformProvider = mockk<IOtelPlatformProvider>(relaxed = true)

    fun setupDefaultMocks() {
        coEvery { mockPlatformProvider.getInstallId() } returns "test-install-id"
        every { mockPlatformProvider.sdkBase } returns "android"
        every { mockPlatformProvider.sdkBaseVersion } returns "5.0.0"
        every { mockPlatformProvider.appPackageId } returns "com.test.app"
        every { mockPlatformProvider.appVersion } returns "1.0.0"
        every { mockPlatformProvider.deviceManufacturer } returns "TestManufacturer"
        every { mockPlatformProvider.deviceModel } returns "TestModel"
        every { mockPlatformProvider.osName } returns "Android"
        every { mockPlatformProvider.osVersion } returns "13"
        every { mockPlatformProvider.osBuildId } returns "TEST123"
        every { mockPlatformProvider.sdkWrapper } returns null
        every { mockPlatformProvider.sdkWrapperVersion } returns null
        every { mockPlatformProvider.appId } returns "test-app-id"
        every { mockPlatformProvider.appIdForHeaders } returns "test-app-id"
        every { mockPlatformProvider.onesignalId } returns "test-onesignal-id"
        every { mockPlatformProvider.pushSubscriptionId } returns "test-subscription-id"
        every { mockPlatformProvider.appState } returns "foreground"
        every { mockPlatformProvider.processUptime } returns 100L
        every { mockPlatformProvider.currentThreadName } returns "main"
        every { mockPlatformProvider.crashStoragePath } returns "/test/path"
        every { mockPlatformProvider.minFileAgeForReadMillis } returns 5000L
        every { mockPlatformProvider.remoteLogLevel } returns "ERROR"
        every { mockPlatformProvider.apiBaseUrl } returns "https://api.onesignal.com"
    }

    beforeEach {
        clearMocks(mockPlatformProvider)
        setupDefaultMocks()
    }

    // ===== Remote Telemetry Tests =====

    test("createRemoteTelemetry should return IOtelOpenTelemetryRemote") {
        val remoteTelemetry = OtelFactory.createRemoteTelemetry(mockPlatformProvider)

        remoteTelemetry.shouldBeInstanceOf<IOtelOpenTelemetryRemote>()
    }

    test("remote telemetry should have logExporter") {
        val remoteTelemetry = OtelFactory.createRemoteTelemetry(mockPlatformProvider)

        remoteTelemetry.logExporter shouldNotBe null
    }

    test("remote telemetry getLogger should return LogRecordBuilder") {
        val remoteTelemetry = OtelFactory.createRemoteTelemetry(mockPlatformProvider)

        runBlocking {
            val logger = remoteTelemetry.getLogger()
            logger.shouldBeInstanceOf<LogRecordBuilder>()
        }
    }

    test("remote telemetry forceFlush should not throw") {
        val remoteTelemetry = OtelFactory.createRemoteTelemetry(mockPlatformProvider)

        runBlocking {
            // Should not throw
            remoteTelemetry.forceFlush()
        }
    }

    // ===== Crash Local Telemetry Tests =====

    test("createCrashLocalTelemetry should return IOtelOpenTelemetryCrash") {
        // Use temp directory for crash storage
        val tempDir = System.getProperty("java.io.tmpdir") + "/otel-test-" + System.currentTimeMillis()
        java.io.File(tempDir).mkdirs()
        every { mockPlatformProvider.crashStoragePath } returns tempDir

        try {
            val crashTelemetry = OtelFactory.createCrashLocalTelemetry(mockPlatformProvider)

            crashTelemetry.shouldBeInstanceOf<IOtelOpenTelemetryCrash>()
        } finally {
            java.io.File(tempDir).deleteRecursively()
        }
    }

    test("crash telemetry getLogger should return LogRecordBuilder") {
        val tempDir = System.getProperty("java.io.tmpdir") + "/otel-test-" + System.currentTimeMillis()
        java.io.File(tempDir).mkdirs()
        every { mockPlatformProvider.crashStoragePath } returns tempDir

        try {
            val crashTelemetry = OtelFactory.createCrashLocalTelemetry(mockPlatformProvider)

            runBlocking {
                val logger = crashTelemetry.getLogger()
                logger.shouldBeInstanceOf<LogRecordBuilder>()
            }
        } finally {
            java.io.File(tempDir).deleteRecursively()
        }
    }

    // ===== LogRecordBuilder Extension Tests =====

    test("setAllAttributes with Map should set all string attributes") {
        val mockBuilder = mockk<LogRecordBuilder>(relaxed = true)
        val attributes = mapOf(
            "key1" to "value1",
            "key2" to "value2"
        )

        mockBuilder.setAllAttributes(attributes)

        io.mockk.verify { mockBuilder.setAttribute("key1", "value1") }
        io.mockk.verify { mockBuilder.setAttribute("key2", "value2") }
    }

    test("setAllAttributes with Attributes should handle different types") {
        val mockBuilder = mockk<LogRecordBuilder>(relaxed = true)
        val attributes = Attributes.builder()
            .put("string.key", "string-value")
            .put("long.key", 123L)
            .put("double.key", 45.67)
            .put("boolean.key", true)
            .build()

        mockBuilder.setAllAttributes(attributes)

        io.mockk.verify { mockBuilder.setAttribute("string.key", "string-value") }
        io.mockk.verify { mockBuilder.setAttribute("long.key", 123L) }
        io.mockk.verify { mockBuilder.setAttribute("double.key", 45.67) }
        io.mockk.verify { mockBuilder.setAttribute("boolean.key", true) }
    }

    // ===== SDK Caching Tests =====

    test("remote telemetry should cache SDK instance") {
        val remoteTelemetry = OtelFactory.createRemoteTelemetry(mockPlatformProvider)

        runBlocking {
            val logger1 = remoteTelemetry.getLogger()
            val logger2 = remoteTelemetry.getLogger()

            // Both calls should succeed (SDK is cached internally)
            logger1 shouldNotBe null
            logger2 shouldNotBe null
        }
    }

    // ===== Integration with Factory Tests =====

    test("factory should create independent instances") {
        val remote1 = OtelFactory.createRemoteTelemetry(mockPlatformProvider)
        val remote2 = OtelFactory.createRemoteTelemetry(mockPlatformProvider)

        remote1 shouldNotBe remote2
    }

    test("factory should work with null optional fields") {
        every { mockPlatformProvider.appId } returns null
        every { mockPlatformProvider.onesignalId } returns null
        every { mockPlatformProvider.pushSubscriptionId } returns null
        every { mockPlatformProvider.sdkWrapper } returns null
        every { mockPlatformProvider.sdkWrapperVersion } returns null

        // Should not throw
        val remoteTelemetry = OtelFactory.createRemoteTelemetry(mockPlatformProvider)
        remoteTelemetry shouldNotBe null
    }
})
