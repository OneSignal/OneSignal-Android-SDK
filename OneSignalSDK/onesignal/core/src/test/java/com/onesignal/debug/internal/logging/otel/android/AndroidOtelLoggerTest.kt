package com.onesignal.debug.internal.logging.otel.android

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.otel.IOtelLogger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf

class AndroidOtelLoggerTest : FunSpec({
    // Save original log level to restore after tests
    val originalLogLevel = Logging.logLevel

    beforeEach {
        // Disable logging during tests to avoid polluting test output
        Logging.logLevel = LogLevel.NONE
    }

    afterEach {
        // Restore original log level
        Logging.logLevel = originalLogLevel
    }

    test("should implement IOtelLogger interface") {
        val logger = AndroidOtelLogger()

        logger.shouldBeInstanceOf<IOtelLogger>()
    }

    test("error should not throw") {
        val logger = AndroidOtelLogger()

        // Should not throw
        logger.error("test error message")
    }

    test("warn should not throw") {
        val logger = AndroidOtelLogger()

        // Should not throw
        logger.warn("test warn message")
    }

    test("info should not throw") {
        val logger = AndroidOtelLogger()

        // Should not throw
        logger.info("test info message")
    }

    test("debug should not throw") {
        val logger = AndroidOtelLogger()

        // Should not throw
        logger.debug("test debug message")
    }

    test("should handle empty messages") {
        val logger = AndroidOtelLogger()

        // Should not throw with empty messages
        logger.error("")
        logger.warn("")
        logger.info("")
        logger.debug("")
    }

    test("should handle messages with special characters") {
        val logger = AndroidOtelLogger()

        // Should not throw with special characters
        logger.error("Error: \n\t special chars: @#$%^&*()")
        logger.info("Unicode: 日本語 中文 한국어")
    }
})
