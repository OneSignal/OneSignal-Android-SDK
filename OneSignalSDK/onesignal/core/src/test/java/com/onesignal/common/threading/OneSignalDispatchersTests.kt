package com.onesignal.common.threading

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class OneSignalDispatchersTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    beforeTest {
        OneSignalDispatchers.bootstrapEnabledForTest = true
        OneSignalDispatchers.resetForTest()
    }

    afterTest {
        OneSignalDispatchers.resetBootstrapInputsForTest()
        OneSignalDispatchers.resetForTest()
    }

    test("prewarm bootstraps the ingress lane before the other lanes") {
        OneSignalDispatchers.resetForTest()
        val created = Collections.synchronizedList(mutableListOf<String>())
        OneSignalDispatchers.beforeLaneCreateForTest = { created += it }

        try {
            OneSignalDispatchers.prewarm(null)
            OneSignalDispatchers.awaitReadyForTest() shouldBe true
            created.first() shouldBe "INGRESS"
        } finally {
            OneSignalDispatchers.resetForTest()
        }
    }

    test("first launch returns while its lane is still being created off caller") {
        OneSignalDispatchers.resetForTest()
        val createStarted = CountDownLatch(1)
        val allowCreate = CountDownLatch(1)
        val launchReturned = CountDownLatch(1)
        val workRan = CountDownLatch(1)
        val createThreadId = AtomicLong()
        val callerThreadId = AtomicLong()

        OneSignalDispatchers.beforeLaneCreateForTest = { lane ->
            if (lane == "IO") {
                createThreadId.set(Thread.currentThread().id)
                createStarted.countDown()
                allowCreate.await()
            }
        }

        val caller =
            Thread {
                callerThreadId.set(Thread.currentThread().id)
                OneSignalDispatchers.launchOnIO { workRan.countDown() }
                launchReturned.countDown()
            }
        caller.start()

        createStarted.await(1, TimeUnit.SECONDS) shouldBe true
        launchReturned.await(1, TimeUnit.SECONDS) shouldBe true
        createThreadId.get() shouldNotBe callerThreadId.get()
        allowCreate.countDown()
        workRan.await(1, TimeUnit.SECONDS) shouldBe true
        OneSignalDispatchers.beforeLaneCreateForTest = null
    }

    test("cold queued launch completes when its generation is reset") {
        OneSignalDispatchers.resetForTest()
        val createStarted = CountDownLatch(1)
        val allowCreate = CountDownLatch(1)
        val completed = CountDownLatch(1)
        OneSignalDispatchers.beforeLaneCreateForTest = { lane ->
            if (lane == "IO") {
                createStarted.countDown()
                allowCreate.await()
            }
        }

        OneSignalDispatchers.launchOnIO {}.invokeOnCompletion { completed.countDown() }
        createStarted.await(1, TimeUnit.SECONDS) shouldBe true
        OneSignalDispatchers.resetForTest()

        completed.await(1, TimeUnit.SECONDS) shouldBe true
        allowCreate.countDown()
    }

    test("fallback initialization failure does not wedge subsequent lanes") {
        OneSignalDispatchers.resetForTest()
        val failedJobCompleted = CountDownLatch(1)
        val defaultWorkRan = CountDownLatch(1)
        OneSignalDispatchers.beforeLaneCreateForTest = { lane ->
            if (lane == "IO") throw IllegalStateException("primary failed")
        }
        OneSignalDispatchers.beforeFallbackCreateForTest = { lane ->
            if (lane == "IO") throw AssertionError("fallback failed")
        }

        OneSignalDispatchers.launchOnIO {}.invokeOnCompletion { failedJobCompleted.countDown() }
        failedJobCompleted.await(1, TimeUnit.SECONDS) shouldBe true

        OneSignalDispatchers.beforeLaneCreateForTest = null
        OneSignalDispatchers.beforeFallbackCreateForTest = null
        OneSignalDispatchers.launchOnDefault { defaultWorkRan.countDown() }
        defaultWorkRan.await(1, TimeUnit.SECONDS) shouldBe true
    }

    test("Error during worker prestart shuts down the partial executor and uses the fallback") {
        OneSignalDispatchers.resetForTest()
        val workRan = CountDownLatch(1)
        var partial: ThreadPoolExecutor? = null
        OneSignalDispatchers.afterExecutorCreateForTest = { lane, executor ->
            if (lane == "IO") {
                partial = executor
                executor.prestartCoreThread()
                throw OutOfMemoryError("unable to create native thread")
            }
        }

        OneSignalDispatchers.launchOnIO { workRan.countDown() }

        workRan.await(1, TimeUnit.SECONDS) shouldBe true
        partial!!.isShutdown shouldBe true
        OneSignalDispatchers.resetForTest()
    }

    test("a closed generation is not reopened when its in-flight bootstrap fails") {
        OneSignalDispatchers.resetForTest()
        val createStarted = CountDownLatch(1)
        val allowCreate = CountDownLatch(1)
        val bootstrapFinished = CountDownLatch(1)
        val ioCreateCount = AtomicInteger()
        OneSignalDispatchers.beforeLaneCreateForTest = { lane ->
            if (lane == "IO") {
                ioCreateCount.incrementAndGet()
                createStarted.countDown()
                allowCreate.await()
                throw IllegalStateException("primary failed")
            }
        }
        OneSignalDispatchers.beforeFallbackCreateForTest = { lane ->
            if (lane == "IO") {
                bootstrapFinished.countDown()
                throw AssertionError("fallback failed")
            }
        }
        val oldIO = OneSignalDispatchers.IO

        OneSignalDispatchers.launchOnIO {}
        createStarted.await(1, TimeUnit.SECONDS) shouldBe true
        val hooks = OneSignalDispatchers.beforeLaneCreateForTest to OneSignalDispatchers.beforeFallbackCreateForTest
        OneSignalDispatchers.resetForTest()
        OneSignalDispatchers.beforeLaneCreateForTest = hooks.first
        OneSignalDispatchers.beforeFallbackCreateForTest = hooks.second
        allowCreate.countDown()
        bootstrapFinished.await(1, TimeUnit.SECONDS) shouldBe true

        val rejected = CountDownLatch(1)
        CoroutineScope(oldIO).launch {}.invokeOnCompletion { rejected.countDown() }
        rejected.await(1, TimeUnit.SECONDS) shouldBe true
        ioCreateCount.get() shouldBe 1
        OneSignalDispatchers.resetForTest()
    }

    test("executorStatus returns 'Active' / 'Shutdown' on the happy path and the Not initialized message when the underlying check throws") {
        // Happy paths (Shutdown / Active) are exercised indirectly via getStatus(); this
        // case pins down the defensive catch branch, which fires when the underlying
        // executor's lazy initializer is in a failed state (e.g. JVM-level
        // Executors.newSingleThreadExecutor refused to construct) and every isShutdown
        // access re-throws. Without this, the catch is unreachable from unit tests because
        // ThreadPoolExecutor.isShutdown does not normally throw.
        OneSignalDispatchers.executorStatus("ioExecutor") { false } shouldBe "Active"
        OneSignalDispatchers.executorStatus("ioExecutor") { true } shouldBe "Shutdown"
        OneSignalDispatchers.executorStatus("ioExecutor") {
            throw RuntimeException("init failure")
        } shouldBe "ioExecutor Not initialized init failure"
        OneSignalDispatchers.executorStatus("ioExecutor") {
            throw RuntimeException()
        } shouldBe "ioExecutor Not initialized Unknown error"
    }

    test("scopeStatus returns 'Active' / 'Cancelled' on the happy path and the Not initialized message when the underlying check throws") {
        OneSignalDispatchers.scopeStatus("IOScope") { true } shouldBe "Active"
        OneSignalDispatchers.scopeStatus("IOScope") { false } shouldBe "Cancelled"
        OneSignalDispatchers.scopeStatus("IOScope") {
            throw RuntimeException("cancelled supervisor")
        } shouldBe "IOScope Not initialized cancelled supervisor"
        OneSignalDispatchers.scopeStatus("IOScope") {
            throw RuntimeException()
        } shouldBe "IOScope Not initialized Unknown error"
    }

    test("flag off constructs the IO executor on the calling thread") {
        OneSignalDispatchers.bootstrapEnabledForTest = false
        OneSignalDispatchers.resetForTest()
        val callerThreadId = AtomicLong()
        val createThreadId = AtomicLong()
        val finished = CountDownLatch(1)
        OneSignalDispatchers.beforeLaneCreateForTest = { lane ->
            if (lane == "IO") createThreadId.set(Thread.currentThread().id)
        }

        Thread {
            callerThreadId.set(Thread.currentThread().id)
            OneSignalDispatchers.launchOnIO { }
            finished.countDown()
        }.start()

        finished.await(2, TimeUnit.SECONDS) shouldBe true
        createThreadId.get() shouldBe callerThreadId.get()
        createThreadId.get() shouldNotBe 0L
    }

    test("flag off prewarm warms ingress before the other lanes") {
        OneSignalDispatchers.bootstrapEnabledForTest = false
        OneSignalDispatchers.resetForTest()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val created = CountDownLatch(4)
        OneSignalDispatchers.beforeLaneCreateForTest = { lane ->
            order.add(lane)
            created.countDown()
        }
        OneSignalDispatchers.prewarm(null)
        created.await(2, TimeUnit.SECONDS) shouldBe true
        order.first() shouldBe "INGRESS"
    }

    test("flag off prewarm does not build executors after reset") {
        OneSignalDispatchers.bootstrapEnabledForTest = false
        OneSignalDispatchers.resetForTest()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        // Shutdown interrupts prewarm. Ignore it so the closed checks, not a dead thread, stop the build.
        OneSignalDispatchers.beforeLazyWarmForTest = {
            entered.countDown()
            awaitIgnoringInterrupts(release)
        }
        OneSignalDispatchers.prewarm(null)
        entered.await(2, TimeUnit.SECONDS) shouldBe true
        OneSignalDispatchers.resetForTest()
        val builtAfterReset = AtomicInteger()
        OneSignalDispatchers.afterExecutorCreateForTest = { _, _ -> builtAfterReset.incrementAndGet() }
        release.countDown()
        Thread.sleep(400)
        builtAfterReset.get() shouldBe 0
    }

    test("flag off prewarm does not build an executor when reset lands mid-build") {
        OneSignalDispatchers.bootstrapEnabledForTest = false
        OneSignalDispatchers.resetForTest()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        OneSignalDispatchers.beforeLaneCreateForTest = { lane ->
            if (lane == "INGRESS") {
                entered.countDown()
                awaitIgnoringInterrupts(release)
            }
        }
        OneSignalDispatchers.prewarm(null)
        entered.await(2, TimeUnit.SECONDS) shouldBe true
        OneSignalDispatchers.resetForTest()
        val builtAfterReset = AtomicInteger()
        OneSignalDispatchers.afterExecutorCreateForTest = { _, _ -> builtAfterReset.incrementAndGet() }
        release.countDown()
        Thread.sleep(400)
        builtAfterReset.get() shouldBe 0
    }

    test("flag off prewarm shuts down an executor built after reset") {
        OneSignalDispatchers.bootstrapEnabledForTest = false
        OneSignalDispatchers.resetForTest()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val built = AtomicReference<ThreadPoolExecutor>()
        OneSignalDispatchers.afterExecutorCreateForTest = { lane, executor ->
            if (lane == "INGRESS") {
                built.set(executor)
                entered.countDown()
                awaitIgnoringInterrupts(release)
            }
        }
        OneSignalDispatchers.prewarm(null)
        entered.await(2, TimeUnit.SECONDS) shouldBe true
        OneSignalDispatchers.resetForTest()
        release.countDown()
        Thread.sleep(400)
        built.get().isShutdown shouldBe true
    }

    test("flag off falls back when executor construction throws") {
        OneSignalDispatchers.bootstrapEnabledForTest = false
        OneSignalDispatchers.resetForTest()
        val fallbacks = AtomicInteger()
        OneSignalDispatchers.beforeLaneCreateForTest = { throw IllegalStateException("boom") }
        OneSignalDispatchers.beforeFallbackCreateForTest = { fallbacks.incrementAndGet() }
        val ran = CountDownLatch(1)
        OneSignalDispatchers.launchOnIO { ran.countDown() }
        ran.await(2, TimeUnit.SECONDS) shouldBe true
        fallbacks.get() shouldBe 1
    }

    test("flag off prewarm retries when the prewarm thread fails to start") {
        OneSignalDispatchers.bootstrapEnabledForTest = false
        OneSignalDispatchers.resetForTest()
        OneSignalDispatchers.beforeLazyPrewarmStartForTest = { throw IllegalStateException("start failed") }
        OneSignalDispatchers.prewarm(null)

        OneSignalDispatchers.beforeLazyPrewarmStartForTest = null
        val created = CountDownLatch(4)
        OneSignalDispatchers.beforeLaneCreateForTest = { created.countDown() }
        OneSignalDispatchers.prewarm(null)
        created.await(2, TimeUnit.SECONDS) shouldBe true
    }

    test("prewarm is idempotent: a second call is a no-op and does not spawn a second prewarm thread") {
        OneSignalDispatchers.resetForTest()
        OneSignalDispatchers.prewarm(null)
        OneSignalDispatchers.awaitReadyForTest() shouldBe true
        val statusBefore = OneSignalDispatchers.getStatus()
        OneSignalDispatchers.prewarm(null)
        OneSignalDispatchers.getStatus() shouldBe statusBefore
    }
})

private fun awaitIgnoringInterrupts(latch: CountDownLatch) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
    while (latch.count > 0 && System.nanoTime() < deadline) {
        try {
            latch.await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
        } catch (_: InterruptedException) {
            // Keep waiting: the test needs the thread alive after shutdown.
        }
    }
}
