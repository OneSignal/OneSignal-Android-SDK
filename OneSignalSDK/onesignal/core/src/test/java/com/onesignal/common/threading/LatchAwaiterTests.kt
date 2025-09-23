package com.onesignal.common.threading

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.TestLogLister
import com.onesignal.debug.internal.logging.Logging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LatchAwaiterTests : FunSpec({

    lateinit var awaiter: LatchAwaiter
    lateinit var testLogListener: TestLogLister

    beforeEach {
        testLogListener = TestLogLister()
        Logging.addListener(testLogListener)
        Logging.logLevel = LogLevel.NONE
        awaiter = LatchAwaiter("TestComponent")
    }

    context("successful initialization") {

        test("completes immediately when already successful") {
            // Given
            awaiter.release()

            // When
            val completed = awaiter.await(0)

            // Then
            completed.shouldBeTrue()
        }

        test("awaitOrThrow does not throw if already released") {
            awaiter.release()
            awaiter.awaitOrThrow(100) // should not throw
        }
    }

    context("waiting behavior - holds until completion") {

        test("waits for delayed completion") {
            val completionDelay = 300L
            val timeoutMs = 2000L

            val startTime = System.currentTimeMillis()

            // Simulate delayed success from another thread
            Executors.newSingleThreadScheduledExecutor().schedule({
                awaiter.release()
            }, completionDelay, TimeUnit.MILLISECONDS)

            val result = awaiter.await(timeoutMs)
            val duration = System.currentTimeMillis() - startTime

            result.shouldBeTrue()
            duration shouldBeGreaterThan (completionDelay - 50)
            duration shouldBeLessThan (completionDelay + 150) // buffer
        }
    }

    context("timeout scenarios") {

        test("await returns false when timeout expires") {
            val timeoutMs = 200L
            val startTime = System.currentTimeMillis()

            val completed = awaiter.await(timeoutMs)
            val duration = System.currentTimeMillis() - startTime

            completed.shouldBeFalse()
            duration shouldBeGreaterThan (timeoutMs - 50)
            duration shouldBeLessThan (timeoutMs + 150)
        }

        test("awaitOrThrow throws on timeout") {
            val timeoutMs = 100L
            val startTime = System.currentTimeMillis()

            shouldThrow<IllegalStateException> {
                awaiter.awaitOrThrow(timeoutMs)
            }

            val duration = System.currentTimeMillis() - startTime
            duration shouldBeGreaterThan (timeoutMs - 30)
            duration shouldBeLessThan (timeoutMs + 100)
        }

        test("timeout of 0 returns false immediately") {
            val startTime = System.currentTimeMillis()
            val completed = awaiter.await(0)
            val duration = System.currentTimeMillis() - startTime

            completed.shouldBeFalse()
            duration shouldBeLessThan (20L)
        }

        test("timeout of 0 throws immediately in awaitOrThrow") {
            val startTime = System.currentTimeMillis()
            shouldThrow<IllegalStateException> {
                awaiter.awaitOrThrow(0)
            }
            val duration = System.currentTimeMillis() - startTime
            duration shouldBeLessThan (20L)
        }
    }
})