package com.onesignal.common.threading

import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Optimized threading manager for the OneSignal SDK.
 *
 * Performance optimizations:
 * - Lazy initialization to reduce startup overhead
 * - Custom thread pools for both IO and Default operations
 * - Optimized thread pool configuration (smaller pools)
 * - Small bounded queues (10 tasks) to prevent memory bloat
 * - Reduced context switching overhead
 * - Efficient thread management with controlled resource usage
 *
 * Made public to allow mocking in tests via IOMockHelper.
 */
object OneSignalDispatchers {
    // Optimized pool sizes based on CPU cores and workload analysis
    private const val IO_CORE_POOL_SIZE = 2 // Increased for better concurrency
    private const val IO_MAX_POOL_SIZE = 3 // Increased for better concurrency
    private const val DEFAULT_CORE_POOL_SIZE = 2 // Optimal for CPU operations
    private const val DEFAULT_MAX_POOL_SIZE = 3 // Slightly larger for CPU operations
    private const val KEEP_ALIVE_TIME_SECONDS =
        30L // Keep threads alive longer to reduce recreation
    private const val QUEUE_CAPACITY =
        200 // Increased to handle more queued operations during init, while still preventing memory bloat
    internal const val BASE_THREAD_NAME = "OneSignal" // Base thread name prefix
    private const val IO_THREAD_NAME_PREFIX =
        "$BASE_THREAD_NAME-IO" // Thread name prefix for I/O operations
    private const val DEFAULT_THREAD_NAME_PREFIX =
        "$BASE_THREAD_NAME-Default" // Thread name prefix for CPU operations
    private const val SERIAL_IO_THREAD_NAME =
        "$BASE_THREAD_NAME-SerialIO" // Single, named thread for order-sensitive work

    private class OptimizedThreadFactory(
        private val namePrefix: String,
        private val priority: Int = Thread.NORM_PRIORITY,
    ) : ThreadFactory {
        private val threadNumber = AtomicInteger(1)

        override fun newThread(r: Runnable): Thread {
            val thread = Thread(r, "$namePrefix-${threadNumber.getAndIncrement()}")
            thread.isDaemon = true
            thread.priority = priority
            return thread
        }
    }

    private val ioExecutor: ThreadPoolExecutor by lazy {
        try {
            ThreadPoolExecutor(
                IO_CORE_POOL_SIZE,
                IO_MAX_POOL_SIZE,
                KEEP_ALIVE_TIME_SECONDS,
                TimeUnit.SECONDS,
                LinkedBlockingQueue(QUEUE_CAPACITY),
                OptimizedThreadFactory(
                    namePrefix = IO_THREAD_NAME_PREFIX,
                    priority = Thread.NORM_PRIORITY - 1,
                    // Slightly lower priority for I/O tasks
                ),
            ).apply {
                allowCoreThreadTimeOut(false) // Keep core threads alive
            }
        } catch (e: Exception) {
            Logging.error("OneSignalDispatchers: Failed to create IO executor: ${e.message}")
            throw e // Let the dispatcher fallback handle this
        }
    }

    /**
     * Single-thread executor for order-sensitive work (e.g. lifecycle event handlers that
     * must run in the order the events fired on the main thread). Submission order on the
     * main thread equals execution order on this thread, so callers don't need their own
     * synchronization to preserve sequence — they only need to capture any time-sensitive
     * state (timestamps, current state snapshots) on the caller before dispatching.
     */
    private val serialIOExecutor: ExecutorService by lazy {
        try {
            Executors.newSingleThreadExecutor(
                OptimizedThreadFactory(
                    namePrefix = SERIAL_IO_THREAD_NAME,
                    priority = Thread.NORM_PRIORITY - 1,
                ),
            )
        } catch (e: Exception) {
            Logging.error("OneSignalDispatchers: Failed to create SerialIO executor: ${e.message}")
            throw e
        }
    }

