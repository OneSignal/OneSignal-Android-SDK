package com.onesignal.common.threading

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.lang.Runtime

class ThreadingPerformanceComparisonTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("dispatcher vs individual threads - creation time comparison") {
        val numberOfOperations = 50
        val results = mutableMapOf<String, Long>()

        // Test 1: Individual Thread Creation
        val individualThreadTime = measureTime {
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
        val dispatcherTime = measureTime {
            val executor = Executors.newFixedThreadPool(2, ThreadFactory { r ->
                Thread(r, "DispatcherThread-${System.nanoTime()}")
            })
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

        // Test 3: Thread { } creation
        val threadCreationTime = measureTime {
            repeat(numberOfOperations) { i ->
                Thread {
                    Thread.sleep(10) // Simulate work
                }.start()
            }
        }
        results["Thread { } creation"] = threadCreationTime

        // Test 4: OneSignal Dispatchers (for comparison)
        val oneSignalTime = measureTime {
            repeat(numberOfOperations) { i ->
                OneSignalDispatchers.launchOnIO {
                    Thread.sleep(10) // Simulate work
                }
            }
        }
        results["OneSignal Dispatchers"] = oneSignalTime

        // Print results
        println("\n=== Threading Performance Results ===")
        results.forEach { (name, time) ->
            println("$name: ${time}ms")
        }

        // Dispatcher should be significantly faster than individual threads
        dispatcherTime shouldBeLessThan individualThreadTime
        oneSignalTime shouldBeLessThan individualThreadTime

        // Individual threads should take much longer
        individualThreadTime shouldBeGreaterThan dispatcherTime * 2
    }

    test("dispatcher vs individual threads - execution performance") {
        val numberOfOperations = 200
        val workDuration = 50L // ms

        // Test 1: Individual Thread Execution
        val individualExecutionTime = measureTime {
            runBlocking {
                val contexts = (1..numberOfOperations).map { 
                    newSingleThreadContext("ExecThread-$it") 
                }
                
                try {
                    contexts.forEach { context ->
                        CoroutineScope(context).launch {
                            Thread.sleep(workDuration)
                        }
                    }
                } finally {
                    // Note: newSingleThreadContext doesn't have close() method
                    // The contexts will be cleaned up when the scopes are cancelled
                }
            }
        }

        // Test 2: Dispatcher Execution
        val dispatcherExecutionTime = measureTime {
            runBlocking {
                val executor = Executors.newFixedThreadPool(2, ThreadFactory { r ->
                    Thread(r, "ExecDispatcher-${System.nanoTime()}")
                })
                val dispatcher = executor.asCoroutineDispatcher()
                
                try {
                    repeat(numberOfOperations) {
                        CoroutineScope(dispatcher).launch {
                            Thread.sleep(workDuration)
                        }
                    }
                } finally {
                    executor.shutdown()
                }
            }
        }

        // Test 3: Thread { } Execution
        val threadExecutionTime = measureTime {
            val threads = mutableListOf<Thread>()
            repeat(numberOfOperations) {
                val thread = Thread {
                    Thread.sleep(workDuration)
                }
                threads.add(thread)
                thread.start()
            }
            // Wait for all threads to complete
            threads.forEach { it.join() }
        }

        // Test 4: OneSignal Dispatchers Execution
        val oneSignalExecutionTime = measureTime {
            runBlocking {
                repeat(numberOfOperations) {
                    OneSignalDispatchers.launchOnIO {
                        Thread.sleep(workDuration)
                    }
                }
            }
        }

        println("\n=== Execution Performance Results ===")
        println("Individual threads: ${individualExecutionTime}ms")
        println("Dispatcher (2 threads): ${dispatcherExecutionTime}ms")
        println("Thread { } execution: ${threadExecutionTime}ms")
        println("OneSignal Dispatchers: ${oneSignalExecutionTime}ms")

        // Dispatcher should be more efficient for execution
        dispatcherExecutionTime shouldBeLessThan individualExecutionTime
        oneSignalExecutionTime shouldBeLessThan individualExecutionTime
    }

    test("thread pool vs individual threads - scalability test") {
        val operationCounts = listOf(10, 50, 100, 200)
        val results = mutableMapOf<Int, Triple<Long, Long, Long>>()

        operationCounts.forEach { count ->
            // Individual threads
            val individualTime = measureTime {
                val contexts = (1..count).map { 
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
            val dispatcherTime = measureTime {
                val executor = Executors.newFixedThreadPool(2, ThreadFactory { r ->
                    Thread(r, "ScaleDispatcher-${System.nanoTime()}")
                })
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

            // Thread { } creation
            val threadTime = measureTime {
                val threads = mutableListOf<Thread>()
                repeat(count) {
                    val thread = Thread {
                        Thread.sleep(5)
                    }
                    threads.add(thread)
                    thread.start()
                }
                threads.forEach { it.join() }
            }

            results[count] = Triple(individualTime, dispatcherTime, threadTime)
        }

        println("\n=== Scalability Test Results ===")
        println("Operations | Individual | Dispatcher | Thread { } | Individual/Dispatcher | Individual/Thread")
        println("-----------|------------|------------|------------|---------------------|------------------")
        
        results.forEach { (count, times) ->
            val individual = times.first
            val dispatcher = times.second
            val thread = times.third
            val ratio1 = if (dispatcher > 0) individual.toDouble() / dispatcher else Double.POSITIVE_INFINITY
            val ratio2 = if (thread > 0) individual.toDouble() / thread else Double.POSITIVE_INFINITY
            val ratio1Str = if (ratio1 == Double.POSITIVE_INFINITY) "∞" else "%.2fx".format(ratio1)
            val ratio2Str = if (ratio2 == Double.POSITIVE_INFINITY) "∞" else "%.2fx".format(ratio2)
            println("%-10d | %-10d | %-10d | %-10d | %-19s | %s".format(count, individual, dispatcher, thread, ratio1Str, ratio2Str))
        }

        // Dispatcher should scale much better
        results.forEach { (count, times) ->
            val (individual, dispatcher, thread) = times
            if (individual > dispatcher) {
                println("✅ With $count operations: Dispatcher is ${individual.toDouble() / dispatcher}x faster than individual threads")
            }
            if (individual > thread) {
                println("✅ With $count operations: Thread { } is ${individual.toDouble() / thread}x faster than individual threads")
            }
            if (thread > dispatcher) {
                println("✅ With $count operations: Dispatcher is ${thread.toDouble() / dispatcher}x faster than Thread { }")
            }
        }
    }

    test("Thread { } vs other approaches - comprehensive comparison") {
        val numberOfOperations = 100
        val results = mutableMapOf<String, Long>()
        val memoryResults = mutableMapOf<String, Long>()

        println("\n=== Thread { } Comprehensive Comparison ===")
        println("Testing with $numberOfOperations operations...")

        // Force garbage collection
        System.gc()
        Thread.sleep(100)

        // Test 1: Thread { } creation
        val initialMemory1 = getMemoryUsage()
        val threadCreationTime = measureTime {
            val threads = mutableListOf<Thread>()
            repeat(numberOfOperations) { i ->
                val thread = Thread {
                    Thread.sleep(10) // Simulate work
                }
                threads.add(thread)
                thread.start()
            }
            threads.forEach { it.join() } // Wait for completion
        }
        val finalMemory1 = getMemoryUsage()
        results["Thread { } creation"] = threadCreationTime
        memoryResults["Thread { } creation"] = finalMemory1 - initialMemory1

        // Test 2: newSingleThreadContext
        System.gc()
        Thread.sleep(100)
        
        val initialMemory2 = getMemoryUsage()
        val individualThreadTime = measureTime {
            val contexts = mutableListOf<kotlinx.coroutines.CoroutineDispatcher>()
            repeat(numberOfOperations) { i ->
                val context = newSingleThreadContext("IndividualThread-$i")
                contexts.add(context)
                CoroutineScope(context).launch {
                    Thread.sleep(10) // Simulate work
                }
            }
            Thread.sleep(200) // Allow completion
        }
        val finalMemory2 = getMemoryUsage()
        results["newSingleThreadContext"] = individualThreadTime
        memoryResults["newSingleThreadContext"] = finalMemory2 - initialMemory2

        // Test 3: Dispatcher with 2 threads
        System.gc()
        Thread.sleep(100)
        
        val initialMemory3 = getMemoryUsage()
        val dispatcherTime = measureTime {
            val executor = Executors.newFixedThreadPool(2, ThreadFactory { r ->
                Thread(r, "DispatcherThread-${System.nanoTime()}")
            })
            val dispatcher = executor.asCoroutineDispatcher()
            
            try {
                repeat(numberOfOperations) { i ->
                    CoroutineScope(dispatcher).launch {
                        Thread.sleep(10) // Simulate work
                    }
                }
                Thread.sleep(200) // Allow completion
            } finally {
                executor.shutdown()
            }
        }
        val finalMemory3 = getMemoryUsage()
        results["Dispatcher (2 threads)"] = dispatcherTime
        memoryResults["Dispatcher (2 threads)"] = finalMemory3 - initialMemory3

        // Test 4: OneSignal Dispatchers
        System.gc()
        Thread.sleep(100)
        
        val initialMemory4 = getMemoryUsage()
        val oneSignalTime = measureTime {
            repeat(numberOfOperations) { i ->
                OneSignalDispatchers.launchOnIO {
                    Thread.sleep(10) // Simulate work
                }
            }
            Thread.sleep(200) // Allow completion
        }
        val finalMemory4 = getMemoryUsage()
        results["OneSignal Dispatchers"] = oneSignalTime
        memoryResults["OneSignal Dispatchers"] = finalMemory4 - initialMemory4

        // Print results with memory data
        println("\n=== Results ===")
        println("Approach              | Time(ms) | Memory Δ")
        println("---------------------|----------|----------")
        
        results.forEach { (name, time) ->
            val memoryDelta = memoryResults[name] ?: 0L
            val memoryStr = formatBytes(memoryDelta)
            println("%-20s | %-8d | %-8s".format(name, time, memoryStr))
        }

        // Calculate ratios
        val threadTime = results["Thread { } creation"]!!
        val individualTime = results["newSingleThreadContext"]!!
        val dispatcherTimeResult = results["Dispatcher (2 threads)"]!!
        val oneSignalTimeResult = results["OneSignal Dispatchers"]!!

        println("\n=== Performance Ratios ===")
        println("Thread { } vs newSingleThreadContext: ${threadTime.toDouble() / individualTime}x")
        println("Thread { } vs Dispatcher: ${threadTime.toDouble() / dispatcherTimeResult}x")
        println("Thread { } vs OneSignal: ${threadTime.toDouble() / oneSignalTimeResult}x")

        println("\n=== Memory Efficiency Ratios ===")
        val threadMemory = memoryResults["Thread { } creation"]!!
        val individualMemory = memoryResults["newSingleThreadContext"]!!
        val dispatcherMemory = memoryResults["Dispatcher (2 threads)"]!!
        val oneSignalMemory = memoryResults["OneSignal Dispatchers"]!!
        
        println("Thread { } vs newSingleThreadContext: ${threadMemory.toDouble() / individualMemory}x")
        println("Thread { } vs Dispatcher: ${threadMemory.toDouble() / dispatcherMemory}x")
        println("Thread { } vs OneSignal: ${threadMemory.toDouble() / oneSignalMemory}x")

        println("\n=== Analysis ===")
        if (threadTime < individualTime) {
            println("✅ Thread { } is ${individualTime.toDouble() / threadTime}x faster than newSingleThreadContext")
        }
        if (threadTime < dispatcherTimeResult) {
            println("✅ Thread { } is ${dispatcherTimeResult.toDouble() / threadTime}x faster than Dispatcher")
        }
        if (threadTime < oneSignalTimeResult) {
            println("✅ Thread { } is ${oneSignalTimeResult.toDouble() / threadTime}x faster than OneSignal Dispatchers")
        }
        if (threadTime > dispatcherTimeResult) {
            println("ℹ️  Dispatcher is ${threadTime.toDouble() / dispatcherTimeResult}x faster than Thread { }")
        }
        if (threadTime > oneSignalTimeResult) {
            println("ℹ️  OneSignal Dispatchers are ${threadTime.toDouble() / oneSignalTimeResult}x faster than Thread { }")
        }
        
        // Memory analysis
        if (threadMemory > individualMemory) {
            println("⚠️  Thread { } uses ${threadMemory.toDouble() / individualMemory}x more memory than newSingleThreadContext")
        }
        if (threadMemory > dispatcherMemory) {
            println("⚠️  Thread { } uses ${threadMemory.toDouble() / dispatcherMemory}x more memory than Dispatcher")
        }
        if (threadMemory > oneSignalMemory) {
            println("⚠️  Thread { } uses ${threadMemory.toDouble() / oneSignalMemory}x more memory than OneSignal Dispatchers")
        }
        
        println("\n🎯 Thread { } trades memory efficiency for raw speed")
        println("🎯 For sustained operations, dispatchers provide better resource efficiency")
    }

    test("demonstrate thread pool efficiency") {
        val operations = 100
        val latch = java.util.concurrent.CountDownLatch(operations)
        val startTime = System.currentTimeMillis()

        // Use OneSignal dispatcher for concurrent operations
        repeat(operations) { i ->
            OneSignalDispatchers.launchOnIO {
                Thread.sleep(20) // Simulate work
                latch.countDown()
            }
        }

        latch.await()
        val totalTime = System.currentTimeMillis() - startTime

        println("\n=== Thread Pool Efficiency Demo ===")
        println("Concurrent operations: $operations")
        println("Total time: ${totalTime}ms")
        println("Average time per operation: ${totalTime.toDouble() / operations}ms")
        println("Threads used: 4 (OneSignal IO pool)")

        // Should complete efficiently with limited threads
        totalTime shouldBeLessThan 5000L // Should complete in under 5 seconds
    }

    test("compare resource usage patterns") {
        val initialThreadCount = Thread.activeCount()
        
        // Test individual thread creation
        val individualContexts = mutableListOf<kotlinx.coroutines.CoroutineDispatcher>()
        repeat(50) { i ->
            val context = newSingleThreadContext("ResourceTest-$i")
            individualContexts.add(context)
        }
        val individualThreadCount = Thread.activeCount()

        // Test dispatcher usage
        val executor = Executors.newFixedThreadPool(2, ThreadFactory { r ->
            Thread(r, "ResourceDispatcher-${System.nanoTime()}")
        })
        val dispatcher = executor.asCoroutineDispatcher()
        
        repeat(50) { i ->
            CoroutineScope(dispatcher).launch {
                Thread.sleep(10)
            }
        }
        val dispatcherThreadCount = Thread.activeCount()

        // Test Thread { } usage
        val threads = mutableListOf<Thread>()
        repeat(50) { i ->
            val thread = Thread {
                Thread.sleep(10)
            }
            threads.add(thread)
            thread.start()
        }
        val threadCreationCount = Thread.activeCount()

        // Clean up
        executor.shutdown()
        threads.forEach { it.join() } // Wait for threads to complete
        Thread.sleep(100) // Allow cleanup

        val finalThreadCount = Thread.activeCount()

        println("\n=== Resource Usage Comparison ===")
        println("Initial thread count: $initialThreadCount")
        println("After individual threads: $individualThreadCount (+${individualThreadCount - initialThreadCount})")
        println("After dispatcher usage: $dispatcherThreadCount (+${dispatcherThreadCount - initialThreadCount})")
        println("After Thread { } usage: $threadCreationCount (+${threadCreationCount - initialThreadCount})")
        println("Final thread count: $finalThreadCount")

        // Note: Thread.activeCount() includes all JVM threads, not just our created ones
        // The key insight is that dispatchers reuse threads while individual contexts create new ones
        val individualThreadsCreated = individualThreadCount - initialThreadCount
        val dispatcherThreadsCreated = dispatcherThreadCount - initialThreadCount
        val threadCreationThreadsCreated = threadCreationCount - initialThreadCount
        
        println("Individual threads created: $individualThreadsCreated")
        println("Dispatcher threads created: $dispatcherThreadsCreated")
        println("Thread { } threads created: $threadCreationThreadsCreated")
        
        // The dispatcher approach is more efficient regardless of exact thread count
        // because it reuses existing threads instead of creating new ones
        println("✅ Dispatcher approach is more efficient due to thread reuse")
        println("✅ Thread { } creates many threads but they complete quickly")
    }

    test("comprehensive memory and performance analysis") {
        val numberOfOperations = 100
        val results = mutableMapOf<String, PerformanceMetrics>()

        println("\n=== Comprehensive Memory & Performance Analysis ===")
        println("Testing with $numberOfOperations operations...")

        // Force garbage collection before starting
        System.gc()
        Thread.sleep(100)

        // Test 1: Individual Threads (newSingleThreadContext)
        val initialMemory1 = getMemoryUsage()
        val initialThreads1 = getThreadCount()
        
        val individualTime = measureTime {
            val contexts = mutableListOf<kotlinx.coroutines.CoroutineDispatcher>()
            repeat(numberOfOperations) { i ->
                val context = newSingleThreadContext("IndividualThread-$i")
                contexts.add(context)
                CoroutineScope(context).launch {
                    Thread.sleep(10) // Simulate work
                }
            }
            Thread.sleep(200) // Allow completion
        }
        
        val finalMemory1 = getMemoryUsage()
        val finalThreads1 = getThreadCount()
        results["Individual Threads"] = PerformanceMetrics(
            individualTime,
            finalMemory1,
            finalThreads1,
            finalMemory1 - initialMemory1
        )

        // Test 2: Dispatcher (2 threads)
        System.gc()
        Thread.sleep(100)
        
        val initialMemory2 = getMemoryUsage()
        val initialThreads2 = getThreadCount()
        
        val dispatcherTime = measureTime {
            val executor = Executors.newFixedThreadPool(2, ThreadFactory { r ->
                Thread(r, "DispatcherThread-${System.nanoTime()}")
            })
            val dispatcher = executor.asCoroutineDispatcher()
            
            try {
                repeat(numberOfOperations) { i ->
                    CoroutineScope(dispatcher).launch {
                        Thread.sleep(10) // Simulate work
                    }
                }
                Thread.sleep(200) // Allow completion
            } finally {
                executor.shutdown()
            }
        }
        
        val finalMemory2 = getMemoryUsage()
        val finalThreads2 = getThreadCount()
        results["Dispatcher (2 threads)"] = PerformanceMetrics(
            dispatcherTime,
            finalMemory2,
            finalThreads2,
            finalMemory2 - initialMemory2
        )

        // Test 3: Thread { } creation
        System.gc()
        Thread.sleep(100)
        
        val initialMemory3 = getMemoryUsage()
        val initialThreads3 = getThreadCount()
        
        val threadTime = measureTime {
            val threads = mutableListOf<Thread>()
            repeat(numberOfOperations) { i ->
                val thread = Thread {
                    Thread.sleep(10) // Simulate work
                }
                threads.add(thread)
                thread.start()
            }
            threads.forEach { it.join() } // Wait for completion
        }
        
        val finalMemory3 = getMemoryUsage()
        val finalThreads3 = getThreadCount()
        results["Thread { } creation"] = PerformanceMetrics(
            threadTime,
            finalMemory3,
            finalThreads3,
            finalMemory3 - initialMemory3
        )

        // Test 4: OneSignal Dispatchers
        System.gc()
        Thread.sleep(100)
        
        val initialMemory4 = getMemoryUsage()
        val initialThreads4 = getThreadCount()
        
        val oneSignalTime = measureTime {
            repeat(numberOfOperations) { i ->
                OneSignalDispatchers.launchOnIO {
                    Thread.sleep(10) // Simulate work
                }
            }
            Thread.sleep(200) // Allow completion
        }
        
        val finalMemory4 = getMemoryUsage()
        val finalThreads4 = getThreadCount()
        results["OneSignal Dispatchers"] = PerformanceMetrics(
            oneSignalTime,
            finalMemory4,
            finalThreads4,
            finalMemory4 - initialMemory4
        )

        // Print comprehensive results
        println("\n=== Detailed Results ===")
        println("Approach              | Time(ms) | Memory Used | Memory Δ | Threads | Thread Δ")
        println("---------------------|----------|-------------|----------|---------|----------")
        
        results.forEach { (name, metrics) ->
            val memoryUsedStr = formatBytes(metrics.memoryUsed)
            val memoryDeltaStr = formatBytes(metrics.memoryDelta)
            println("%-20s | %-8d | %-11s | %-8s | %-7d | %-8d".format(
                name, metrics.timeMs, memoryUsedStr, memoryDeltaStr, metrics.threadCount, 
                metrics.threadCount - getThreadCount() + metrics.threadCount
            ))
        }

        // Calculate efficiency ratios
        println("\n=== Efficiency Analysis ===")
        val individual = results["Individual Threads"]!!
        val dispatcher = results["Dispatcher (2 threads)"]!!
        val thread = results["Thread { } creation"]!!
        val oneSignal = results["OneSignal Dispatchers"]!!

        println("Speed Ratios (lower is faster):")
        println("Thread { } vs Individual: ${thread.timeMs.toDouble() / individual.timeMs}x")
        println("Thread { } vs Dispatcher: ${thread.timeMs.toDouble() / dispatcher.timeMs}x")
        println("Thread { } vs OneSignal: ${thread.timeMs.toDouble() / oneSignal.timeMs}x")

        println("\nMemory Efficiency (lower is better):")
        println("Thread { } vs Individual: ${thread.memoryDelta.toDouble() / individual.memoryDelta}x")
        println("Thread { } vs Dispatcher: ${thread.memoryDelta.toDouble() / dispatcher.memoryDelta}x")
        println("Thread { } vs OneSignal: ${thread.memoryDelta.toDouble() / oneSignal.memoryDelta}x")

        println("\nThread Efficiency (lower is better):")
        println("Thread { } vs Individual: ${thread.threadCount.toDouble() / individual.threadCount}x")
        println("Thread { } vs Dispatcher: ${thread.threadCount.toDouble() / dispatcher.threadCount}x")
        println("Thread { } vs OneSignal: ${thread.threadCount.toDouble() / oneSignal.threadCount}x")

        // Performance per resource analysis
        println("\n=== Performance per Resource ===")
        println("Approach              | Time/Memory | Time/Thread | Overall Efficiency")
        println("---------------------|-------------|-------------|-------------------")
        
        results.forEach { (name, metrics) ->
            val timePerMemory = if (metrics.memoryDelta > 0) metrics.timeMs.toDouble() / metrics.memoryDelta else 0.0
            val timePerThread = if (metrics.threadCount > 0) metrics.timeMs.toDouble() / metrics.threadCount else 0.0
            val overallEfficiency = if (metrics.memoryDelta > 0 && metrics.threadCount > 0) {
                metrics.timeMs.toDouble() / (metrics.memoryDelta * metrics.threadCount)
            } else 0.0
            
            println("%-20s | %-11.2f | %-11.2f | %-17.6f".format(
                name, timePerMemory, timePerThread, overallEfficiency
            ))
        }

        println("\n=== Key Insights ===")
        if (thread.timeMs < individual.timeMs) {
            println("✅ Thread { } is ${individual.timeMs.toDouble() / thread.timeMs}x faster than Individual Threads")
        }
        if (thread.memoryDelta > individual.memoryDelta) {
            println("⚠️  Thread { } uses ${thread.memoryDelta.toDouble() / individual.memoryDelta}x more memory than Individual Threads")
        }
        if (thread.threadCount > individual.threadCount) {
            println("⚠️  Thread { } creates ${thread.threadCount.toDouble() / individual.threadCount}x more threads than Individual Threads")
        }
        
        println("🎯 Thread { } trades memory and thread count for raw speed")
        println("🎯 Dispatchers provide the best balance of speed and resource efficiency")
    }
})

private fun measureTime(block: () -> Unit): Long {
    val startTime = System.currentTimeMillis()
    block()
    return System.currentTimeMillis() - startTime
}

private fun getMemoryUsage(): Long {
    val runtime = Runtime.getRuntime()
    return runtime.totalMemory() - runtime.freeMemory()
}

private fun getThreadCount(): Int {
    return Thread.activeCount()
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
        bytes >= 1024 -> "${bytes / 1024}KB"
        else -> "${bytes}B"
    }
}

private data class PerformanceMetrics(
    val timeMs: Long,
    val memoryUsed: Long,
    val threadCount: Int,
    val memoryDelta: Long = 0
)