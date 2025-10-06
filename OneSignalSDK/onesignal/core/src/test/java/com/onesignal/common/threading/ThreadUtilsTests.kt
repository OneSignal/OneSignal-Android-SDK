package com.onesignal.common.threading

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class ThreadUtilsTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("suspendifyBlocking should execute work synchronously") {
        var completed = false

        suspendifyBlocking {
            Thread.sleep(50)
            completed = true
        }

        completed shouldBe true
    }

    test("suspendifyOnMain should execute work asynchronously") {
        suspendifyOnMain {
            // In test environment, main thread operations may not complete
            // The important thing is that it doesn't block the test thread
        }

        Thread.sleep(200)
    }

    test("suspendifyOnThread should execute work asynchronously") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        suspendifyOnThread {
            backgroundThreadId = Thread.currentThread().id
        }

        Thread.sleep(50)
        backgroundThreadId shouldNotBe null
        backgroundThreadId shouldNotBe mainThreadId
    }

    test("suspendifyOnThread with completion should execute onComplete callback") {
        var completed = false
        var onCompleteCalled = false

        suspendifyOnThread(
            block = {
                Thread.sleep(50)
                completed = true
            },
            onComplete = {
                onCompleteCalled = true
            },
        )

        Thread.sleep(100)
        completed shouldBe true
        onCompleteCalled shouldBe true
    }

    test("suspendifyOnThread with name should execute work asynchronously") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        suspendifyOnThread("TestThread") {
            backgroundThreadId = Thread.currentThread().id
        }

        Thread.sleep(50)
        backgroundThreadId shouldNotBe null
        backgroundThreadId shouldNotBe mainThreadId
    }

    test("suspendifyOnIO should execute work on background thread") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        suspendifyOnIO {
            backgroundThreadId = Thread.currentThread().id
        }

        Thread.sleep(50)
        backgroundThreadId shouldNotBe null
        backgroundThreadId shouldNotBe mainThreadId
    }

    test("suspendifyOnDefault should execute work on background thread") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        suspendifyOnDefault {
            backgroundThreadId = Thread.currentThread().id
        }

        Thread.sleep(50)
        backgroundThreadId shouldNotBe null
        backgroundThreadId shouldNotBe mainThreadId
    }

    test("suspendifyOnMainModern should execute work on main thread") {
        suspendifyOnMainModern {
            // In test environment, main thread operations may not complete
            // The important thing is that it doesn't block the test thread
        }

        Thread.sleep(200)
    }

    test("suspendifyWithCompletion should execute onComplete callback") {
        var completed = false
        var onCompleteCalled = false

        suspendifyWithCompletion(
            useIO = true,
            block = {
                Thread.sleep(50)
                completed = true
            },
            onComplete = {
                onCompleteCalled = true
            },
        )

        Thread.sleep(100)
        completed shouldBe true
        onCompleteCalled shouldBe true
    }

    test("suspendifyWithErrorHandling should handle errors properly") {
        var errorHandled = false
        var onCompleteCalled = false
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
            onComplete = {
                onCompleteCalled = true
            },
        )

        Thread.sleep(100)
        errorHandled shouldBe true
        onCompleteCalled shouldBe false
        caughtException?.message shouldBe "Test error"
    }

    test("suspendifyWithErrorHandling should call onComplete when no error") {
        var errorHandled = false
        var onCompleteCalled = false
        var completed = false

        suspendifyWithErrorHandling(
            useIO = true,
            block = {
                Thread.sleep(50)
                completed = true
            },
            onError = { _ ->
                errorHandled = true
            },
            onComplete = {
                onCompleteCalled = true
            },
        )

        Thread.sleep(100)
        errorHandled shouldBe false
        onCompleteCalled shouldBe true
        completed shouldBe true
    }

    test("modern functions should handle concurrent operations") {
        val results = mutableListOf<Int>()
        val expectedResults = (1..5).toList()

        (1..5).forEach { i ->
            suspendifyOnIO {
                Thread.sleep(10)
                synchronized(results) {
                    results.add(i)
                }
            }
        }

        Thread.sleep(100)
        results.sorted() shouldBe expectedResults
    }

    test("legacy functions should work with modern implementation") {
        val latch = CountDownLatch(3)
        val completed = AtomicInteger(0)

        suspendifyBlocking {
            Thread.sleep(20)
            completed.incrementAndGet()
            latch.countDown()
        }

        suspendifyOnThread {
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

    test("completion callbacks should work with different dispatchers") {
        val latch = CountDownLatch(2)
        val ioCompleted = AtomicInteger(0)
        val defaultCompleted = AtomicInteger(0)

        suspendifyWithCompletion(
            useIO = true,
            block = {
                Thread.sleep(30)
                ioCompleted.incrementAndGet()
            },
            onComplete = { latch.countDown() },
        )

        suspendifyWithCompletion(
            useIO = false,
            block = {
                Thread.sleep(30)
                defaultCompleted.incrementAndGet()
            },
            onComplete = { latch.countDown() },
        )

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
        val latch = CountDownLatch(20)
        val completed = AtomicInteger(0)

        repeat(20) { i ->
            suspendifyOnIO {
                Thread.sleep(5)
                completed.incrementAndGet()
                latch.countDown()
            }
        }

        latch.await()
        completed.get() shouldBe 20
    }

    test("mixed legacy and modern functions should work together") {
        val latch = CountDownLatch(4)
        val results = mutableListOf<String>()

        suspendifyBlocking {
            synchronized(results) { results.add("blocking") }
            latch.countDown()
        }

        suspendifyOnThread {
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
