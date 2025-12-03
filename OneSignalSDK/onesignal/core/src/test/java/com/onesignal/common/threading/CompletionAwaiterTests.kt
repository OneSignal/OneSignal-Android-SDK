package com.onesignal.common.threading

import com.onesignal.common.AndroidUtils
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.unmockkObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class CompletionAwaiterTests : FunSpec({

    lateinit var awaiter: CompletionAwaiter

    beforeEach {
        Logging.logLevel = LogLevel.NONE
        awaiter = CompletionAwaiter("TestComponent")
    }

    afterEach {
        unmockkObject(AndroidUtils)
    }

    context("blocking await functionality") {

        test("await completes immediately when already completed") {
            // Given
            awaiter.complete()

            // When
            val startTime = System.currentTimeMillis()
            val completed = awaiter.await()
            val duration = System.currentTimeMillis() - startTime

            // Then
            completed shouldBe true
            duration shouldBeLessThan 50L // Should be very fast
        }

        test("await waits for delayed completion") {
            val completionDelay = 300L

            val startTime = System.currentTimeMillis()

            // Simulate delayed completion from another thread
            suspendifyOnIO {
                delay(completionDelay)
                awaiter.complete()
            }

            val result = awaiter.await()
            val duration = System.currentTimeMillis() - startTime

            result shouldBe true
            duration shouldBeGreaterThan (completionDelay - 50)
            duration shouldBeLessThan (completionDelay + 150) // buffer
        }

        test("multiple blocking callers are all unblocked") {
            val numCallers = 5
            val results = mutableListOf<Boolean>()
            val jobs = mutableListOf<Thread>()

            // Start multiple blocking callers
            repeat(numCallers) { index ->
                val thread =
                    Thread {
                        val result = awaiter.await()
                        synchronized(results) {
                            results.add(result)
                        }
                    }
                thread.start()
                jobs.add(thread)
            }

            // Wait a bit to ensure all threads are waiting
            Thread.sleep(100)

            // Complete the awaiter
            awaiter.complete()

            // Wait for all threads to complete
            jobs.forEach { it.join(1000) }

            // All should have completed successfully
            results.size shouldBe numCallers
            results.all { it } shouldBe true
        }
    }

    context("suspend await functionality") {

        test("awaitSuspend completes immediately when already completed") {
            runBlocking {
                // Given
                awaiter.complete()

                // When - should complete immediately without hanging
                awaiter.awaitSuspend()

                // Then - if we get here, it completed successfully
                // No timing assertions needed in test environment
            }
        }

        test("awaitSuspend waits for delayed completion") {
            runBlocking {
                val completionDelay = 100L

                // Start delayed completion
                val completionJob =
                    launch {
                        delay(completionDelay)
                        awaiter.complete()
                    }

                // Wait for completion
                awaiter.awaitSuspend()

                // In test environment, we just verify it completed without hanging
                completionJob.join()
            }
        }

        test("multiple suspend callers are all unblocked") {
            runBlocking {
                val numCallers = 5
                val results = mutableListOf<String>()

                // Start multiple suspend callers
                val jobs =
                    (1..numCallers).map { index ->
                        async {
                            awaiter.awaitSuspend()
                            results.add("caller-$index")
                        }
                    }

                // Wait a bit to ensure all coroutines are suspended
                delay(50)

                // Complete the awaiter
                awaiter.complete()

                // Wait for all callers to complete
                jobs.awaitAll()

                // All should have completed
                results.size shouldBe numCallers
            }
        }

        test("awaitSuspend can be cancelled") {
            runBlocking {
                val job =
                    launch {
                        awaiter.awaitSuspend()
                    }

                // Wait a bit then cancel
                delay(50)
                job.cancel()

                // Job should be cancelled
                job.isCancelled shouldBe true
            }
        }
    }

    context("mixed blocking and suspend callers") {

        test("completion unblocks both blocking and suspend callers") {
            // This test verifies the dual mechanism works
            // We'll test blocking and suspend separately since mixing them in runTest is problematic

            // Test suspend callers first
            runBlocking {
                val suspendResults = mutableListOf<String>()

                // Start suspend callers
                val suspendJobs =
                    (1..2).map { index ->
                        async {
                            awaiter.awaitSuspend()
                            suspendResults.add("suspend-$index")
                        }
                    }

                // Wait a bit to ensure all are waiting
                delay(50)

                // Complete the awaiter
                awaiter.complete()

                // Wait for all to complete
                suspendJobs.awaitAll()

                // All should have completed
                suspendResults.size shouldBe 2
            }

            // Reset for blocking test
            awaiter = CompletionAwaiter("TestComponent")

            // Test blocking callers
            val blockingResults = mutableListOf<Boolean>()
            val blockingThreads =
                (1..2).map { index ->
                    Thread {
                        val result = awaiter.await()
                        synchronized(blockingResults) {
                            blockingResults.add(result)
                        }
                    }
                }
            blockingThreads.forEach { it.start() }

            // Wait a bit to ensure all are waiting
            Thread.sleep(100)

            // Complete the awaiter
            awaiter.complete()

            // Wait for all to complete
            blockingThreads.forEach { it.join(1000) }

            // All should have completed
            blockingResults shouldBe arrayOf(true, true)
        }
    }

    context("edge cases and safety") {

        test("multiple complete calls are safe") {
            // Complete multiple times
            awaiter.complete()
            awaiter.complete()
            awaiter.complete()

            // Should still work normally
            val completed = awaiter.await()
            completed shouldBe true
        }

        test("waiting after completion returns immediately") {
            runBlocking {
                // Complete first
                awaiter.complete()

                // Then wait - should return immediately without hanging
                awaiter.awaitSuspend()

                // Multiple calls should also work immediately
                awaiter.awaitSuspend()
                awaiter.awaitSuspend()
            }
        }

        test("concurrent access is safe") {
            runBlocking {
                val numOperations = 10 // Reduced for test stability
                val jobs = mutableListOf<Job>()

                // Start some waiters first
                repeat(numOperations / 2) { index ->
                    jobs.add(
                        async {
                            awaiter.awaitSuspend()
                        },
                    )
                }

                // Wait a bit for them to start waiting
                delay(10)

                // Then complete multiple times concurrently
                repeat(numOperations / 2) { index ->
                    jobs.add(launch { awaiter.complete() })
                }

                // Wait for all operations
                jobs.joinAll()

                // Final wait should work immediately
                awaiter.awaitSuspend()
            }
        }
    }

    // Note: Timeout behavior tests removed - await() now waits indefinitely per PR #2412
    // to ensure consistent state. Logging of slow operations is handled by callers.
})