    private val defaultExecutor: ThreadPoolExecutor by lazy {
        try {
            ThreadPoolExecutor(
                DEFAULT_CORE_POOL_SIZE,
                DEFAULT_MAX_POOL_SIZE,
                KEEP_ALIVE_TIME_SECONDS,
                TimeUnit.SECONDS,
                LinkedBlockingQueue(QUEUE_CAPACITY),
                OptimizedThreadFactory(DEFAULT_THREAD_NAME_PREFIX),
            ).apply {
                allowCoreThreadTimeOut(false) // Keep core threads alive
            }
        } catch (e: Exception) {
            Logging.error("OneSignalDispatchers: Failed to create Default executor: ${e.message}")
            throw e // Let the dispatcher fallback handle this
        }
    }

    // Dispatchers and scopes - also lazy initialized
    val IO: CoroutineDispatcher by lazy {
        try {
            ioExecutor.asCoroutineDispatcher()
        } catch (e: Exception) {
            Logging.error("OneSignalDispatchers: Using fallback Dispatchers.IO dispatcher: ${e.message}")
            Dispatchers.IO
        }
    }

    val Default: CoroutineDispatcher by lazy {
        try {
            defaultExecutor.asCoroutineDispatcher()
        } catch (e: Exception) {
            Logging.error("OneSignalDispatchers: Using fallback Dispatchers.Default dispatcher: ${e.message}")
            Dispatchers.Default
        }
    }

    val SerialIO: CoroutineDispatcher by lazy {
        try {
            serialIOExecutor.asCoroutineDispatcher()
        } catch (e: Exception) {
            // Fall back to a limited-parallelism view of Dispatchers.IO. A single shared view
            // serializes our submissions even when our own executor failed to start.
            Logging.error("OneSignalDispatchers: Using fallback serialized Dispatchers.IO: ${e.message}")
            @Suppress("OPT_IN_USAGE")
            Dispatchers.IO.limitedParallelism(1)
        }
    }

