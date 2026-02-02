package com.onesignal.otel.crash

import com.onesignal.otel.IOtelCrashReporter
import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.IOtelOpenTelemetryCrash
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
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
    val mockLogRecordBuilder = mockk<LogRecordBuilder>(relaxed = true)
    val mockCompletableResult = mockk<CompletableResultCode>(relaxed = true)

    fun setupDefaultMocks() {
        coEvery { mockOpenTelemetry.getLogger() } returns mockLogRecordBuilder
        coEvery { mockOpenTelemetry.forceFlush() } returns mockCompletableResult
        every { mockLogRecordBuilder.setSeverity(any()) } returns mockLogRecordBuilder
        every { mockLogRecordBuilder.setTimestamp(any()) } returns mockLogRecordBuilder
        every { mockLogRecordBuilder.emit() } returns Unit
    }

    beforeEach {
        clearMocks(mockOpenTelemetry, mockLogger, mockLogRecordBuilder, mockCompletableResult)
        setupDefaultMocks()
    }

    test("should implement IOtelCrashReporter interface") {
        val crashReporter = OtelCrashReporter(mockOpenTelemetry, mockLogger)

        crashReporter.shouldBeInstanceOf<IOtelCrashReporter>()
    }

    test("saveCrash should get logger and emit log record") {
        val crashReporter = OtelCrashReporter(mockOpenTelemetry, mockLogger)
        val throwable = RuntimeException("Test crash")
        val thread = Thread.currentThread()

        runBlocking {
            crashReporter.saveCrash(thread, throwable)
        }

        coVerify(exactly = 1) { mockOpenTelemetry.getLogger() }
        coVerify(exactly = 1) { mockOpenTelemetry.forceFlush() }
        verify { mockLogRecordBuilder.setSeverity(Severity.FATAL) }
        verify { mockLogRecordBuilder.emit() }
    }

    test("saveCrash should log info messages") {
        val crashReporter = OtelCrashReporter(mockOpenTelemetry, mockLogger)
        val throwable = RuntimeException("Test crash")
        val thread = Thread.currentThread()

        runBlocking {
            crashReporter.saveCrash(thread, throwable)
        }

        verify { mockLogger.info(match { it.contains("Starting to save crash report") }) }
        verify { mockLogger.info(match { it.contains("Crash report saved and flushed successfully") }) }
    }

    test("saveCrash should handle null exception message") {
        val crashReporter = OtelCrashReporter(mockOpenTelemetry, mockLogger)
        val throwable = RuntimeException() // No message
        val thread = Thread.currentThread()

        runBlocking {
            crashReporter.saveCrash(thread, throwable)
        }

        coVerify { mockOpenTelemetry.getLogger() }
        verify { mockLogRecordBuilder.emit() }
    }

    test("saveCrash should re-throw RuntimeException on failure") {
        coEvery { mockOpenTelemetry.getLogger() } throws RuntimeException("OpenTelemetry failed")

        val crashReporter = OtelCrashReporter(mockOpenTelemetry, mockLogger)
        val throwable = RuntimeException("Test crash")
        val thread = Thread.currentThread()

        shouldThrow<RuntimeException> {
            runBlocking {
                crashReporter.saveCrash(thread, throwable)
            }
        }

        verify { mockLogger.error(match { it.contains("Failed to save crash report") }) }
    }

    test("saveCrash should re-throw IOException on IO failure") {
        coEvery { mockOpenTelemetry.getLogger() } throws java.io.IOException("IO failed")

        val crashReporter = OtelCrashReporter(mockOpenTelemetry, mockLogger)
        val throwable = RuntimeException("Test crash")
        val thread = Thread.currentThread()

        shouldThrow<java.io.IOException> {
            runBlocking {
                crashReporter.saveCrash(thread, throwable)
            }
        }

        verify { mockLogger.error(match { it.contains("IO error saving crash report") }) }
    }

    test("saveCrash should re-throw IllegalStateException") {
        coEvery { mockOpenTelemetry.getLogger() } throws IllegalStateException("Illegal state")

        val crashReporter = OtelCrashReporter(mockOpenTelemetry, mockLogger)
        val throwable = RuntimeException("Test crash")
        val thread = Thread.currentThread()

        shouldThrow<IllegalStateException> {
            runBlocking {
                crashReporter.saveCrash(thread, throwable)
            }
        }

        verify { mockLogger.error(match { it.contains("Illegal state error saving crash report") }) }
    }

    test("saveCrash should set timestamp") {
        val crashReporter = OtelCrashReporter(mockOpenTelemetry, mockLogger)
        val throwable = RuntimeException("Test crash")
        val thread = Thread.currentThread()

        runBlocking {
            crashReporter.saveCrash(thread, throwable)
        }

        verify { mockLogRecordBuilder.setTimestamp(any()) }
    }
})
