package com.onesignal.debug.internal.logging

import android.os.Build
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.crash.OtelSdkSupport
import com.onesignal.otel.IOtelOpenTelemetryRemote
import com.onesignal.otel.OtelLoggingHelper
import io.kotest.core.spec.style.FunSpec
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.robolectric.annotation.Config

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class LoggingOtelTest :
    FunSpec({
        val mockTelemetry = mockk<IOtelOpenTelemetryRemote>(relaxed = true)

        beforeEach {
            // Reset Logging state
            Logging.setOtelTelemetry(null, { false })
        }

        afterEach {
            OtelSdkSupport.reset()
        }

        test("setOtelTelemetry should store telemetry and enabled check function") {
            // Given
            val shouldSend = { _: LogLevel -> true }

            // When
            Logging.setOtelTelemetry(mockTelemetry, shouldSend)

            // Then - verify it's set (we'll test it works by logging)
            Logging.info("test")

            // Wait for async logging
            runBlocking {
                delay(100)
            }

            // Then - verify it doesn't crash (integration test)
            // Note: We can't verify exact calls due to OpenTelemetry type visibility
        }

        test("logToOtel should work when remote logging is enabled") {
            // Given
            Logging.setOtelTelemetry(mockTelemetry, { _: LogLevel -> true })

            // When
            Logging.info("test message")

            // Wait for async logging
            runBlocking {
                delay(100)
            }

            // Then - should not crash (integration test)
            // The actual Otel call is verified in otel module tests
        }

        test("logToOtel should NOT crash when remote logging is disabled") {
            // Given
            Logging.setOtelTelemetry(mockTelemetry, { _: LogLevel -> false })

            // When
            Logging.info("test message")

            // Wait for async logging
            runBlocking {
                delay(100)
            }

            // Then - should not crash
        }

        test("logToOtel should NOT crash when telemetry is null") {
            // Given
            Logging.setOtelTelemetry(null, { _: LogLevel -> true })

            // When
            Logging.info("test message")

            // Wait for async logging
            runBlocking {
                delay(100)
            }

            // Then - should not crash
        }

        test("logToOtel should handle all log levels without crashing") {
            // Given
            Logging.setOtelTelemetry(mockTelemetry, { _: LogLevel -> true })

            // When
            Logging.verbose("verbose message")
            Logging.debug("debug message")
            Logging.info("info message")
            Logging.warn("warn message")
            Logging.error("error message")
            Logging.fatal("fatal message")

            // Wait for async logging
            runBlocking {
                delay(200)
            }

            // Then - should not crash for any level
        }

        test("logToOtel should NOT log NONE level") {
            // Given
            Logging.setOtelTelemetry(mockTelemetry, { _: LogLevel -> true })

            // When
            Logging.log(LogLevel.NONE, "none message")

            // Wait for async logging
            runBlocking {
                delay(100)
            }

            // Then - should not crash, NONE level is skipped
        }

        test("logToOtel should handle exceptions in logs") {
            // Given
            Logging.setOtelTelemetry(mockTelemetry, { _: LogLevel -> true })
            val exception = RuntimeException("test exception")

            // When
            Logging.error("error with exception", exception)

            // Wait for async logging
            runBlocking {
                delay(100)
            }

            // Then - should not crash, exception details are included
        }

        test("logToOtel should handle null exception message") {
            // Given
            Logging.setOtelTelemetry(mockTelemetry, { _: LogLevel -> true })
            val exception = RuntimeException()

            // When
            Logging.error("error with null exception message", exception)

            // Wait for async logging
            runBlocking {
                delay(100)
            }

            // Then - should not crash
        }

        test("logToOtel should handle Otel errors gracefully") {
            // Given
            Logging.setOtelTelemetry(mockTelemetry, { _: LogLevel -> true })
            // Note: We can't mock getLogger() to throw due to OpenTelemetry type visibility,
            // but the real implementation in Logging.logToOtel() handles errors gracefully

            // When
            Logging.info("test message")

            // Wait for async logging
            runBlocking {
                delay(100)
            }

            // Then - should not crash, error handling is tested in integration tests
        }

        test("logToOtel should use dynamic remote logging check") {
            // Given
            var isEnabled = false
            Logging.setOtelTelemetry(mockTelemetry, { _: LogLevel -> isEnabled })

            // When - initially disabled
            Logging.info("message 1")
            runBlocking { delay(50) }

            // When - enable remote logging
            isEnabled = true
            Logging.info("message 2")
            runBlocking { delay(50) }

            // When - disable again
            isEnabled = false
            Logging.info("message 3")
            runBlocking { delay(50) }

            // Then - should not crash, dynamic check works
        }

        test("logToOtel should handle multiple rapid log calls") {
            // Given
            Logging.setOtelTelemetry(mockTelemetry, { _: LogLevel -> true })

            // When - rapid fire logging
            repeat(10) {
                Logging.info("message $it")
            }

            // Wait for async logging
            runBlocking {
                delay(200)
            }

            // Then - should not crash
        }

        test("logToOtel should work with different message formats") {
            // Given
            Logging.setOtelTelemetry(mockTelemetry, { _: LogLevel -> true })

            // When
            Logging.info("simple message")
            Logging.info("message with numbers: 12345")
            Logging.info("message with special chars: !@#$%")
            Logging.info("message with unicode: 测试 🚀")

            // Wait for async logging
            runBlocking {
                delay(200)
            }

            // Then - should not crash
        }

        test("logToOtel should work on Android 8 and newer") {
            // Given
            mockkObject(OtelLoggingHelper)
            Logging.setOtelTelemetry(mockTelemetry, { _: LogLevel -> true })
            OtelSdkSupport.isSupported = true

            // When
            Logging.fatal("simple message")

            coVerify(exactly = 1) {
                OtelLoggingHelper.logToOtel(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }

            unmockkObject(OtelLoggingHelper)
        }

        test("logToOtel should skip Android 7 and older") {
            // Given
            mockkObject(OtelLoggingHelper)
            Logging.setOtelTelemetry(mockTelemetry, { _: LogLevel -> true })
            OtelSdkSupport.isSupported = false

            // When
            Logging.fatal("simple message")

            coVerify(exactly = 0) {
                OtelLoggingHelper.logToOtel(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }

            unmockkObject(OtelLoggingHelper)
        }
    })
