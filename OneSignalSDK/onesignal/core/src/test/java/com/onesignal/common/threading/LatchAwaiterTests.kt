package com.onesignal.common.threading

import com.onesignal.common.AndroidUtils
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.delay

class LatchAwaiterTests : FunSpec({

    lateinit var awaiter: LatchAwaiter

    beforeEach {
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
            completed shouldBe true
        }
    }

    context("waiting behavior - holds until completion") {

        test("waits for delayed completion") {
            val completionDelay = 300L
            val timeoutMs = 2000L

            val startTime = System.currentTimeMillis()

            // Simulate delayed success from another thread
            suspendifyOnThread {
                delay(completionDelay)
                awaiter.release()
            }

            val result = awaiter.await(timeoutMs)
            val duration = System.currentTimeMillis() - startTime

            result shouldBe true
            duration shouldBeGreaterThan (completionDelay - 50)
            duration shouldBeLessThan (completionDelay + 150) // buffer
        }
    }

    context("timeout scenarios") {

        beforeEach {
            mockkObject(AndroidUtils)
            every { AndroidUtils.isRunningOnMainThread() } returns true
        }

        test("await returns false when timeout expires") {
            val timeoutMs = 200L
            val startTime = System.currentTimeMillis()

            val completed = awaiter.await(timeoutMs)
            val duration = System.currentTimeMillis() - startTime

            completed shouldBe false
            duration shouldBeGreaterThan (timeoutMs - 50)
            duration shouldBeLessThan (timeoutMs + 150)
        }

        test("timeout of 0 returns false immediately") {
            val startTime = System.currentTimeMillis()
            val completed = awaiter.await(0)
            val duration = System.currentTimeMillis() - startTime

            completed shouldBe false
            duration shouldBeLessThan 20L
        }
    }
})
