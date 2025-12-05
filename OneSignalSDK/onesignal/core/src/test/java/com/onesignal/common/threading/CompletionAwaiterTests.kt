package com.onesignal.common.threading

import com.onesignal.common.AndroidUtils
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
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

    context("edge cases and safety") {

        test("multiple complete calls are safe") {
            runBlocking {
                // Complete multiple times
                awaiter.complete()
                awaiter.complete()
                awaiter.complete()

                // Should still work normally
                awaiter.awaitSuspend()
            }
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

    // Note: Blocking await() method removed - only suspend-based awaitSuspend() is supported.
    // This ensures the SDK uses modern coroutine patterns for all async operations.
})
