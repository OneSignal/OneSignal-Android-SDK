package com.onesignal.common.threading

import androidx.annotation.VisibleForTesting
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
 */
internal object OneSignalDispatchers {
    // Optimized pool sizes based on CPU cores and workload analysis
    private const val IO_CORE_POOL_SIZE = 2 // Increased for better concurrency
    private const val IO_MAX_POOL_SIZE = 3 // Increased for better concurrency
    private const val DEFAULT_CORE_POOL_SIZE = 2 // Optimal for CPU operations
    private const val DEFAULT_MAX_POOL_SIZE = 3 // Slightly larger for CPU operations
    private const val KEEP_ALIVE_TIME_SECONDS =
        30L // Keep threads alive longer to reduce recreation
    private const val QUEUE_CAPACITY =
        10 // Small queue that allows up to 10 tasks to wait in queue when all threads are busy
    internal const val BASE_THREAD_NAME = "OneSignal" // Base thread name prefix
    private const val IO_THREAD_NAME_PREFIX =
        "$BASE_THREAD_NAME-IO" // Thread name prefix for I/O operations
    private const val DEFAULT_THREAD_NAME_PREFIX =
        "$BASE_THREAD_NAME-Default" // Thread name prefix for CPU operations

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

    private val IOScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + IO)
    }

    private val DefaultScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Default)
    }

    @VisibleForTesting
    internal fun waitForDefaultScope() {
        runBlocking {
            // Wait for all active coroutines in DefaultScope to complete
            DefaultScope.coroutineContext[Job]?.children?.toList()?.forEach { child ->
                child.join()
            }
        }
    }

    fun launchOnIO(block: suspend () -> Unit) {
        IOScope.launch { block() }
    }

    fun launchOnDefault(block: suspend () -> Unit) {
        DefaultScope.launch { block() }
    }

    internal fun getPerformanceMetrics(): String {
        return try {
            """
            OneSignalDispatchers Performance Metrics:
            - IO Pool: ${ioExecutor.activeCount}/${ioExecutor.corePoolSize} active/core threads
            - IO Queue: ${ioExecutor.queue.size} pending tasks
            - Default Pool: ${defaultExecutor.activeCount}/${defaultExecutor.corePoolSize} active/core threads
            - Default Queue: ${defaultExecutor.queue.size} pending tasks
            - Total completed tasks: ${ioExecutor.completedTaskCount + defaultExecutor.completedTaskCount}
            - Memory usage: ~${(ioExecutor.activeCount + defaultExecutor.activeCount) * 1024}KB (thread stacks, ~1MB each)
            """.trimIndent()
        } catch (e: Exception) {
            "OneSignalDispatchers not initialized or using fallback dispatchers ${e.message}"
        }
    }

    internal fun getStatus(): String {
        val ioExecutorStatus =
            try {
                if (ioExecutor.isShutdown) "Shutdown" else "Active"
            } catch (e: Exception) {
                "ioExecutor Not initialized ${e.message ?: "Unknown error"}"
            }

        val defaultExecutorStatus =
            try {
                if (defaultExecutor.isShutdown) "Shutdown" else "Active"
            } catch (e: Exception) {
                "defaultExecutor Not initialized ${e.message ?: "Unknown error"}"
            }

        val ioScopeStatus =
            try {
                if (IOScope.isActive) "Active" else "Cancelled"
            } catch (e: Exception) {
                "IOScope Not initialized ${e.message ?: "Unknown error"}"
            }

        val defaultScopeStatus =
            try {
                if (DefaultScope.isActive) "Active" else "Cancelled"
            } catch (e: Exception) {
                "DefaultScope Not initialized ${e.message ?: "Unknown error"}"
            }

        return """
            OneSignalDispatchers Status:
            - IO Executor: $ioExecutorStatus
            - Default Executor: $defaultExecutorStatus
            - IO Scope: $ioScopeStatus
            - Default Scope: $defaultScopeStatus
            """.trimIndent()
    }
}
