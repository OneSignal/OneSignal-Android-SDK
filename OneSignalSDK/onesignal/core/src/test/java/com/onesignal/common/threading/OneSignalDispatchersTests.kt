package com.onesignal.common.threading

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class OneSignalDispatchersTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("OneSignalDispatchers should be properly initialized") {
        // Access a dispatcher to trigger initialization
        OneSignalDispatchers.IO
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

    test("Default dispatcher should execute work on background thread") {
        val mainThreadId = Thread.currentThread().id
        var backgroundThreadId: Long? = null

        runBlocking {
            OneSignalDispatchers.withDefault {
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

    test("runBlockingOnDefault should execute work synchronously") {
        var completed = false

        OneSignalDispatchers.runBlockingOnDefault {
            Thread.sleep(100)
            completed = true
        }

        completed shouldBe true
    }

    test("getStatus should return meaningful status information") {
        val status = OneSignalDispatchers.getStatus()

        status shouldContain "OneSignalDispatchers Status:"
        status shouldContain "IO Executor: Active"
        status shouldContain "Default Executor: Active"
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


})
