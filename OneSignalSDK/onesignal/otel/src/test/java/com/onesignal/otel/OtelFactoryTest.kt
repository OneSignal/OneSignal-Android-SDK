package com.onesignal.otel

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
        every { mockPlatformProvider.processUptime } returns 100.0
        every { mockPlatformProvider.currentThreadName } returns "main"
        every { mockPlatformProvider.crashStoragePath } returns "/test/path"
        every { mockPlatformProvider.minFileAgeForReadMillis } returns 5000L
        every { mockPlatformProvider.remoteLoggingEnabled } returns true
        every { mockPlatformProvider.appIdForHeaders } returns "test-app-id"
        coEvery { mockPlatformProvider.getInstallId() } returns "test-install-id"
    }

    test("createCrashHandler should return IOtelCrashHandler") {
        val handler = OtelFactory.createCrashHandler(mockPlatformProvider, mockLogger)

        handler.shouldBeInstanceOf<IOtelCrashHandler>()
    }

    test("createCrashHandler should create handler with correct dependencies") {
        val handler = OtelFactory.createCrashHandler(mockPlatformProvider, mockLogger)

        handler shouldNotBe null
        // Handler should be initializable
        handler.initialize()
    }

    test("createCrashUploader should return OtelCrashUploader") {
        val uploader = OtelFactory.createCrashUploader(mockPlatformProvider, mockLogger)

        uploader shouldNotBe null
    }

    test("createCrashUploader should create uploader with correct dependencies") {
        val uploader = OtelFactory.createCrashUploader(mockPlatformProvider, mockLogger)

        uploader shouldNotBe null
    }
})
