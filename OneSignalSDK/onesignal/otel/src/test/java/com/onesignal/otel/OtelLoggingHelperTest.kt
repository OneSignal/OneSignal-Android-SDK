package com.onesignal.otel

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Severity
import kotlinx.coroutines.runBlocking

class OtelLoggingHelperTest : FunSpec({
    val mockTelemetry = mockk<IOtelOpenTelemetryRemote>(relaxed = true)
    val mockLogRecordBuilder = mockk<LogRecordBuilder>(relaxed = true)

    beforeEach {
        coEvery { mockTelemetry.getLogger() } returns mockLogRecordBuilder
    }

    test("logToOtel should set correct severity for VERBOSE level") {
        val severitySlot = slot<Severity>()
        coEvery { mockLogRecordBuilder.setSeverity(capture(severitySlot)) } returns mockLogRecordBuilder

        runBlocking {
            OtelLoggingHelper.logToOtel(mockTelemetry, "VERBOSE", "test message")
        }

        severitySlot.captured shouldBe Severity.TRACE
    }

    test("logToOtel should set correct severity for DEBUG level") {
        val severitySlot = slot<Severity>()
        coEvery { mockLogRecordBuilder.setSeverity(capture(severitySlot)) } returns mockLogRecordBuilder

        runBlocking {
            OtelLoggingHelper.logToOtel(mockTelemetry, "DEBUG", "test message")
        }

        severitySlot.captured shouldBe Severity.DEBUG
    }

    test("logToOtel should set correct severity for INFO level") {
        val severitySlot = slot<Severity>()
        coEvery { mockLogRecordBuilder.setSeverity(capture(severitySlot)) } returns mockLogRecordBuilder

        runBlocking {
            OtelLoggingHelper.logToOtel(mockTelemetry, "INFO", "test message")
        }

        severitySlot.captured shouldBe Severity.INFO
    }

    test("logToOtel should set correct severity for WARN level") {
        val severitySlot = slot<Severity>()
        coEvery { mockLogRecordBuilder.setSeverity(capture(severitySlot)) } returns mockLogRecordBuilder

        runBlocking {
            OtelLoggingHelper.logToOtel(mockTelemetry, "WARN", "test message")
        }

        severitySlot.captured shouldBe Severity.WARN
    }

    test("logToOtel should set correct severity for ERROR level") {
        val severitySlot = slot<Severity>()
        coEvery { mockLogRecordBuilder.setSeverity(capture(severitySlot)) } returns mockLogRecordBuilder

        runBlocking {
            OtelLoggingHelper.logToOtel(mockTelemetry, "ERROR", "test message")
        }

        severitySlot.captured shouldBe Severity.ERROR
    }

    test("logToOtel should set correct severity for FATAL level") {
        val severitySlot = slot<Severity>()
        coEvery { mockLogRecordBuilder.setSeverity(capture(severitySlot)) } returns mockLogRecordBuilder

        runBlocking {
            OtelLoggingHelper.logToOtel(mockTelemetry, "FATAL", "test message")
        }

        severitySlot.captured shouldBe Severity.FATAL
    }

    test("logToOtel should default to INFO for unknown level") {
        val severitySlot = slot<Severity>()
        coEvery { mockLogRecordBuilder.setSeverity(capture(severitySlot)) } returns mockLogRecordBuilder

        runBlocking {
            OtelLoggingHelper.logToOtel(mockTelemetry, "UNKNOWN", "test message")
        }

        severitySlot.captured shouldBe Severity.INFO
    }

    test("logToOtel should set body with message") {
        val bodySlot = slot<String>()
        coEvery { mockLogRecordBuilder.setBody(capture(bodySlot)) } returns mockLogRecordBuilder

        runBlocking {
            OtelLoggingHelper.logToOtel(mockTelemetry, "INFO", "my test message")
        }

        bodySlot.captured shouldBe "my test message"
    }

    test("logToOtel should emit the log record") {
        runBlocking {
            OtelLoggingHelper.logToOtel(mockTelemetry, "INFO", "test message")
        }

        coVerify { mockLogRecordBuilder.emit() }
    }

    test("logToOtel should include exception attributes when provided") {
        runBlocking {
            OtelLoggingHelper.logToOtel(
                telemetry = mockTelemetry,
                level = "ERROR",
                message = "error occurred",
                exceptionType = "java.lang.RuntimeException",
                exceptionMessage = "something went wrong",
                exceptionStacktrace = "at com.test.Class.method(Class.kt:10)"
            )
        }

        coVerify { mockTelemetry.getLogger() }
        coVerify { mockLogRecordBuilder.emit() }
    }

    test("logToOtel should handle case-insensitive log levels") {
        val severitySlot = slot<Severity>()
        coEvery { mockLogRecordBuilder.setSeverity(capture(severitySlot)) } returns mockLogRecordBuilder

        runBlocking {
            OtelLoggingHelper.logToOtel(mockTelemetry, "error", "test message")
        }

        severitySlot.captured shouldBe Severity.ERROR
    }
})
