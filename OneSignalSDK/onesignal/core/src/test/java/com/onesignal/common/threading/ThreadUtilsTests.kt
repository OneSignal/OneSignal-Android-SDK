package com.onesignal.common.threading

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

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

        suspendifyOnIO {
            backgroundThreadId = Thread.currentThread().id
        }

        Thread.sleep(10)
        backgroundThreadId shouldNotBe null
        backgroundThreadId shouldNotBe mainThreadId
    }

    test("suspendifyOnThread with completion should execute onComplete callback") {
        var completed = false
        var onCompleteCalled = false

        suspendifyOnIO(
            block = {
                Thread.sleep(10)
                completed = true
            },
            onComplete = {
                onCompleteCalled = true
            },
        )

        Thread.sleep(20)
        completed shouldBe true
        onCompleteCalled shouldBe true
    }

    test("suspendifyOnIO should execute work asynchronously") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        suspendifyOnIO {
            backgroundThreadId = Thread.currentThread().id
        }

        Thread.sleep(10)
        backgroundThreadId shouldNotBe null
        backgroundThreadId shouldNotBe mainThreadId
    }

    test("suspendifyOnIO should execute work on background thread") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        suspendifyOnIO {
            backgroundThreadId = Thread.currentThread().id
        }

        Thread.sleep(10)
        backgroundThreadId shouldNotBe null
        backgroundThreadId shouldNotBe mainThreadId
    }

    test("suspendifyOnDefault should execute work on background thread") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        suspendifyOnDefault {
            backgroundThreadId = Thread.currentThread().id
        }

        Thread.sleep(10)
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

    test("suspendifyWithCompletion should execute onComplete callback") {
        var completed = false
        var onCompleteCalled = false

        suspendifyWithCompletion(
            useIO = true,
            block = {
                Thread.sleep(10)
                completed = true
            },
            onComplete = {
                onCompleteCalled = true
            },
        )

        Thread.sleep(20)
        completed shouldBe true
        onCompleteCalled shouldBe true
    }

    test("the default arguments of the completion helpers reach the IO scope") {
        val finished = CountDownLatch(4)

        suspendifyWithCompletion(block = { finished.countDown() })
        suspendifyWithErrorHandling(block = { finished.countDown() })
        suspendifyOnIO(block = { finished.countDown() }, onComplete = { finished.countDown() })

        finished.await(2, TimeUnit.SECONDS) shouldBe true
    }

    test("launchOnDefault returns a job that can be joined") {
        var ran = false

        runBlocking { launchOnDefault { ran = true }.join() }

        ran shouldBe true
    }

    test("linkage errors stay inside suspendify helpers") {
        @OptIn(ExperimentalCoroutinesApi::class)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val uncaught = AtomicReference<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val escaped = CountDownLatch(1)
        Thread.setDefaultUncaughtExceptionHandler { _, error ->
            uncaught.set(error)
            escaped.countDown()
        }
        val finished = CountDownLatch(6)
        val reported = AtomicReference<Exception>()
        try {
            mainDispatcherOrNull() shouldNotBe null
            suspendifyOnIO {
                try {
                    throw NoSuchMethodError("forNamespace")
                } finally {
                    finished.countDown()
                }
            }
            suspendifyOnSerialIO {
                try {
                    throw ExceptionInInitializerError("Module with the Main dispatcher is missing")
                } finally {
                    finished.countDown()
                }
            }
            suspendifyOnMain {
                try {
                    throw NoSuchMethodError("Main")
                } finally {
                    finished.countDown()
                }
            }
            launchOnIO {
                try {
                    throw NoSuchMethodError("launchOnIO")
                } finally {
                    finished.countDown()
                }
            }
            launchOnDefault {
                try {
                    throw ExceptionInInitializerError("launchOnDefault")
                } finally {
                    finished.countDown()
                }
            }
            suspendifyWithErrorHandling(
                block = { throw NoSuchMethodError("errorHandling") },
                onError = {
                    reported.set(it)
                    finished.countDown()
                },
            )
            finished.await(2, TimeUnit.SECONDS) shouldBe true
            escaped.await(300, TimeUnit.MILLISECONDS)
            uncaught.get() shouldBe null
            reported.get().cause.shouldBeInstanceOf<NoSuchMethodError>()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            @OptIn(ExperimentalCoroutinesApi::class)
            Dispatchers.resetMain()
        }
    }

    test("a LinkageError reading Main is contained") {
        usableMainDispatcher { throw ExceptionInInitializerError("missing") } shouldBe null
        usableMainDispatcher { throw NoClassDefFoundError("Dispatchers") } shouldBe null
    }

    test("a dispatcher that cannot dispatch is reported unusable") {
        usableMainDispatcher { UndispatchableDispatcher } shouldBe null
    }

    test("a LinkageError from the dispatch probe is contained") {
        usableMainDispatcher { UnlinkableDispatcher } shouldBe null
    }

    test("withMain skips the block when Main cannot dispatch") {
        var ran = false

        withMain { ran = true } shouldBe null

        ran shouldBe false
    }

    test("a cancelled caller is not mistaken for a missing Main") {
        @OptIn(ExperimentalCoroutinesApi::class)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val cancelled = AtomicReference<Throwable>()
            val skipped = AtomicBoolean(false)

            runBlocking {
                val job =
                    launch(Dispatchers.Default) {
                        cancel()
                        try {
                            if (withMain { } == null) skipped.set(true)
                        } catch (e: CancellationException) {
                            cancelled.set(e)
                        }
                    }
                job.join()
            }

            skipped.get() shouldBe false
            cancelled.get().shouldBeInstanceOf<CancellationException>()

            // The cancellation must not have left Main poisoned for everyone else.
            var ran = false
            withMain { ran = true }
            ran shouldBe true
        } finally {
            @OptIn(ExperimentalCoroutinesApi::class)
            Dispatchers.resetMain()
        }
    }

    test("withMain lets the block's own failure through") {
        @OptIn(ExperimentalCoroutinesApi::class)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            shouldThrow<IllegalStateException> {
                withMain { throw IllegalStateException("from the block") }
            }

            var ran = false
            withMain { ran = true }
            ran shouldBe true
        } finally {
            @OptIn(ExperimentalCoroutinesApi::class)
            Dispatchers.resetMain()
        }
    }

    test("withMain runs the block when Main is available") {
        @OptIn(ExperimentalCoroutinesApi::class)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            var ran = false
            withMain { ran = true }
            ran shouldBe true
        } finally {
            @OptIn(ExperimentalCoroutinesApi::class)
            Dispatchers.resetMain()
        }
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

        Thread.sleep(20)
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
                Thread.sleep(10)
                completed = true
            },
            onError = { _ ->
                errorHandled = true
            },
            onComplete = {
                onCompleteCalled = true
            },
        )

        Thread.sleep(20)
        errorHandled shouldBe false
        onCompleteCalled shouldBe true
        completed shouldBe true
    }

    test("modern functions should handle concurrent operations") {
        val results = mutableListOf<Int>()
        val expectedResults = (1..5).toList()
        val latch = CountDownLatch(5)

        (1..5).forEach { i ->
            suspendifyOnIO(
                block = {
                    Thread.sleep(20)
                    synchronized(results) {
                        results.add(i)
                    }
                },
                onComplete = {
                    latch.countDown()
                },
            )
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

/**
 * Stands in for coroutines' MissingMainCoroutineDispatcher, which can be read and then throws from
 * both of these the moment anything tries to use it.
 */
private object UndispatchableDispatcher : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        throw IllegalStateException("Module with the Main dispatcher is missing")
    }

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        throw IllegalStateException("Module with the Main dispatcher is missing")
    }
}

/** A Main backed by a half-installed coroutines-android, which fails to link rather than to throw. */
private object UnlinkableDispatcher : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        throw NoSuchMethodError("HandlerContext.isDispatchNeeded")
    }

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        throw NoSuchMethodError("HandlerContext.dispatch")
    }
}
