package com.onesignal.common.threading

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class OneSignalDispatchersTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("OneSignalDispatchers should be properly initialized") {
        OneSignalDispatchers.isInitialized() shouldBe true
    }

    test("IO dispatcher should execute work on background thread") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        runBlocking {
            OneSignalDispatchers.withIO {
                backgroundThreadId = Thread.currentThread().id
            }
        }

        backgroundThreadId shouldNotBe null
        backgroundThreadId shouldNotBe mainThreadId
    }

    test("Computation dispatcher should execute work on background thread") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        runBlocking {
            OneSignalDispatchers.withComputation {
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

    test("runBlockingOnIO should execute work synchronously") {
        var completed = false

        OneSignalDispatchers.runBlockingOnIO {
            Thread.sleep(100)
            completed = true
        }

        completed shouldBe true
    }

    test("runBlockingOnComputation should execute work synchronously") {
        var completed = false

        OneSignalDispatchers.runBlockingOnComputation {
            Thread.sleep(100)
            completed = true
        }

        completed shouldBe true
    }

    test("getStatus should return meaningful status information") {
        val status = OneSignalDispatchers.getStatus()

        status shouldContain "OneSignalDispatchers Status:"
        status shouldContain "IO Executor: Active"
        status shouldContain "Computation Executor: Active"
        status shouldContain "IO Scope: Active"
        status shouldContain "Default Scope: Active"
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
        val latch = CountDownLatch(20)
        val completed = AtomicInteger(0)

        repeat(20) { i ->
            OneSignalDispatchers.launchOnIO {
                Thread.sleep(10)
                completed.incrementAndGet()
                latch.countDown()
            }
        }

        latch.await()
        completed.get() shouldBe 20
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

    test("rapid sequential launches should complete successfully") {
        val latch = CountDownLatch(50)
        val completed = AtomicInteger(0)

        repeat(50) { i ->
            OneSignalDispatchers.launchOnIO {
                Thread.sleep(5)
                completed.incrementAndGet()
                latch.countDown()
            }
        }

        latch.await()
        completed.get() shouldBe 50
    }

    test("runBlocking calls should not interfere with each other") {
        val latch = CountDownLatch(5)
        val results = mutableListOf<Int>()

        repeat(5) { i ->
            Thread {
                val result =
                    OneSignalDispatchers.runBlockingOnIO {
                        Thread.sleep(20)
                        i
                    }
                synchronized(results) {
                    results.add(result)
                }
                latch.countDown()
            }.start()
        }

        latch.await()
        results.size shouldBe 5
        results.sorted() shouldBe (0..4).toList()
    }

    test("dispatchers should remain active after heavy usage") {
        val latch = CountDownLatch(100)

        repeat(100) { i ->
            OneSignalDispatchers.launchOnIO {
                Thread.sleep(5)
                latch.countDown()
            }
        }

        latch.await()
        OneSignalDispatchers.isInitialized() shouldBe true
    }

    test("empty task blocks should not cause issues") {
        val latch = CountDownLatch(10)

        repeat(10) { i ->
            OneSignalDispatchers.launchOnIO {
                // Empty block
                latch.countDown()
            }
        }

        latch.await()
        OneSignalDispatchers.isInitialized() shouldBe true
    }

    test("tasks that throw immediately should be handled") {
        val latch = CountDownLatch(5)
        val errorCount = AtomicInteger(0)

        repeat(5) { i ->
            OneSignalDispatchers.launchOnIO {
                try {
                    throw RuntimeException("Immediate error")
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        errorCount.get() shouldBe 5
    }

    test("withIO should work correctly under concurrent access") {
        val latch = CountDownLatch(10)
        val results = mutableListOf<Int>()

        repeat(10) { i ->
            OneSignalDispatchers.launchOnIO {
                OneSignalDispatchers.withIO {
                    synchronized(results) {
                        results.add(i)
                    }
                }
                latch.countDown()
            }
        }

        latch.await()
        results.size shouldBe 10
        results.sorted() shouldBe (0..9).toList()
    }

    test("withComputation should work correctly under concurrent access") {
        val latch = CountDownLatch(10)
        val results = mutableListOf<Int>()

        repeat(10) { i ->
            OneSignalDispatchers.launchOnDefault {
                OneSignalDispatchers.withComputation {
                    synchronized(results) {
                        results.add(i)
                    }
                }
                latch.countDown()
            }
        }

        latch.await()
        results.size shouldBe 10
        results.sorted() shouldBe (0..9).toList()
    }
})
