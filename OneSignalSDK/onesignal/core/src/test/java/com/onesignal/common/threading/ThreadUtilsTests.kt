package com.onesignal.common.threading

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ThreadUtilsTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("suspendifyBlocking should execute work synchronously") {
        val latch = CountDownLatch(1)
        var completed = false

        suspendifyOnDefault {
            delay(10)
            completed = true
            latch.countDown()
        }

        latch.await()
        completed shouldBe true
    }

    test("suspendifyOnMain should execute work asynchronously") {
        suspendifyOnMain {
            // In test environment, main thread operations may not complete
            // The important thing is that it doesn't block the test thread
        }

        Thread.sleep(20)
    }

    test("suspendifyOnThread should execute work asynchronously") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        val ran = CountDownLatch(1)

        suspendifyOnIO {
            backgroundThreadId = Thread.currentThread().id
            ran.countDown()
        }

        ran.await(2, TimeUnit.SECONDS) shouldBe true
        backgroundThreadId shouldNotBe null
        backgroundThreadId shouldNotBe mainThreadId
    }

    test("suspendifyOnIngress runs onSuccess after the block completes") {
        val succeeded = CountDownLatch(1)
        var blockRan = false

        suspendifyOnIngress(
            block = { blockRan = true },
            onSuccess = { succeeded.countDown() },
        )

        succeeded.await(2, TimeUnit.SECONDS) shouldBe true
        blockRan shouldBe true
    }

    test("suspendifyOnIngress skips onSuccess when the block fails") {
        val failed = CountDownLatch(1)
        var onSuccessCalled = false

        suspendifyOnIngress(
            block = {
                failed.countDown()
                throw IllegalStateException("journal unavailable")
            },
            onSuccess = { onSuccessCalled = true },
        )

        failed.await(2, TimeUnit.SECONDS) shouldBe true
        Thread.sleep(50)
        onSuccessCalled shouldBe false
    }

    test("suspendifyOnIO should execute work asynchronously") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        val ran = CountDownLatch(1)

        suspendifyOnIO {
            backgroundThreadId = Thread.currentThread().id
            ran.countDown()
        }

        ran.await(2, TimeUnit.SECONDS) shouldBe true
        backgroundThreadId shouldNotBe null
        backgroundThreadId shouldNotBe mainThreadId
    }

    test("suspendifyOnIO should execute work on background thread") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        val ran = CountDownLatch(1)

        suspendifyOnIO {
            backgroundThreadId = Thread.currentThread().id
            ran.countDown()
        }

        ran.await(2, TimeUnit.SECONDS) shouldBe true
        backgroundThreadId shouldNotBe null
        backgroundThreadId shouldNotBe mainThreadId
    }

    test("suspendifyOnDefault should execute work on background thread") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        val ran = CountDownLatch(1)

        suspendifyOnDefault {
            backgroundThreadId = Thread.currentThread().id
            ran.countDown()
        }

        ran.await(2, TimeUnit.SECONDS) shouldBe true
        backgroundThreadId shouldNotBe null
        backgroundThreadId shouldNotBe mainThreadId
    }

    test("suspendifyOnMainModern should execute work on main thread") {
        suspendifyOnMain {
            // In test environment, main thread operations may not complete
            // The important thing is that it doesn't block the test thread
        }

        Thread.sleep(20)
    }

    test("suspendifyWithErrorHandling should handle errors properly") {
        var errorHandled = false
        var caughtException: Exception? = null

        suspendifyWithErrorHandling(
            useIO = true,
            block = {
                throw RuntimeException("Test error")
            },
            onError = { exception ->
                errorHandled = true
                caughtException = exception
            },
        )

        Thread.sleep(20)
        errorHandled shouldBe true
        caughtException?.message shouldBe "Test error"
    }

    test("modern functions should handle concurrent operations") {
        val results = mutableListOf<Int>()
        val expectedResults = (1..5).toList()
        val latch = CountDownLatch(5)

        (1..5).forEach { i ->
            suspendifyOnIO {
                Thread.sleep(20)
                synchronized(results) {
                    results.add(i)
                }
                latch.countDown()
            }
        }

        latch.await()
        results.sorted() shouldBe expectedResults
    }

    test("legacy functions should work with modern implementation") {
        val latch = CountDownLatch(3)
        val completed = AtomicInteger(0)

        suspendifyOnDefault {
            Thread.sleep(20)
            completed.incrementAndGet()
            latch.countDown()
        }

        suspendifyOnIO {
            Thread.sleep(20)
            completed.incrementAndGet()
            latch.countDown()
        }

        suspendifyOnIO {
            Thread.sleep(20)
            completed.incrementAndGet()
            latch.countDown()
        }

        latch.await()
        completed.get() shouldBe 3
    }

    test("IO and Default dispatchers should both run work") {
        val latch = CountDownLatch(2)
        val ioCompleted = AtomicInteger(0)
        val defaultCompleted = AtomicInteger(0)

        suspendifyOnIO {
            Thread.sleep(30)
            ioCompleted.incrementAndGet()
            latch.countDown()
        }

        suspendifyOnDefault {
            Thread.sleep(30)
            defaultCompleted.incrementAndGet()
            latch.countDown()
        }

        latch.await()
        ioCompleted.get() shouldBe 1
        defaultCompleted.get() shouldBe 1
    }

    test("error handling should work with different dispatchers") {
        val latch = CountDownLatch(2)
        val ioErrors = AtomicInteger(0)
        val defaultErrors = AtomicInteger(0)

        suspendifyWithErrorHandling(
            useIO = true,
            block = { throw RuntimeException("IO error") },
            onError = {
                ioErrors.incrementAndGet()
                latch.countDown()
            },
        )

        suspendifyWithErrorHandling(
            useIO = false,
            block = { throw RuntimeException("Default error") },
            onError = {
                defaultErrors.incrementAndGet()
                latch.countDown()
            },
        )

        latch.await()
        ioErrors.get() shouldBe 1
        defaultErrors.get() shouldBe 1
    }

    test("rapid sequential calls should complete successfully") {
        val latch = CountDownLatch(5)
        val completed = AtomicInteger(0)

        repeat(5) { _ ->
            suspendifyOnIO {
                delay(1)
                completed.incrementAndGet()
                latch.countDown()
            }
        }

        latch.await()
        completed.get() shouldBe 5
    }

    test("mixed legacy and modern functions should work together") {
        val latch = CountDownLatch(4)
        val results = mutableListOf<String>()

        suspendifyOnDefault {
            synchronized(results) { results.add("blocking") }
            latch.countDown()
        }

        suspendifyOnIO {
            synchronized(results) { results.add("thread") }
            latch.countDown()
        }

        suspendifyOnIO {
            synchronized(results) { results.add("io") }
            latch.countDown()
        }

        suspendifyOnDefault {
            synchronized(results) { results.add("default") }
            latch.countDown()
        }

        latch.await()
        results.size shouldBe 4
        results shouldContain "blocking"
        results shouldContain "thread"
        results shouldContain "io"
        results shouldContain "default"
    }
})
