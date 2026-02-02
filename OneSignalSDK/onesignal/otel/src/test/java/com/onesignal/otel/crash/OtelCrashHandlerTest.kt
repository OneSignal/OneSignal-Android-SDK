package com.onesignal.otel.crash

import com.onesignal.otel.IOtelCrashReporter
import com.onesignal.otel.IOtelLogger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify

class OtelCrashHandlerTest : FunSpec({
    val mockCrashReporter = mockk<IOtelCrashReporter>(relaxed = true)
    val mockLogger = mockk<IOtelLogger>(relaxed = true)

    fun createFreshHandler() = OtelCrashHandler(mockCrashReporter, mockLogger)

    beforeEach {
        clearMocks(mockCrashReporter, mockLogger)
    }

    test("initialize should set up uncaught exception handler") {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val crashHandler = createFreshHandler()

        crashHandler.initialize()

        Thread.getDefaultUncaughtExceptionHandler() shouldBe crashHandler
        verify { mockLogger.info("OtelCrashHandler: Setting up uncaught exception handler...") }
        verify { mockLogger.info("OtelCrashHandler: ✅ Successfully initialized and registered as default uncaught exception handler") }

        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    test("initialize should not initialize twice") {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val crashHandler = createFreshHandler()

        crashHandler.initialize()
        crashHandler.initialize()

        verify(exactly = 1) { mockLogger.warn("OtelCrashHandler already initialized, skipping") }

        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    test("uncaughtException should not process non-OneSignal crashes") {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val mockHandler = mockk<Thread.UncaughtExceptionHandler>(relaxed = true)
        Thread.setDefaultUncaughtExceptionHandler(mockHandler)
        val crashHandler = createFreshHandler()
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
        val crashHandler = createFreshHandler()
        crashHandler.initialize()

        val throwable = RuntimeException("OneSignal crash").apply {
            stackTrace = arrayOf(
                StackTraceElement("com.onesignal.SomeClass", "someMethod", "SomeClass.kt", 10)
            )
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
        val crashHandler = createFreshHandler()
        crashHandler.initialize()

        val throwable = RuntimeException("OneSignal crash").apply {
            stackTrace = arrayOf(
                StackTraceElement("com.onesignal.SomeClass", "someMethod", "SomeClass.kt", 10)
            )
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
        val crashHandler = createFreshHandler()
        crashHandler.initialize()

        val throwable = RuntimeException("OneSignal crash").apply {
            stackTrace = arrayOf(
                StackTraceElement("com.onesignal.SomeClass", "someMethod", "SomeClass.kt", 10)
            )
        }
        val thread = Thread.currentThread()

        coEvery { mockCrashReporter.saveCrash(any(), any()) } throws RuntimeException("Reporter failed")

        crashHandler.uncaughtException(thread, throwable)

        verify { mockLogger.error(match { it.contains("Failed to save crash report") }) }
        verify { mockHandler.uncaughtException(thread, throwable) }

        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    // ===== isOneSignalAtFault Tests =====

    test("isOneSignalAtFault should return true for OneSignal stack traces") {
        val stackTrace = arrayOf(
            StackTraceElement("com.onesignal.core.SomeClass", "method", "File.kt", 10)
        )

        isOneSignalAtFault(stackTrace) shouldBe true
    }

    test("isOneSignalAtFault should return false for non-OneSignal stack traces") {
        val stackTrace = arrayOf(
            StackTraceElement("com.example.app.SomeClass", "method", "File.kt", 10)
        )

        isOneSignalAtFault(stackTrace) shouldBe false
    }

    test("isOneSignalAtFault should return false for empty stack traces") {
        val stackTrace = emptyArray<StackTraceElement>()

        isOneSignalAtFault(stackTrace) shouldBe false
    }

    test("isOneSignalAtFault with throwable should check throwable stack trace") {
        val throwable = RuntimeException("test").apply {
            stackTrace = arrayOf(
                StackTraceElement("com.onesignal.SomeClass", "method", "File.kt", 10)
            )
        }

        isOneSignalAtFault(throwable) shouldBe true
    }
})
