package com.onesignal.otel.crash

import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.IOtelPlatformProvider
import com.onesignal.otel.IOtelSdkRemoteTelemetry
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.io.File

class OtelCrashUploaderTest {
    private lateinit var mockRemoteTelemetry: IOtelSdkRemoteTelemetry
    private lateinit var mockPlatformProvider: IOtelPlatformProvider
    private lateinit var mockLogger: IOtelLogger
    private lateinit var mockExporter: LogRecordExporter

    @Before
    fun setUp() {
        mockRemoteTelemetry = mockk(relaxed = true)
        mockPlatformProvider = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockExporter = mockk(relaxed = true)
    }

    private fun createTempDir(): String {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "otel-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        return tempDir.absolutePath
    }

    private fun setupDefaultMocks(
        remoteLogLevel: String? = "ERROR",
        crashStoragePath: String? = null,
        minFileAgeForReadMillis: Long = 2_001L,
    ) {
        val path = crashStoragePath ?: createTempDir()
        every { mockPlatformProvider.remoteLogLevel } returns remoteLogLevel
        every { mockPlatformProvider.crashStoragePath } returns path
        every { mockPlatformProvider.minFileAgeForReadMillis } returns minFileAgeForReadMillis
        every { mockRemoteTelemetry.logExporter } returns mockExporter
        every { mockExporter.export(any()) } returns CompletableResultCode.ofSuccess()
    }

    @Test
    fun `should create uploader with dependencies`() {
        setupDefaultMocks()

        val uploader = OtelCrashUploader(mockRemoteTelemetry, mockPlatformProvider, mockLogger)

        uploader shouldNotBe null
    }

    @Test
    fun `start should return immediately when remote logging is disabled with null level`() {
        setupDefaultMocks(remoteLogLevel = null)

        val uploader = OtelCrashUploader(mockRemoteTelemetry, mockPlatformProvider, mockLogger)

        runBlocking { uploader.start() }

        verify { mockLogger.info("OtelCrashUploader: remote logging disabled (level: null)") }
    }

    @Test
    fun `start should return immediately when remote logging is NONE`() {
        setupDefaultMocks(remoteLogLevel = "NONE")

        val uploader = OtelCrashUploader(mockRemoteTelemetry, mockPlatformProvider, mockLogger)

        runBlocking { uploader.start() }

        verify { mockLogger.info("OtelCrashUploader: remote logging disabled (level: NONE)") }
    }

    @Test
    fun `start should proceed when remote logging is enabled`() {
        setupDefaultMocks(remoteLogLevel = "ERROR")

        val uploader = OtelCrashUploader(mockRemoteTelemetry, mockPlatformProvider, mockLogger)

        runBlocking { uploader.start() }

        verify {
            mockLogger.info(
                match {
                    it.startsWith("OtelCrashUploader: starting path=") &&
                        it.endsWith("minFileAgeMs=2001 level=ERROR")
                },
            )
        }
        verify { mockLogger.info(match { it.contains("disk before-read — no files") }) }
        verify(exactly = 2) {
            mockLogger.info("OtelCrashUploader: pass complete sentBatches=0 stoppedOnFailure=false")
        }
    }

    @Test
    fun `sendCrashReports logs a preview and successful summary`() {
        setupDefaultMocks()
        val record = mockk<LogRecordData>(relaxed = true)
        every { record.severityText } returns "FATAL"
        every { record.body.asString() } returns "example crash"
        every { record.attributes } returns Attributes.builder().put("exception.type", "Example").build()
        val uploader = OtelCrashUploader(mockRemoteTelemetry, mockPlatformProvider, mockLogger)

        uploader.sendCrashReports(listOf(listOf(record)).iterator())

        verify { mockExporter.export(match { it.single() === record }) }
        verify {
            mockLogger.info(
                "OtelCrashUploader: posting batch records=1 " +
                    "preview=[severity=FATAL body=example crash attrs=[exception.type]]",
            )
        }
        verify { mockLogger.info("OtelCrashUploader: batch done failed=false") }
        verify { mockLogger.info("OtelCrashUploader: pass complete sentBatches=1 stoppedOnFailure=false") }
    }

    @Test
    fun `sendCrashReports stops after a failed batch`() {
        setupDefaultMocks()
        every { mockExporter.export(any()) } returns CompletableResultCode.ofFailure()
        val record = mockk<LogRecordData>(relaxed = true)
        every { record.attributes } returns Attributes.empty()
        val uploader = OtelCrashUploader(mockRemoteTelemetry, mockPlatformProvider, mockLogger)

        uploader.sendCrashReports(listOf(listOf(record), listOf(record)).iterator())

        verify(exactly = 1) { mockExporter.export(any()) }
        verify { mockLogger.info("OtelCrashUploader: pass complete sentBatches=0 stoppedOnFailure=true") }
    }

    @Test
    fun `logDiskFiles includes file names and sizes`() {
        val directory = File(createTempDir())
        File(directory, "crash.log").writeText("crash")
        setupDefaultMocks(crashStoragePath = directory.path)
        val uploader = OtelCrashUploader(mockRemoteTelemetry, mockPlatformProvider, mockLogger)

        uploader.logDiskFiles("test")

        verify {
            mockLogger.info(match { it.contains("disk test count=1 [name=crash.log bytes=5]") })
        }
    }

    @Test
    fun `SEND_TIMEOUT_SECONDS should be 30 seconds`() {
        OtelCrashUploader.SEND_TIMEOUT_SECONDS shouldBe 30L
    }
}