    private val IOScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + IO)
    }

    private val DefaultScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Default)
    }

    private val SerialIOScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + SerialIO)
    }

    fun launchOnIO(block: suspend () -> Unit): Job {
        return IOScope.launch { block() }
    }

    fun launchOnDefault(block: suspend () -> Unit): Job {
        return DefaultScope.launch { block() }
    }

    /**
     * Launches [block] on the dedicated serial IO thread. Tasks submitted from any thread
     * run one-at-a-time in submission order, which makes this the right entry point for
     * order-sensitive lifecycle work (e.g. focus/unfocus handlers).
     */
    fun launchOnSerialIO(block: suspend () -> Unit): Job {
        return SerialIOScope.launch { block() }
    }

    @Volatile
    private var prewarmStarted = false
    private val prewarmLock = Any()

    /**
     * Triggers the lazy initialization of [IO], [Default], and [SerialIO] (and their backing
     * executors, dispatchers, and scopes) on a short-lived background thread.
     *
     * Background:
     * The lazy `by lazy` properties below construct `ThreadPoolExecutor` instances and wrap them
     * in `asCoroutineDispatcher() + SupervisorJob() + CoroutineScope(...)`. Production OTel
     * shows that when the **first** caller of [launchOnIO] / [launchOnSerialIO] is on the main
     * thread (Activity-lifecycle handler, `JobService.onStartJob`, etc.), the construction cost
     * — which includes a `kotlinx.coroutines.BuildersKt.launch` that hits
     * `ThreadPoolExecutor.execute` and `LinkedBlockingQueue.offer` synchronously — is paid on
     * the calling thread, blocking the main thread for many seconds on cold start. See SDK-4507.
     *
     * Calling [prewarm] from a non-time-sensitive spot in [com.onesignal.OneSignal.initWithContext]
     * shifts that cost to a dedicated `OneSignal-prewarm` daemon thread, so the first
     * production caller — including main-thread lifecycle handlers — only pays the much cheaper
     * "submit work to an already-constructed executor" cost.
     *
     * Idempotent and fire-and-forget: a no-op on second and subsequent calls. Safe to invoke
     * from any thread; the heavy lifting always happens on the daemon thread we spawn here, not
     * on the caller. Failures are logged and swallowed because the executors retain their
     * existing fallback paths (e.g. `Dispatchers.IO.limitedParallelism(1)` for [SerialIO]) and a
     * failed prewarm will simply mean the first production caller pays the original cost.
     */
    fun prewarm() {
        if (prewarmStarted) return
        synchronized(prewarmLock) {
            if (prewarmStarted) return
            prewarmStarted = true
        }
        val prewarmThread = Thread(
            {
                try {
                    // Each launch* call below triggers the corresponding lazy chain
                    // (executor -> dispatcher -> scope) and submits an empty coroutine,
                    // which forces the worker thread(s) to start as well.
                    launchOnIO { /* warm IOScope + ioExecutor */ }
                    launchOnDefault { /* warm DefaultScope + defaultExecutor */ }
                    launchOnSerialIO { /* warm SerialIOScope + serialIOExecutor */ }
                } catch (e: Exception) {
                    Logging.warn("OneSignalDispatchers.prewarm failed: ${e.message}")
                }
            },
            "$BASE_THREAD_NAME-prewarm",
        )
        prewarmThread.isDaemon = true
        prewarmThread.priority = Thread.NORM_PRIORITY - 2
        prewarmThread.start()
    }

    /**
     * Test-only hook to reset [prewarmStarted] so different specs can exercise the
     * "first call wins" branch independently. Not part of any public contract.
     */
    internal fun resetPrewarmForTest() {
        synchronized(prewarmLock) {
            prewarmStarted = false
        }
    }

    internal fun getPerformanceMetrics(): String {
        return try {
            val serialQueueSize =
                (serialIOExecutor as? ThreadPoolExecutor)?.queue?.size?.toString() ?: "n/a"
            val serialCompleted =
                (serialIOExecutor as? ThreadPoolExecutor)?.completedTaskCount ?: 0L
            """
            OneSignalDispatchers Performance Metrics:
            - IO Pool: ${ioExecutor.activeCount}/${ioExecutor.corePoolSize} active/core threads
            - IO Queue: ${ioExecutor.queue.size} pending tasks
            - Default Pool: ${defaultExecutor.activeCount}/${defaultExecutor.corePoolSize} active/core threads
            - Default Queue: ${defaultExecutor.queue.size} pending tasks
            - SerialIO Queue: $serialQueueSize pending tasks
            - Total completed tasks: ${ioExecutor.completedTaskCount + defaultExecutor.completedTaskCount + serialCompleted}
            - Memory usage: ~${(ioExecutor.activeCount + defaultExecutor.activeCount + 1) * 1024}KB (thread stacks, ~1MB each)
            """.trimIndent()
        } catch (e: Exception) {
            "OneSignalDispatchers not initialized or using fallback dispatchers ${e.message}"
        }
    }

    internal fun getStatus(): String {
        return """
            OneSignalDispatchers Status:
            - IO Executor: ${executorStatus("ioExecutor") { ioExecutor.isShutdown }}
            - Default Executor: ${executorStatus("defaultExecutor") { defaultExecutor.isShutdown }}
            - SerialIO Executor: ${executorStatus("serialIOExecutor") { serialIOExecutor.isShutdown }}
            - IO Scope: ${scopeStatus("IOScope") { IOScope.isActive }}
            - Default Scope: ${scopeStatus("DefaultScope") { DefaultScope.isActive }}
            - SerialIO Scope: ${scopeStatus("SerialIOScope") { SerialIOScope.isActive }}
        """.trimIndent()
    }

    // Visible to tests so the failure branch (where `isShutdown()` itself throws — happens
    // when the underlying executor's lazy initializer threw and re-throws on every access)
    // can be exercised without forcing a real ThreadPoolExecutor to fail to construct, which
    // is impractical to trigger in a unit test.
    internal fun executorStatus(
        name: String,
        isShutdown: () -> Boolean,
    ): String =
        try {
            if (isShutdown()) "Shutdown" else "Active"
        } catch (e: Exception) {
            "$name $NOT_INITIALIZED ${e.message ?: UNKNOWN_ERROR}"
        }

    internal fun scopeStatus(
        name: String,
        isActive: () -> Boolean,
    ): String =
        try {
            if (isActive()) "Active" else "Cancelled"
        } catch (e: Exception) {
            "$name $NOT_INITIALIZED ${e.message ?: UNKNOWN_ERROR}"
        }

    private const val NOT_INITIALIZED = "Not initialized"
    private const val UNKNOWN_ERROR = "Unknown error"
}
