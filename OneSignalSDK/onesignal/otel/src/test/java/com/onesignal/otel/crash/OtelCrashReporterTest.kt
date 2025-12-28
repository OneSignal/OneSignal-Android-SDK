package com.onesignal.otel.crash

import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.IOtelOpenTelemetryCrash
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.sdk.common.CompletableResultCode
import kotlinx.coroutines.runBlocking

class OtelCrashReporterTest : FunSpec({
    val mockOpenTelemetry = mockk<IOtelOpenTelemetryCrash>(relaxed = true)
    val mockLogger = mockk<IOtelLogger>(relaxed = true)
    val crashReporter = OtelCrashReporter(mockOpenTelemetry, mockLogger)

    test("saveCrash should log crash with correct attributes") {
        val mockLogRecordBuilder = mockk<LogRecordBuilder>(relaxed = true)
        val mockCompletableResult = mockk<CompletableResultCode>(relaxed = true)

        coEvery { mockOpenTelemetry.getLogger() } returns mockLogRecordBuilder
        coEvery { mockOpenTelemetry.forceFlush() } returns mockCompletableResult
        every { mockLogRecordBuilder.setAttribute(any<String>(), any<String>()) } returns mockLogRecordBuilder
        every { mockLogRecordBuilder.setAttribute(any<String>(), any<Long>()) } returns mockLogRecordBuilder
        every { mockLogRecordBuilder.setAttribute(any<String>(), any<Double>()) } returns mockLogRecordBuilder
        every { mockLogRecordBuilder.setAttribute(any<String>(), any<Boolean>()) } returns mockLogRecordBuilder
        every { mockLogRecordBuilder.setSeverity(any()) } returns mockLogRecordBuilder
        every { mockLogRecordBuilder.emit() } returns Unit

        val throwable = RuntimeException("Test crash")
        val thread = Thread.currentThread()

        runBlocking {
            crashReporter.saveCrash(thread, throwable)
        }

        coVerify(exactly = 1) { mockOpenTelemetry.getLogger() }
        coVerify(exactly = 1) { mockOpenTelemetry.forceFlush() }
        verify { mockLogRecordBuilder.setAttribute("exception.type", "java.lang.RuntimeException") }
        verify { mockLogRecordBuilder.setAttribute("exception.message", "Test crash") }
        verify { mockLogRecordBuilder.setAttribute("exception.stacktrace", any<String>()) }
        verify { mockLogRecordBuilder.setAttribute("ossdk.exception.thread.name", thread.name) }
        verify { mockLogRecordBuilder.setSeverity(Severity.FATAL) }
        verify { mockLogRecordBuilder.emit() }
    }

    test("saveCrash should handle null exception message") {
        val mockLogRecordBuilder = mockk<LogRecordBuilder>(relaxed = true)
        val mockCompletableResult = mockk<CompletableResultCode>(relaxed = true)

        coEvery { mockOpenTelemetry.getLogger() } returns mockLogRecordBuilder
        coEvery { mockOpenTelemetry.forceFlush() } returns mockCompletableResult
        every { mockLogRecordBuilder.setAttribute(any<String>(), any<String>()) } returns mockLogRecordBuilder
        every { mockLogRecordBuilder.setSeverity(any()) } returns mockLogRecordBuilder
        every { mockLogRecordBuilder.emit() } returns Unit

        val throwable = RuntimeException()
        val thread = Thread.currentThread()

        runBlocking {
            crashReporter.saveCrash(thread, throwable)
        }

        verify { mockLogRecordBuilder.setAttribute("exception.message", "") }
    }

    test("saveCrash should handle failures gracefully") {
        val mockLogRecordBuilder = mockk<LogRecordBuilder>(relaxed = true)

        coEvery { mockOpenTelemetry.getLogger() } throws RuntimeException("OpenTelemetry failed")

        val throwable = RuntimeException("Test crash")
        val thread = Thread.currentThread()

        runBlocking {
            crashReporter.saveCrash(thread, throwable)
        }

        verify { mockLogger.error("Failed to save crash report: OpenTelemetry failed") }
    }
})
