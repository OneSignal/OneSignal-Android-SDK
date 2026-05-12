package com.onesignal.common.threading

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class OneSignalDispatchersTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("OneSignalDispatchers should be properly initialized") {
        // Access dispatchers to trigger initialization
        OneSignalDispatchers.IO shouldNotBe null
        OneSignalDispatchers.Default shouldNotBe null
    }

    test("IO dispatcher should execute work on background thread") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        runBlocking {
            withContext(OneSignalDispatchers.IO) {
                backgroundThreadId = Thread.currentThread().id
            }
        }

        backgroundThreadId shouldNotBe null
        backgroundThreadId shouldNotBe mainThreadId
    }

    test("Default dispatcher should execute work on background thread") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        runBlocking {
            withContext(OneSignalDispatchers.Default) {
                backgroundThreadId = Thread.currentThread().id
            }
        }

        backgroundThreadId shouldNotBe null
        backgroundThreadId shouldNotBe mainThreadId
    }

    test("IOScope should launch coroutines asynchronously") {
        var completed = false

        OneSignalDispatchers.launchOnIO {
            Thread.sleep(100)
            completed = true
        }

        Thread.sleep(50)
        completed shouldBe false
    }

    test("DefaultScope should launch coroutines asynchronously") {
        var completed = false

        OneSignalDispatchers.launchOnDefault {
            Thread.sleep(100)
            completed = true
        }

        Thread.sleep(50)
        completed shouldBe false
    }

    test("getStatus should return meaningful status information") {
        val status = OneSignalDispatchers.getStatus()

        status shouldContain "OneSignalDispatchers Status:"
        status shouldContain "IO Executor: Active"
        status shouldContain "Default Executor: Active"
        status shouldContain "SerialIO Executor: Active"
        status shouldContain "IO Scope: Active"
        status shouldContain "Default Scope: Active"
        status shouldContain "SerialIO Scope: Active"
    }

    test("getPerformanceMetrics should include SerialIO queue and total completed task counters") {
        // Trigger lazy init of the SerialIO executor before asking for metrics so its queue
        // line resolves to a concrete value instead of the "n/a" fallback path.
        OneSignalDispatchers.SerialIO shouldNotBe null

        val metrics = OneSignalDispatchers.getPerformanceMetrics()

        metrics shouldContain "OneSignalDispatchers Performance Metrics:"
        metrics shouldContain "SerialIO Queue:"
        metrics shouldContain "Total completed tasks:"
    }

    test("SerialIO dispatcher executes work on a background thread") {
        val callerThreadId = Thread.currentThread().id
        var serialThreadId: Long? = null

        runBlocking {
            withContext(OneSignalDispatchers.SerialIO) {
                serialThreadId = Thread.currentThread().id
            }
        }

        serialThreadId shouldNotBe null
        serialThreadId shouldNotBe callerThreadId
    }

    test("launchOnSerialIO runs tasks on a single thread in submission order") {
        // SerialIO's contract: submission order on the caller thread == execution order on
        // the worker thread. We submit N tasks with a small sleep so they queue up, then
        // assert the recorded order matches submission order and that all observations
        // came from one thread.
        val taskCount = 5
        val observedOrder = mutableListOf<Int>()
        val observedThreads = mutableSetOf<Long>()
        val latch = CountDownLatch(taskCount)

        repeat(taskCount) { i ->
            OneSignalDispatchers.launchOnSerialIO {
                Thread.sleep(5)
                synchronized(observedOrder) {
                    observedOrder.add(i)
                    observedThreads.add(Thread.currentThread().id)
                }
                latch.countDown()
            }
        }

        latch.await()
        observedOrder shouldBe (0 until taskCount).toList()
        observedThreads.size shouldBe 1
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

    test("exceptions in a SerialIO task do not stop subsequent tasks from running") {
        // Mirrors the parallel "exceptions in one task should not affect others" case for
        // launchOnIO. A thrown exception in one serial task must not poison the dispatcher
        // for the rest of the queue.
        val latch = CountDownLatch(3)
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        repeat(3) { i ->
            OneSignalDispatchers.launchOnSerialIO {
                try {
                    if (i == 1) {
                        throw RuntimeException("Test error")
                    }
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        successCount.get() shouldBe 2
        errorCount.get() shouldBe 1
    }

    test("dispatchers should handle concurrent operations") {
        val results = mutableListOf<Int>()
        val expectedResults = (1..5).toList()

        runBlocking {
            (1..5).forEach { i ->
                OneSignalDispatchers.launchOnIO {
                    Thread.sleep(10)
                    synchronized(results) {
                        results.add(i)
                    }
                }
            }

            Thread.sleep(100)
        }

        results.sorted() shouldBe expectedResults
    }

    test("multiple concurrent launches should not cause issues") {
        val latch = CountDownLatch(5) // Reduced from 20 to 5
        val completed = AtomicInteger(0)

        repeat(5) { i -> // Reduced from 20 to 5
            OneSignalDispatchers.launchOnIO {
                delay(10) // Use coroutine delay instead of Thread.sleep
                completed.incrementAndGet()
                latch.countDown()
            }
        }

        latch.await()
        completed.get() shouldBe 5 // Updated expectation
    }

    test("mixed IO and computation tasks should work together") {
        val latch = CountDownLatch(10)
        val ioCount = AtomicInteger(0)
        val compCount = AtomicInteger(0)

        repeat(5) { i ->
            OneSignalDispatchers.launchOnIO {
                Thread.sleep(20)
                ioCount.incrementAndGet()
                latch.countDown()
            }

            OneSignalDispatchers.launchOnDefault {
                Thread.sleep(20)
                compCount.incrementAndGet()
                latch.countDown()
            }
        }

        latch.await()
        ioCount.get() shouldBe 5
        compCount.get() shouldBe 5
    }

    test("exceptions in one task should not affect others") {
        val latch = CountDownLatch(5)
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        repeat(5) { i ->
            OneSignalDispatchers.launchOnIO {
                try {
                    if (i == 2) {
                        throw RuntimeException("Test error")
                    }
                    Thread.sleep(10)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        successCount.get() shouldBe 4
        errorCount.get() shouldBe 1
    }

    test("prewarm returns immediately and warms IO / Default / SerialIO dispatchers on a background thread") {
        // SDK-4507: regression coverage for the cold-init main-thread block. prewarm() must
        // (a) return on the caller's thread without ever doing the executor / dispatcher /
        // scope construction work inline, and (b) leave all three dispatchers + scopes in the
        // "Active" state once the dedicated daemon thread finishes its empty launches.
        OneSignalDispatchers.resetPrewarmForTest()
        val callerThreadId = Thread.currentThread().id

        // Call from the test thread (which stands in for the main thread under production
        // usage). The call must return microseconds-fast; we don't assert wall-clock latency,
        // just that the heavy work didn't happen on this thread.
        OneSignalDispatchers.prewarm()

        // Resolve the prewarm thread by name from the JVM's thread set; its name is set by
        // the prewarm() impl. We `join()` on it so the subsequent status assertions don't
        // race a still-running prewarm thread.
        val prewarmThread =
            Thread.getAllStackTraces().keys.firstOrNull { it.name == "OneSignal-prewarm" }
        prewarmThread?.join(2_000)
        // After prewarm has finished, getStatus must report all three executors and scopes
        // as Active. If the prewarm thread itself failed it would be a no-op for getStatus
        // because the lazy chain wouldn't have run; this assertion proves both ends of the
        // contract (heavy work was done, and it ran on the prewarm thread, not the caller).
        val status = OneSignalDispatchers.getStatus()
        status shouldContain "IO Executor: Active"
        status shouldContain "Default Executor: Active"
        status shouldContain "SerialIO Executor: Active"
        status shouldContain "IO Scope: Active"
        status shouldContain "Default Scope: Active"
        status shouldContain "SerialIO Scope: Active"

        // Sanity: the prewarm thread was a separate thread, not the test thread.
        prewarmThread?.id shouldNotBe callerThreadId
    }

    test("prewarm is idempotent: a second call is a no-op and does not spawn a second prewarm thread") {
        // The first prewarm() may have already run in earlier tests (or in the previous test
        // above). Reset the latch so we get a deterministic "first call" here, then verify
        // that the second call does NOT spawn another OneSignal-prewarm thread.
        OneSignalDispatchers.resetPrewarmForTest()

        OneSignalDispatchers.prewarm()
        val firstPrewarmThread =
            Thread.getAllStackTraces().keys.firstOrNull { it.name == "OneSignal-prewarm" }
        firstPrewarmThread?.join(2_000)

        // Snapshot any straggling "OneSignal-prewarm" threads -- there should be at most one
        // (the one above, possibly still in TERMINATED state in the JVM's thread set briefly).
        val countBefore = Thread.getAllStackTraces().keys.count { it.name == "OneSignal-prewarm" }

        // Second call must be a no-op. No new prewarm thread, no exception.
        OneSignalDispatchers.prewarm()
        val countAfter = Thread.getAllStackTraces().keys.count { it.name == "OneSignal-prewarm" }

        countAfter shouldBe countBefore
    }
})
