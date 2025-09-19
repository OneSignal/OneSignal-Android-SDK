package com.onesignal.common.threading

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan

class LatchAwaiterTests : FunSpec({
    lateinit var awaiter: LatchAwaiter

    beforeEach {
        awaiter = LatchAwaiter("Test")
    }

    context("successful initialization") {

        test("completes immediately when already successful") {
            // When
            awaiter.completeSuccess()

            // Then: should complete without throwing
            awaiter.waitForCompletion(0)
        }
    }

    context("waiting behavior - holds until completion") {

        test("waits for delayed completion") {
            // Given
            val waitStartTime = System.currentTimeMillis()
            val completionDelay = 300L

            // When
            Thread {
                // complete after a short delay
                Thread.sleep(completionDelay)
                awaiter.completeSuccess()
            }.start()
            awaiter.waitForCompletion(2000)
            val waitDuration = System.currentTimeMillis() - waitStartTime

            // Then: should have waited approximately the delay time
            waitDuration shouldBeGreaterThan (completionDelay - 50)
            waitDuration shouldBeLessThan (completionDelay + 50)
        }
    }

    context("timeout scenarios") {

        test("times out at correct duration") {
            // Given
            val timeoutMs = 200L
            val startTime = System.currentTimeMillis()

            // When
            shouldThrow<IllegalStateException> {
                awaiter.waitForCompletion(timeoutMs)
            }

            // Then
            val duration = System.currentTimeMillis() - startTime
            duration shouldBeGreaterThan (timeoutMs - 50)
            duration shouldBeLessThan (timeoutMs + 50)
        }

        test("timeout of 0 throws immediately") {
            val startTime = System.currentTimeMillis()

            shouldThrow<IllegalStateException> {
                awaiter.waitForCompletion(0)
            }

            val duration = System.currentTimeMillis() - startTime
            duration shouldBeLessThan (20L)
        }
    }
})
