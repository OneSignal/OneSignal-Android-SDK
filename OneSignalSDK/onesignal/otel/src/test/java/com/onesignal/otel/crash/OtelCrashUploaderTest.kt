package com.onesignal.otel.crash

import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.IOtelOpenTelemetryRemote
import com.onesignal.otel.IOtelPlatformProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import kotlinx.coroutines.runBlocking
import java.io.File

class OtelCrashUploaderTest : FunSpec({
    val mockRemoteTelemetry = mockk<IOtelOpenTelemetryRemote>(relaxed = true)
    val mockPlatformProvider = mockk<IOtelPlatformProvider>(relaxed = true)
    val mockLogger = mockk<IOtelLogger>(relaxed = true)
    val mockExporter = mockk<LogRecordExporter>(relaxed = true)

    // Use temp directory for tests that need file system access
    fun createTempDir(): String {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "otel-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        return tempDir.absolutePath
    }

    fun setupDefaultMocks(
        remoteLogLevel: String? = "ERROR",
        crashStoragePath: String? = null,
        minFileAgeForReadMillis: Long = 0L // Use 0 to avoid delays in tests
    ) {
        val path = crashStoragePath ?: createTempDir()
        every { mockPlatformProvider.remoteLogLevel } returns remoteLogLevel
        every { mockPlatformProvider.crashStoragePath } returns path
        every { mockPlatformProvider.minFileAgeForReadMillis } returns minFileAgeForReadMillis
        every { mockRemoteTelemetry.logExporter } returns mockExporter
        every { mockExporter.export(any()) } returns CompletableResultCode.ofSuccess()
    }

    beforeEach {
        clearMocks(mockRemoteTelemetry, mockPlatformProvider, mockLogger, mockExporter)
    }

    test("should create uploader with dependencies") {
        setupDefaultMocks()

        val uploader = OtelCrashUploader(mockRemoteTelemetry, mockPlatformProvider, mockLogger)

        uploader shouldNotBe null
    }

    test("start should return immediately when remote logging is disabled (null)") {
        setupDefaultMocks(remoteLogLevel = null)

        val uploader = OtelCrashUploader(mockRemoteTelemetry, mockPlatformProvider, mockLogger)

        runBlocking { uploader.start() }

        verify { mockLogger.info("OtelCrashUploader: remote logging disabled (level: null)") }
    }

    test("start should return immediately when remote logging is NONE") {
        setupDefaultMocks(remoteLogLevel = "NONE")

        val uploader = OtelCrashUploader(mockRemoteTelemetry, mockPlatformProvider, mockLogger)

        runBlocking { uploader.start() }

        verify { mockLogger.info("OtelCrashUploader: remote logging disabled (level: NONE)") }
    }

    test("start should proceed when remote logging is enabled") {
        setupDefaultMocks(remoteLogLevel = "ERROR")

        val uploader = OtelCrashUploader(mockRemoteTelemetry, mockPlatformProvider, mockLogger)

        runBlocking { uploader.start() }

        verify { mockLogger.info("OtelCrashUploader: starting") }
    }

    test("SEND_TIMEOUT_SECONDS should be 30 seconds") {
        OtelCrashUploader.SEND_TIMEOUT_SECONDS shouldBe 30L
    }
})
