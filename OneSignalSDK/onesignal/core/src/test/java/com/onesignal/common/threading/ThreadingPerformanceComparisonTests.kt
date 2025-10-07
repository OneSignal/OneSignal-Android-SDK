package com.onesignal.common.threading

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

// Performance tests - run manually when needed
// To run these tests, set the environment variable: RUN_PERFORMANCE_TESTS=true
class ThreadingPerformanceComparisonTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("simple performance test") {
        // Skip performance tests unless explicitly enabled
        if (System.getenv("RUN_PERFORMANCE_TESTS") != "true") {
            println("Skipping performance test - set RUN_PERFORMANCE_TESTS=true to run")
            return@test
        }
        
        println("Starting simple performance test...")
        
        // Test 1: Simple individual thread test
        val individualThreadTime = measureTime {
            val threads = mutableListOf<Thread>()
            repeat(10) { i ->
                val thread = Thread {
                    Thread.sleep(10) // Simulate work
                }
                threads.add(thread)
                thread.start()
            }
            // Wait for all threads to complete
            threads.forEach { it.join() }
        }
        println("Individual Threads: ${individualThreadTime}ms")

        // Test 2: Simple dispatcher test
        val dispatcherTime = measureTime {
            val executor = Executors.newFixedThreadPool(2, ThreadFactory { r ->
                Thread(r, "DispatcherThread-${System.nanoTime()}")
            })
            val dispatcher = executor.asCoroutineDispatcher()
            
            try {
                runBlocking {
                    repeat(10) { i ->
                        launch(dispatcher) {
                            Thread.sleep(10) // Simulate work
                        }
                    }
                }
            } finally {
                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
        println("Dispatcher (2 threads): ${dispatcherTime}ms")

        // Test 3: OneSignal Dispatchers test (this might be hanging)
        println("Testing OneSignal Dispatchers...")
        try {
            val oneSignalTime = measureTime {
                runBlocking {
                    repeat(10) { i ->
                        launch(OneSignalDispatchers.IO) {
                            Thread.sleep(10) // Simulate work
                        }
                    }
                }
            }
            println("OneSignal Dispatchers: ${oneSignalTime}ms")
        } catch (e: Exception) {
            println("OneSignal Dispatchers failed: ${e.message}")
        }

        // Test 4: OneSignal Dispatchers with launchOnIO (this might be hanging)
        println("Testing OneSignal launchOnIO...")
        try {
            val oneSignalFireAndForgetTime = measureTime {
                repeat(10) { i ->
                    OneSignalDispatchers.launchOnIO {
                        Thread.sleep(10) // Simulate work
                    }
                }
                // Give some time for completion
                Thread.sleep(100)
            }
            println("OneSignal (fire & forget): ${oneSignalFireAndForgetTime}ms")
        } catch (e: Exception) {
            println("OneSignal launchOnIO failed: ${e.message}")
        }

        println("Performance test completed!")
    }

    test("dispatcher vs individual threads - execution performance") {
        // Skip performance tests unless explicitly enabled
        if (System.getenv("RUN_PERFORMANCE_TESTS") != "true") {
            println("Skipping performance test - set RUN_PERFORMANCE_TESTS=true to run")
            return@test
        }
        val numberOfOperations = 20
        val workDuration = 50L // ms
        val results = mutableMapOf<String, Long>()

        // Test 1: Individual Threads
        val individualThreadTime = measureTime {
            val threads = mutableListOf<Thread>()
            repeat(numberOfOperations) { i ->
                val thread = Thread {
                    Thread.sleep(workDuration)
                }
                threads.add(thread)
                thread.start()
            }
            threads.forEach { it.join() }
        }
        results["Individual Threads"] = individualThreadTime

        // Test 2: Dispatcher with 2 threads
        val dispatcherTime = measureTime {
            val executor = Executors.newFixedThreadPool(2, ThreadFactory { r ->
                Thread(r, "DispatcherThread-${System.nanoTime()}")
            })
            val dispatcher = executor.asCoroutineDispatcher()
            
            try {
                runBlocking {
                    repeat(numberOfOperations) { i ->
                        launch(dispatcher) {
                            Thread.sleep(workDuration)
                        }
                    }
                }
            } finally {
                executor.shutdown()
            }
        }
        results["Dispatcher (2 threads)"] = dispatcherTime

        // Test 3: OneSignal Dispatchers
        val oneSignalTime = measureTime {
            runBlocking {
                repeat(numberOfOperations) { i ->
                    OneSignalDispatchers.launchOnIO {
                        Thread.sleep(workDuration)
                    }
                }
            }
        }
        results["OneSignal Dispatchers"] = oneSignalTime

        // Print results
        println("\n=== Execution Performance Results ===")
        results.forEach { (name, time) ->
            println("$name: ${time}ms")
        }

        // Dispatcher should be faster than individual threads
        dispatcherTime shouldBeLessThan individualThreadTime
        oneSignalTime shouldBeLessThan individualThreadTime
    }

    test("memory usage comparison") {
        // Skip performance tests unless explicitly enabled
        if (System.getenv("RUN_PERFORMANCE_TESTS") != "true") {
            println("Skipping performance test - set RUN_PERFORMANCE_TESTS=true to run")
            return@test
        }
        val numberOfOperations = 50
        val results = mutableMapOf<String, Long>()

        // Test 1: Individual Threads Memory Usage
        val initialMemory1 = getUsedMemory()
        val threads = mutableListOf<Thread>()
        repeat(numberOfOperations) { i ->
            val thread = Thread {
                Thread.sleep(100)
            }
            threads.add(thread)
            thread.start()
        }
        threads.forEach { it.join() }
        val finalMemory1 = getUsedMemory()
        val individualThreadMemory = finalMemory1 - initialMemory1
        results["Individual Threads Memory"] = individualThreadMemory

        // Test 2: Dispatcher Memory Usage
        val initialMemory2 = getUsedMemory()
        val executor = Executors.newFixedThreadPool(2, ThreadFactory { r ->
            Thread(r, "DispatcherThread-${System.nanoTime()}")
        })
        val dispatcher = executor.asCoroutineDispatcher()
        
        try {
            runBlocking {
                repeat(numberOfOperations) { i ->
                    launch(dispatcher) {
                        Thread.sleep(100)
                    }
                }
            }
        } finally {
            executor.shutdown()
        }
        val finalMemory2 = getUsedMemory()
        val dispatcherMemory = finalMemory2 - initialMemory2
        results["Dispatcher Memory"] = dispatcherMemory

        // Test 3: OneSignal Dispatchers Memory Usage
        val initialMemory3 = getUsedMemory()
        runBlocking {
            repeat(numberOfOperations) { i ->
                OneSignalDispatchers.launchOnIO {
                    Thread.sleep(100)
                }
            }
        }
        val finalMemory3 = getUsedMemory()
        val oneSignalMemory = finalMemory3 - initialMemory3
        results["OneSignal Dispatchers Memory"] = oneSignalMemory

        // Print results
        println("\n=== Memory Usage Results ===")
        results.forEach { (name, memory) ->
            println("$name: ${memory}KB")
        }

        // Dispatcher should use less memory than individual threads
        dispatcherMemory shouldBeLessThan individualThreadMemory
        oneSignalMemory shouldBeLessThan individualThreadMemory
    }

    test("scalability comparison") {
        // Skip performance tests unless explicitly enabled
        if (System.getenv("RUN_PERFORMANCE_TESTS") != "true") {
            println("Skipping performance test - set RUN_PERFORMANCE_TESTS=true to run")
            return@test
        }
        val testSizes = listOf(10, 50, 100)
        val results = mutableMapOf<String, MutableMap<Int, Long>>()

        testSizes.forEach { size ->
            println("Testing with $size operations...")
            
            // Individual Threads
            val individualTime = measureTime {
                val threads = mutableListOf<Thread>()
                repeat(size) { i ->
                    val thread = Thread {
                        Thread.sleep(10)
                    }
                    threads.add(thread)
                    thread.start()
                }
                threads.forEach { it.join() }
            }
            results.getOrPut("Individual Threads") { mutableMapOf() }[size] = individualTime

            // Dispatcher
            val dispatcherTime = measureTime {
                val executor = Executors.newFixedThreadPool(2, ThreadFactory { r ->
                    Thread(r, "DispatcherThread-${System.nanoTime()}")
                })
                val dispatcher = executor.asCoroutineDispatcher()
                
                try {
                    runBlocking {
                        repeat(size) { i ->
                            launch(dispatcher) {
                                Thread.sleep(10)
                            }
                        }
                    }
                } finally {
                    executor.shutdown()
                }
            }
            results.getOrPut("Dispatcher") { mutableMapOf() }[size] = dispatcherTime

            // OneSignal Dispatchers
            val oneSignalTime = measureTime {
                runBlocking {
                    repeat(size) { i ->
                        OneSignalDispatchers.launchOnIO {
                            Thread.sleep(10)
                        }
                    }
                }
            }
            results.getOrPut("OneSignal Dispatchers") { mutableMapOf() }[size] = oneSignalTime
        }

        // Print scalability results
        println("\n=== Scalability Results ===")
        results.forEach { (name, times) ->
            println("$name:")
            times.forEach { (size, time) ->
                println("  $size operations: ${time}ms")
            }
        }

        // Verify that dispatcher scales better than individual threads
        testSizes.forEach { size ->
            val individualTime = results["Individual Threads"]!![size]!!
            val dispatcherTime = results["Dispatcher"]!![size]!!
            val oneSignalTime = results["OneSignal Dispatchers"]!![size]!!
            
            dispatcherTime shouldBeLessThan individualTime
            oneSignalTime shouldBeLessThan individualTime
        }
    }

    test("thread creation vs dispatcher creation performance") {
        // Skip performance tests unless explicitly enabled
        if (System.getenv("RUN_PERFORMANCE_TESTS") != "true") {
            println("Skipping performance test - set RUN_PERFORMANCE_TESTS=true to run")
            return@test
        }
        val numberOfTests = 1000
        val results = mutableMapOf<String, Long>()

        // Test 1: Individual Thread Creation
        val threadCreationTime = measureTime {
            repeat(numberOfTests) { i ->
                Thread {
                    // Empty thread
                }.start()
            }
        }
        results["Thread Creation"] = threadCreationTime

        // Test 2: Dispatcher Creation
        val dispatcherCreationTime = measureTime {
            repeat(numberOfTests) { i ->
                val executor = Executors.newFixedThreadPool(2, ThreadFactory { r ->
                    Thread(r, "DispatcherThread-${System.nanoTime()}")
                })
                val dispatcher = executor.asCoroutineDispatcher()
                executor.shutdown()
            }
        }
        results["Dispatcher Creation"] = dispatcherCreationTime

        // Test 3: OneSignal Dispatchers (reuse existing)
        val oneSignalTime = measureTime {
            repeat(numberOfTests) { i ->
                OneSignalDispatchers.launchOnIO {
                    // Empty coroutine
                }
            }
        }
        results["OneSignal Dispatchers"] = oneSignalTime

        // Print results
        println("\n=== Creation Performance Results ===")
        results.forEach { (name, time) ->
            println("$name: ${time}ms")
        }

        // OneSignal dispatchers should be fastest (reusing existing pool)
        oneSignalTime shouldBeLessThan threadCreationTime
        oneSignalTime shouldBeLessThan dispatcherCreationTime
    }

    test("resource cleanup comparison") {
        // Skip performance tests unless explicitly enabled
        if (System.getenv("RUN_PERFORMANCE_TESTS") != "true") {
            println("Skipping performance test - set RUN_PERFORMANCE_TESTS=true to run")
            return@test
        }
        val numberOfOperations = 100
        val initialThreads = Thread.activeCount()

        // Test 1: Individual Threads (should create many threads)
        repeat(numberOfOperations) { i ->
            Thread {
                Thread.sleep(50)
            }.start()
        }
        Thread.sleep(200) // Wait for completion
        val afterIndividualThreads = Thread.activeCount()

        // Test 2: Dispatcher (should reuse threads)
        val executor = Executors.newFixedThreadPool(2, ThreadFactory { r ->
            Thread(r, "DispatcherThread-${System.nanoTime()}")
        })
        val dispatcher = executor.asCoroutineDispatcher()
        
        try {
            runBlocking {
                repeat(numberOfOperations) { i ->
                    launch(dispatcher) {
                        Thread.sleep(50)
                    }
                }
            }
        } finally {
            executor.shutdown()
        }
        val afterDispatcher = Thread.activeCount()

        // Test 3: OneSignal Dispatchers (should reuse threads)
        runBlocking {
            repeat(numberOfOperations) { i ->
                OneSignalDispatchers.launchOnIO {
                    Thread.sleep(50)
                }
            }
        }
        val afterOneSignal = Thread.activeCount()

        println("\n=== Resource Usage Results ===")
        println("Initial threads: $initialThreads")
        println("After individual threads: $afterIndividualThreads")
        println("After dispatcher: $afterDispatcher")
        println("After OneSignal dispatchers: $afterOneSignal")

        // Dispatcher should use fewer threads than individual threads
        afterDispatcher shouldBeLessThan afterIndividualThreads
        afterOneSignal shouldBeLessThan afterIndividualThreads
    }

    test("concurrent access performance") {
        // Skip performance tests unless explicitly enabled
        if (System.getenv("RUN_PERFORMANCE_TESTS") != "true") {
            println("Skipping performance test - set RUN_PERFORMANCE_TESTS=true to run")
            return@test
        }
        val numberOfConcurrentOperations = 50
        val results = mutableMapOf<String, Long>()

        // Test 1: Individual Threads with concurrent access
        val individualTime = measureTime {
            val threads = mutableListOf<Thread>()
            repeat(numberOfConcurrentOperations) { i ->
                val thread = Thread {
                    Thread.sleep(20)
                }
                threads.add(thread)
                thread.start()
            }
            threads.forEach { it.join() }
        }
        results["Individual Threads"] = individualTime

        // Test 2: Dispatcher with concurrent access
        val dispatcherTime = measureTime {
            val executor = Executors.newFixedThreadPool(2, ThreadFactory { r ->
                Thread(r, "DispatcherThread-${System.nanoTime()}")
            })
            val dispatcher = executor.asCoroutineDispatcher()
            
            try {
                runBlocking {
                    repeat(numberOfConcurrentOperations) { i ->
                        launch(dispatcher) {
                            Thread.sleep(20)
                        }
                    }
                }
            } finally {
                executor.shutdown()
            }
        }
        results["Dispatcher"] = dispatcherTime

        // Test 3: OneSignal Dispatchers with concurrent access
        val oneSignalTime = measureTime {
            runBlocking {
                repeat(numberOfConcurrentOperations) { i ->
                    OneSignalDispatchers.launchOnIO {
                        Thread.sleep(20)
                    }
                }
            }
        }
        results["OneSignal Dispatchers"] = oneSignalTime

        // Print results
        println("\n=== Concurrent Access Performance Results ===")
        results.forEach { (name, time) ->
            println("$name: ${time}ms")
        }

        // Dispatcher should handle concurrent access better
        dispatcherTime shouldBeLessThan individualTime
        oneSignalTime shouldBeLessThan individualTime
    }
})

private fun measureTime(block: () -> Unit): Long {
    val startTime = System.currentTimeMillis()
    block()
    val endTime = System.currentTimeMillis()
    return endTime - startTime
}

private fun getUsedMemory(): Long {
    val runtime = Runtime.getRuntime()
    return (runtime.totalMemory() - runtime.freeMemory()) / 1024 // Convert to KB
}
