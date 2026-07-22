package com.onesignal.otel.crash

import com.onesignal.otel.IOtelCrashReporter
import com.onesignal.otel.IOtelLogger
import com.onesignal.otel.IOtelOpenTelemetryCrash
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
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

    test("saveCrash should emit at FATAL severity and tag the record fatal") {
        val attrsSlot = slot<Attributes>()
        every { mockLogRecordBuilder.setAllAttributes(capture(attrsSlot)) } returns mockLogRecordBuilder
        val crashReporter = OtelCrashReporter(mockOpenTelemetry, mockLogger)

        runBlocking {
            crashReporter.saveCrash(Thread.currentThread(), RuntimeException("boom"))
        }

        verify { mockLogRecordBuilder.setSeverity(Severity.FATAL) }
        attrsSlot.captured.get(AttributeKey.booleanKey("ossdk.crash.fatal")) shouldBe true
    }

    test("saveNonFatal should emit at WARN severity and tag the record non-fatal") {
        val attrsSlot = slot<Attributes>()
        every { mockLogRecordBuilder.setAllAttributes(capture(attrsSlot)) } returns mockLogRecordBuilder
        val crashReporter = OtelCrashReporter(mockOpenTelemetry, mockLogger)

        runBlocking {
            crashReporter.saveNonFatal(Thread.currentThread(), RuntimeException("background block"))
        }

        // A background block must never ride the fatal severity that feeds the crash/ANR metric.
        verify { mockLogRecordBuilder.setSeverity(Severity.WARN) }
        verify(exactly = 0) { mockLogRecordBuilder.setSeverity(Severity.FATAL) }
        attrsSlot.captured.get(AttributeKey.booleanKey("ossdk.crash.fatal")) shouldBe false
    }

    test("saveNonFatal should still get logger, emit, and flush to the retained crash telemetry") {
        val crashReporter = OtelCrashReporter(mockOpenTelemetry, mockLogger)

        runBlocking {
            crashReporter.saveNonFatal(Thread.currentThread(), RuntimeException("background block"))
        }

        coVerify(exactly = 1) { mockOpenTelemetry.getLogger() }
        coVerify(exactly = 1) { mockOpenTelemetry.forceFlush() }
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

        // Note: IllegalStateException extends RuntimeException, so it gets caught by the RuntimeException handler
        shouldThrow<IllegalStateException> {
            runBlocking {
                crashReporter.saveCrash(thread, throwable)
            }
        }

        verify { mockLogger.error(match { it.contains("Failed to save crash report") }) }
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
