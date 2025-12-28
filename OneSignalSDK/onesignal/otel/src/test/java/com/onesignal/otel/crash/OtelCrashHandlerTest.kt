package com.onesignal.otel.crash

import com.onesignal.otel.IOtelCrashReporter
import com.onesignal.otel.IOtelLogger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify

class OtelCrashHandlerTest : FunSpec({
    val mockCrashReporter = mockk<IOtelCrashReporter>(relaxed = true)
    val mockLogger = mockk<IOtelLogger>(relaxed = true)
    val crashHandler = OtelCrashHandler(mockCrashReporter, mockLogger)

    test("initialize should set up uncaught exception handler") {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        crashHandler.initialize()

        Thread.getDefaultUncaughtExceptionHandler() shouldBe crashHandler
        verify { mockLogger.debug("OtelCrashHandler initialized") }

        // Restore original handler
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    test("initialize should not initialize twice") {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        crashHandler.initialize()

        crashHandler.initialize()

        verify(exactly = 1) { mockLogger.debug("OtelCrashHandler initialized") }
        verify(exactly = 1) { mockLogger.warn("OtelCrashHandler already initialized, skipping") }

        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    test("uncaughtException should not process non-OneSignal crashes") {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val mockHandler = mockk<Thread.UncaughtExceptionHandler>(relaxed = true)
        Thread.setDefaultUncaughtExceptionHandler(mockHandler)
        crashHandler.initialize()

        val throwable = RuntimeException("Non-OneSignal crash")
        val thread = Thread.currentThread()

        crashHandler.uncaughtException(thread, throwable)

        coVerify(exactly = 0) { mockCrashReporter.saveCrash(any(), any()) }
        verify { mockHandler.uncaughtException(thread, throwable) }

        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    test("uncaughtException should process OneSignal crashes") {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val mockHandler = mockk<Thread.UncaughtExceptionHandler>(relaxed = true)
        Thread.setDefaultUncaughtExceptionHandler(mockHandler)
        crashHandler.initialize()

        val throwable = RuntimeException("OneSignal crash").apply {
            setStackTrace(arrayOf(
                StackTraceElement("com.onesignal.SomeClass", "someMethod", "SomeClass.kt", 10)
            ))
        }
        val thread = Thread.currentThread()

        coEvery { mockCrashReporter.saveCrash(any(), any()) } returns Unit

        crashHandler.uncaughtException(thread, throwable)

        coVerify(exactly = 1) { mockCrashReporter.saveCrash(thread, throwable) }
        verify { mockHandler.uncaughtException(thread, throwable) }

        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    test("uncaughtException should not process same throwable twice") {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        crashHandler.initialize()

        val throwable = RuntimeException("OneSignal crash").apply {
            setStackTrace(arrayOf(
                StackTraceElement("com.onesignal.SomeClass", "someMethod", "SomeClass.kt", 10)
            ))
        }
        val thread = Thread.currentThread()

        coEvery { mockCrashReporter.saveCrash(any(), any()) } returns Unit

        crashHandler.uncaughtException(thread, throwable)
        crashHandler.uncaughtException(thread, throwable)

        coVerify(exactly = 1) { mockCrashReporter.saveCrash(any(), any()) }

        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    test("uncaughtException should handle crash reporter failures gracefully") {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val mockHandler = mockk<Thread.UncaughtExceptionHandler>(relaxed = true)
        Thread.setDefaultUncaughtExceptionHandler(mockHandler)
        crashHandler.initialize()

        val throwable = RuntimeException("OneSignal crash").apply {
            setStackTrace(arrayOf(
                StackTraceElement("com.onesignal.SomeClass", "someMethod", "SomeClass.kt", 10)
            ))
        }
        val thread = Thread.currentThread()

        coEvery { mockCrashReporter.saveCrash(any(), any()) } throws RuntimeException("Reporter failed")

        crashHandler.uncaughtException(thread, throwable)

        verify { mockLogger.error("Failed to save crash: Reporter failed") }
        verify { mockHandler.uncaughtException(thread, throwable) }

        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }
})
