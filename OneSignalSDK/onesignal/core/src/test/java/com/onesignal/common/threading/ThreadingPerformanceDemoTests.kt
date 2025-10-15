package com.onesignal.common.threading

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class ThreadingPerformanceDemoTests : FunSpec({

    val runPerformanceTests = System.getenv("RUN_PERFORMANCE_TESTS") == "true"

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("demonstrate dispatcher vs individual threads performance").config(enabled = runPerformanceTests) {
        val numberOfOperations = 50
        val results = mutableMapOf<String, Long>()

        println("\n=== Threading Performance Comparison ===")
        println("Testing with $numberOfOperations operations...")

        // Test 1: Individual Thread Creation
        val individualThreadTime =
            measureTime {
                repeat(numberOfOperations) { i ->
                    val context = newSingleThreadContext("IndividualThread-$i")
                    try {
                        CoroutineScope(context).launch {
                            Thread.sleep(10) // Simulate work
                        }
                    } finally {
                        // Note: newSingleThreadContext doesn't have close() method
                        // The context will be cleaned up when the scope is cancelled
                    }
                }
            }
        results["Individual Threads"] = individualThreadTime

        // Test 2: Dispatcher with 2 threads
        val dispatcherTime =
            measureTime {
                val executor =
                    Executors.newFixedThreadPool(
                        2,
                        ThreadFactory { r ->
                            Thread(r, "DispatcherThread-${System.nanoTime()}")
                        },
                    )
                val dispatcher = executor.asCoroutineDispatcher()

                try {
                    repeat(numberOfOperations) { i ->
                        CoroutineScope(dispatcher).launch {
                            Thread.sleep(10) // Simulate work
                        }
                    }
                } finally {
                    executor.shutdown()
                }
            }
        results["Dispatcher (2 threads)"] = dispatcherTime

        // Test 3: OneSignal Dispatchers (for comparison)
        val oneSignalTime =
            measureTime {
                repeat(numberOfOperations) { i ->
                    OneSignalDispatchers.launchOnIO {
                        Thread.sleep(10) // Simulate work
                    }
                }
            }
        results["OneSignal Dispatchers"] = oneSignalTime

        // Print results
        println("\n=== Results ===")
        results.forEach { (name, time) ->
            println("$name: ${time}ms")
        }

        // Calculate ratios
        val individualTime = results["Individual Threads"]!!
        val dispatcherTimeResult = results["Dispatcher (2 threads)"]!!
        val oneSignalTimeResult = results["OneSignal Dispatchers"]!!

        println("\n=== Performance Ratios ===")
        println("Individual Threads vs Dispatcher: ${individualTime.toDouble() / dispatcherTimeResult}x slower")
        println("Individual Threads vs OneSignal: ${individualTime.toDouble() / oneSignalTimeResult}x slower")
        println("Dispatcher vs OneSignal: ${dispatcherTimeResult.toDouble() / oneSignalTimeResult}x slower")

        println("\n=== Analysis ===")
        if (individualTime > dispatcherTimeResult) {
            println("✅ Dispatcher is ${individualTime.toDouble() / dispatcherTimeResult}x faster than individual threads")
        }
        if (individualTime > oneSignalTimeResult) {
            println("✅ OneSignal Dispatchers are ${individualTime.toDouble() / oneSignalTimeResult}x faster than individual threads")
        }
    }

    test("demonstrate resource usage difference").config(enabled = runPerformanceTests) {
        val initialThreadCount = Thread.activeCount()

        println("\n=== Resource Usage Comparison ===")
        println("Initial thread count: $initialThreadCount")

        // Test individual thread creation
        val individualContexts = mutableListOf<kotlinx.coroutines.CoroutineDispatcher>()
        repeat(50) { i ->
            val context = newSingleThreadContext("ResourceTest-$i")
            individualContexts.add(context)
        }
        val individualThreadCount = Thread.activeCount()

        println("After creating 50 individual thread contexts: $individualThreadCount (+${individualThreadCount - initialThreadCount})")

        // Test dispatcher usage
        val executor =
            Executors.newFixedThreadPool(
                2,
                ThreadFactory { r ->
                    Thread(r, "ResourceDispatcher-${System.nanoTime()}")
                },
            )
        val dispatcher = executor.asCoroutineDispatcher()

        repeat(50) { i ->
            CoroutineScope(dispatcher).launch {
                Thread.sleep(10)
            }
        }
        val dispatcherThreadCount = Thread.activeCount()

        println("After using dispatcher with 50 operations: $dispatcherThreadCount (+${dispatcherThreadCount - initialThreadCount})")

        // Clean up
        executor.shutdown()
        Thread.sleep(100) // Allow cleanup

        val finalThreadCount = Thread.activeCount()
        println("Final thread count after cleanup: $finalThreadCount")

        println("\n=== Resource Analysis ===")
        val individualThreadsCreated = individualThreadCount - initialThreadCount
        val dispatcherThreadsCreated = dispatcherThreadCount - initialThreadCount

        println("Individual threads created: $individualThreadsCreated")
        println("Dispatcher threads created: $dispatcherThreadsCreated")

        if (dispatcherThreadsCreated < individualThreadsCreated) {
            println("✅ Dispatcher uses ${individualThreadsCreated - dispatcherThreadsCreated} fewer threads")
        }
    }

    test("demonstrate scalability difference").config(enabled = runPerformanceTests) {
        val operationCounts = listOf(10, 50, 100, 200)
        val results = mutableMapOf<Int, Pair<Long, Long>>()

        println("\n=== Scalability Test ===")
        println("Testing different operation counts...")

        operationCounts.forEach { count ->
            // Individual threads
            val individualTime =
                measureTime {
                    val contexts =
                        (1..count).map {
                            newSingleThreadContext("ScaleTest-$it")
                        }
                    try {
                        contexts.forEach { context ->
                            CoroutineScope(context).launch {
                                Thread.sleep(5)
                            }
                        }
                    } finally {
                        // Note: newSingleThreadContext doesn't have close() method
                        // The contexts will be cleaned up when the scopes are cancelled
                    }
                }

            // Dispatcher
            val dispatcherTime =
                measureTime {
                    val executor =
                        Executors.newFixedThreadPool(
                            2,
                            ThreadFactory { r ->
                                Thread(r, "ScaleDispatcher-${System.nanoTime()}")
                            },
                        )
                    val dispatcher = executor.asCoroutineDispatcher()

                    try {
                        repeat(count) {
                            CoroutineScope(dispatcher).launch {
                                Thread.sleep(5)
                            }
                        }
                    } finally {
                        executor.shutdown()
                    }
                }

            results[count] = Pair(individualTime, dispatcherTime)
        }

        println("\n=== Scalability Results ===")
        println("Operations | Individual | Dispatcher | Ratio")
        println("-----------|------------|------------|------")

        results.forEach { (count, times) ->
            val ratio = if (times.second > 0) times.first.toDouble() / times.second else Double.POSITIVE_INFINITY
            val ratioStr = if (ratio == Double.POSITIVE_INFINITY) "∞" else "%.2fx".format(ratio)
            println("%-10d | %-10d | %-10d | %s".format(count, times.first, times.second, ratioStr))
        }

        println("\n=== Scalability Analysis ===")
        results.forEach { (count, times) ->
            if (times.first > times.second) {
                val ratio = if (times.second > 0) times.first.toDouble() / times.second else Double.POSITIVE_INFINITY
                println("✅ With $count operations: Dispatcher is ${if (ratio == Double.POSITIVE_INFINITY) "infinitely" else "${ratio}x"} faster")
            }
        }
    }
})

private fun measureTime(block: () -> Unit): Long {
    val startTime = System.currentTimeMillis()
    block()
    return System.currentTimeMillis() - startTime
}
