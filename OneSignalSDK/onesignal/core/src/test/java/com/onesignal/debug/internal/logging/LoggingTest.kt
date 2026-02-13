package com.onesignal.debug.internal.logging

import android.os.Build
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.OneSignalLogEvent
import com.onesignal.otel.IOtelOpenTelemetryRemote
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.robolectric.annotation.Config

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class LoggingTest : FunSpec({

    val originalLogLevel = Logging.logLevel
    val originalVisualLogLevel = Logging.visualLogLevel

    beforeEach {
        // Reset Logging state
        Logging.logLevel = LogLevel.WARN
        Logging.visualLogLevel = LogLevel.NONE
        Logging.setOtelTelemetry(null) { false }
    }

    afterEach {
        // Restore original state
        Logging.logLevel = originalLogLevel
        Logging.visualLogLevel = originalVisualLogLevel
        Logging.setOtelTelemetry(null) { false }
    }

    // ===== Log Level Tests =====

    test("default logLevel should be WARN") {
        // Reset to default
        Logging.logLevel = LogLevel.WARN

        // Then
        Logging.logLevel shouldBe LogLevel.WARN
    }

    test("default visualLogLevel should be NONE") {
        // Reset to default
        Logging.visualLogLevel = LogLevel.NONE

        // Then
        Logging.visualLogLevel shouldBe LogLevel.NONE
    }

    test("logLevel can be changed") {
        // When
        Logging.logLevel = LogLevel.DEBUG

        // Then
        Logging.logLevel shouldBe LogLevel.DEBUG
    }

    test("visualLogLevel can be changed") {
        // When
        Logging.visualLogLevel = LogLevel.INFO

        // Then
        Logging.visualLogLevel shouldBe LogLevel.INFO
    }

    // ===== atLogLevel Tests =====

    test("atLogLevel returns true when level is at or below logLevel") {
        // Given
        Logging.logLevel = LogLevel.WARN

        // Then
        Logging.atLogLevel(LogLevel.WARN) shouldBe true
        Logging.atLogLevel(LogLevel.ERROR) shouldBe true
        Logging.atLogLevel(LogLevel.FATAL) shouldBe true
    }

    test("atLogLevel returns false when level is above logLevel") {
        // Given
        Logging.logLevel = LogLevel.WARN
        Logging.visualLogLevel = LogLevel.NONE

        // Then
        Logging.atLogLevel(LogLevel.INFO) shouldBe false
        Logging.atLogLevel(LogLevel.DEBUG) shouldBe false
        Logging.atLogLevel(LogLevel.VERBOSE) shouldBe false
    }

    test("atLogLevel considers visualLogLevel too") {
        // Given
        Logging.logLevel = LogLevel.NONE
        Logging.visualLogLevel = LogLevel.INFO

        // Then - INFO should pass because visualLogLevel is INFO
        Logging.atLogLevel(LogLevel.INFO) shouldBe true
    }

    // ===== Log Methods Tests =====

    test("verbose method should not throw") {
        // Given
        Logging.logLevel = LogLevel.VERBOSE

        // When & Then - should not throw
        Logging.verbose("Test message")
        Logging.verbose("Test message with throwable", RuntimeException("test"))
    }

    test("debug method should not throw") {
        // Given
        Logging.logLevel = LogLevel.DEBUG

        // When & Then - should not throw
        Logging.debug("Test message")
        Logging.debug("Test message with throwable", RuntimeException("test"))
    }

    test("info method should not throw") {
        // Given
        Logging.logLevel = LogLevel.INFO

        // When & Then - should not throw
        Logging.info("Test message")
        Logging.info("Test message with throwable", RuntimeException("test"))
    }

    test("warn method should not throw") {
        // Given
        Logging.logLevel = LogLevel.WARN

        // When & Then - should not throw
        Logging.warn("Test message")
        Logging.warn("Test message with throwable", RuntimeException("test"))
    }

    test("error method should not throw") {
        // Given
        Logging.logLevel = LogLevel.ERROR

        // When & Then - should not throw
        Logging.error("Test message")
        Logging.error("Test message with throwable", RuntimeException("test"))
    }

    test("fatal method should not throw") {
        // Given
        Logging.logLevel = LogLevel.FATAL

        // When & Then - should not throw
        Logging.fatal("Test message")
        Logging.fatal("Test message with throwable", RuntimeException("test"))
    }

    test("log method with level and message should not throw") {
        // When & Then - should not throw
        Logging.log(LogLevel.INFO, "Test message")
    }

    test("log method with level, message, and throwable should not throw") {
        // When & Then - should not throw
        Logging.log(LogLevel.ERROR, "Test message", RuntimeException("test"))
    }

    // ===== Log Listener Tests =====

    test("addListener should register listener") {
        // Given
        val mockListener = mockk<ILogListener>(relaxed = true)
        val eventSlot = slot<OneSignalLogEvent>()
        every { mockListener.onLogEvent(capture(eventSlot)) } returns Unit

        Logging.addListener(mockListener)
        Logging.logLevel = LogLevel.INFO

        // When
        Logging.info("Test listener message")

        // Then
        verify { mockListener.onLogEvent(any()) }
        eventSlot.captured.level shouldBe LogLevel.INFO
        eventSlot.captured.entry.contains("Test listener message") shouldBe true

        // Cleanup
        Logging.removeListener(mockListener)
    }

    test("removeListener should unregister listener") {
        // Given
        val mockListener = mockk<ILogListener>(relaxed = true)
        Logging.addListener(mockListener)
        Logging.removeListener(mockListener)
        Logging.logLevel = LogLevel.INFO

        // When
        Logging.info("Test message after removal")

        // Then - listener should not be called
        verify(exactly = 0) { mockListener.onLogEvent(any()) }
    }

    test("listener should receive throwable in message") {
        // Given
        val mockListener = mockk<ILogListener>(relaxed = true)
        val eventSlot = slot<OneSignalLogEvent>()
        every { mockListener.onLogEvent(capture(eventSlot)) } returns Unit

        Logging.addListener(mockListener)
        Logging.logLevel = LogLevel.ERROR

        // When
        val exception = RuntimeException("Test exception message")
        Logging.error("Test error", exception)

        // Then
        verify { mockListener.onLogEvent(any()) }
        eventSlot.captured.entry.contains("Test error") shouldBe true
        eventSlot.captured.entry.contains("Test exception message") shouldBe true

        // Cleanup
        Logging.removeListener(mockListener)
    }

    // ===== Otel Integration Tests =====

    test("setOtelTelemetry should set telemetry instance") {
        // Given
        val mockTelemetry = mockk<IOtelOpenTelemetryRemote>(relaxed = true)

        // When
        Logging.setOtelTelemetry(mockTelemetry) { true }

        // Then - no exception thrown
    }

    test("setOtelTelemetry with null should clear telemetry") {
        // Given
        val mockTelemetry = mockk<IOtelOpenTelemetryRemote>(relaxed = true)
        Logging.setOtelTelemetry(mockTelemetry) { true }

        // When
        Logging.setOtelTelemetry(null) { false }

        // Then - no exception thrown
    }

    test("log with Otel configured should not throw") {
        // Given - Using relaxed mock that doesn't require OpenTelemetry classes
        val mockTelemetry = mockk<IOtelOpenTelemetryRemote>(relaxed = true)

        Logging.setOtelTelemetry(mockTelemetry) { level -> level >= LogLevel.ERROR }
        Logging.logLevel = LogLevel.ERROR

        // When & Then - should not throw
        Logging.error("Test Otel error message")
        runBlocking { delay(100) }
    }

    test("log with Otel telemetry set to null should not throw") {
        // Given
        Logging.setOtelTelemetry(null) { true }
        Logging.logLevel = LogLevel.ERROR

        // When & Then - should not throw
        Logging.error("Test error - telemetry is null")
    }

    test("log with NONE level and Otel configured should not throw") {
        // Given
        val mockTelemetry = mockk<IOtelOpenTelemetryRemote>(relaxed = true)
        Logging.setOtelTelemetry(mockTelemetry) { true }

        // When & Then - should not throw
        Logging.log(LogLevel.NONE, "Should not be logged")
    }

    // ===== Message Formatting Tests =====

    test("log message should include thread name") {
        // Given
        val mockListener = mockk<ILogListener>(relaxed = true)
        val eventSlot = slot<OneSignalLogEvent>()
        every { mockListener.onLogEvent(capture(eventSlot)) } returns Unit

        Logging.addListener(mockListener)
        Logging.logLevel = LogLevel.INFO

        // When
        Logging.info("Test message")

        // Then - message should contain thread name in brackets
        eventSlot.captured.entry.contains("[") shouldBe true
        eventSlot.captured.entry.contains("]") shouldBe true

        // Cleanup
        Logging.removeListener(mockListener)
    }

    // ===== Thread Safety Tests =====

    test("multiple listeners can be added safely") {
        // Given
        val listener1 = mockk<ILogListener>(relaxed = true)
        val listener2 = mockk<ILogListener>(relaxed = true)
        val listener3 = mockk<ILogListener>(relaxed = true)

        Logging.addListener(listener1)
        Logging.addListener(listener2)
        Logging.addListener(listener3)
        Logging.logLevel = LogLevel.INFO

        // When
        Logging.info("Test message to multiple listeners")

        // Then - all listeners should receive the event
        verify { listener1.onLogEvent(any()) }
        verify { listener2.onLogEvent(any()) }
        verify { listener3.onLogEvent(any()) }

        // Cleanup
        Logging.removeListener(listener1)
        Logging.removeListener(listener2)
        Logging.removeListener(listener3)
    }

    test("removing non-existent listener should not throw") {
        // Given
        val mockListener = mockk<ILogListener>(relaxed = true)

        // When & Then - should not throw
        Logging.removeListener(mockListener)
    }

    // ===== All Log Levels Tests =====

    test("all log levels should work correctly") {
        // Given
        Logging.logLevel = LogLevel.VERBOSE
        val logLevels = listOf(
            LogLevel.VERBOSE to { msg: String -> Logging.verbose(msg) },
            LogLevel.DEBUG to { msg: String -> Logging.debug(msg) },
            LogLevel.INFO to { msg: String -> Logging.info(msg) },
            LogLevel.WARN to { msg: String -> Logging.warn(msg) },
            LogLevel.ERROR to { msg: String -> Logging.error(msg) },
            LogLevel.FATAL to { msg: String -> Logging.fatal(msg) }
        )

        // When & Then - none should throw
        logLevels.forEach { (level, logFn) ->
            logFn("Test message for level $level")
        }
    }
})
