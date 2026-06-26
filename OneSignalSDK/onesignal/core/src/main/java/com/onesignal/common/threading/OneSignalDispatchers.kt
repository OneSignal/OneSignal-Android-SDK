package com.onesignal.common.threading

import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
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

    /**
     * Holds one generation of executors, dispatchers, and scopes. Everything stays lazily
     * initialized (so production cold-start only pays for what the first caller actually uses),
     * but bundling them lets the test-only [resetForTest] hook atomically swap in a clean
     * generation and tear down the old one — including [shutdownNow] to interrupt worker threads
     * that are parked in non-cancellable JVM waits (e.g. `CountDownLatch.await()` from a prior
     * spec), which a plain coroutine cancellation cannot free.
     */
    private class Pools {
        val ioExecutorLazy =
            lazy {
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
        val ioExecutor: ThreadPoolExecutor get() = ioExecutorLazy.value

        /** Single-thread executor for order-sensitive lifecycle work (focus / unfocus handlers). */
        val serialIOExecutorLazy =
            lazy {
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
        val serialIOExecutor: ExecutorService get() = serialIOExecutorLazy.value

        val defaultExecutorLazy =
            lazy {
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
        val defaultExecutor: ThreadPoolExecutor get() = defaultExecutorLazy.value

        // Dispatchers - also lazy initialized
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
                // Fall back to a limitedParallelism(1) view of Dispatchers.IO so submissions stay serialized.
                Logging.error("OneSignalDispatchers: Using fallback serialized Dispatchers.IO: ${e.message}")
                @Suppress("OPT_IN_USAGE")
                Dispatchers.IO.limitedParallelism(1)
            }
        }

        val ioScopeLazy = lazy { CoroutineScope(SupervisorJob() + IO) }
        val IOScope: CoroutineScope get() = ioScopeLazy.value

        val defaultScopeLazy = lazy { CoroutineScope(SupervisorJob() + Default) }
        val DefaultScope: CoroutineScope get() = defaultScopeLazy.value

        val serialIOScopeLazy = lazy { CoroutineScope(SupervisorJob() + SerialIO) }
        val SerialIOScope: CoroutineScope get() = serialIOScopeLazy.value

        /**
         * Cancel scopes and forcibly stop executors for this generation. Only touches what was
         * actually initialized so we never spin up a pool just to tear it down. [shutdownNow]
         * interrupts parked worker threads so they can't outlive the spec that created them.
         */
        @Suppress("TooGenericExceptionCaught")
        fun shutdown() {
            if (ioScopeLazy.isInitialized()) runCatching { ioScopeLazy.value.cancel() }
            if (defaultScopeLazy.isInitialized()) runCatching { defaultScopeLazy.value.cancel() }
            if (serialIOScopeLazy.isInitialized()) runCatching { serialIOScopeLazy.value.cancel() }
            if (ioExecutorLazy.isInitialized()) runCatching { ioExecutorLazy.value.shutdownNow() }
            if (defaultExecutorLazy.isInitialized()) runCatching { defaultExecutorLazy.value.shutdownNow() }
            if (serialIOExecutorLazy.isInitialized()) runCatching { serialIOExecutorLazy.value.shutdownNow() }
        }
    }

    @Volatile
    private var pools = Pools()

    // Dispatchers and scopes delegate to the current generation so [resetForTest] can swap them.
    val IO: CoroutineDispatcher get() = pools.IO

    val Default: CoroutineDispatcher get() = pools.Default

    val SerialIO: CoroutineDispatcher get() = pools.SerialIO

    fun launchOnIO(block: suspend () -> Unit): Job {
        return pools.IOScope.launch { block() }
    }

    fun launchOnDefault(block: suspend () -> Unit): Job {
        return pools.DefaultScope.launch { block() }
    }

    /** Launches [block] on the single-thread serial IO dispatcher (FIFO across all callers). */
    fun launchOnSerialIO(block: suspend () -> Unit): Job {
        return pools.SerialIOScope.launch { block() }
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
     * the calling thread, blocking the main thread for many seconds on cold start.
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
     *
     * **Best-effort, not a hard guarantee.** Because [prewarm] is fire-and-forget, a caller that
     * dispatches immediately afterward can still win the lazy-init race and pay construction on
     * its own thread. The fix relies on placing [prewarm] at cold-start entry points where there
     * is meaningful lead time (e.g. a `goAsync()` handoff or `initWithContext` work) before the
     * first `suspendify*` / `launchOn*` dispatch.
     *
     * [suspendifyOnIO], [suspendifyOnDefault], [launchOnIO], [launchOnDefault], and
     * [suspendifyOnSerialIO] always route through [IO] / [Default] / [SerialIO], so [prewarm]
     * benefits the very first real dispatch from any of them.
     *
     * The known main-thread cold-start entry points:
     * | Entry point | Class |
     * |---|---|
     * | `initWithContext` / `initWithContextSuspend` | `OneSignalImp` |
     * | `onStartJob` | `core.services.SyncJobService` |
     * | `onReceive` | `FCMBroadcastReceiver`, `NotificationDismissReceiver`, `BootUpReceiver`, `UpgradeReceiver` |
     * | `onMessage` / registration callbacks | `ADMMessageHandler`, `ADMMessageHandlerJob` |
     * | `onNewToken` / `onMessageReceived` | `OneSignalHmsEventBridge` |
     * | `processIntent` / `processOpen` | `NotificationOpenedActivityBase`, `NotificationOpenedActivityHMS` |
     *
     * When adding a new cold-start entry point (receiver, job, activity trampoline, push bridge),
     * call [prewarm] at the top of it before the first dispatch.
     */
    @Suppress("TooGenericExceptionCaught")
    fun prewarm() {
        if (prewarmStarted) return
        synchronized(prewarmLock) {
            if (prewarmStarted) return
            prewarmStarted = true
        }
        try {
            val prewarmThread = Thread(
                {
                    try {
                        // Each launch* call below triggers the corresponding lazy chain
                        // (executor -> dispatcher -> scope) and submits an empty coroutine,
                        // which forces the worker thread(s) to start as well.
                        launchOnIO { /* warm IOScope + ioExecutor */ }
                        launchOnDefault { /* warm DefaultScope + defaultExecutor */ }
                        launchOnSerialIO { /* warm SerialIOScope + serialIOExecutor */ }
                    } catch (e: Throwable) {
                        synchronized(prewarmLock) { prewarmStarted = false }
                        Logging.warn("OneSignalDispatchers.prewarm failed: ${e.message}", e)
                    }
                },
                "$BASE_THREAD_NAME-prewarm",
            )
            prewarmThread.isDaemon = true
            prewarmThread.priority = Thread.NORM_PRIORITY - 2
            prewarmThread.start()
        } catch (t: Throwable) {
            // Constructing, configuring, or starting the daemon can itself fail before the body
            // ever runs (e.g. OutOfMemoryError "unable to create new native thread", a
            // SecurityManager denial, or InternalError). Swallow it so a prewarm failure never
            // propagates onto the cold-start entry point's caller thread, and reset the guard so a
            // later entry point can retry. The dispatchers keep their lazy fallbacks, so the only
            // cost of a failed prewarm is that the first real dispatch pays the original
            // construction cost.
            synchronized(prewarmLock) { prewarmStarted = false }
            Logging.warn("OneSignalDispatchers.prewarm failed to start daemon: ${t.message}", t)
        }
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

    /**
     * Test-only hook to clear all in-flight and queued work between specs.
     *
     * Every `suspendify*` / `launchOn*` now routes unconditionally through this single
     * process-wide object (bounded pools: IO core=2/max=3, Default core=2/max=3, queue cap 200).
     * In the full unit-test suite, background work launched by one spec can outlive it and
     * accumulate, saturating the pools and `LinkedBlockingQueue`. Worse, a spec can leave a worker
     * thread parked in a non-cancellable JVM wait (e.g. a `CountDownLatch.await()` that never gets
     * released because the test asserted/failed first); a plain coroutine cancellation cannot free
     * such a thread, so it permanently starves the small pool. Later specs that use the real pool
     * (e.g. HttpClientTests' `launchOnIO {…}.join()`, OperationRepo, OtelIdResolver) then see
     * `launchOnIO` rejected/cancelled and observe null results.
     *
     * This atomically swaps in a fresh [Pools] generation and tears the old one down —
     * cancelling its scopes and calling `shutdownNow()` to interrupt parked workers. Safe to call
     * even when this object is `mockkObject`-mocked: the affected helpers stub only
     * `prewarm`/`launchOn*`/`suspendify*`, so this real method still runs. Not part of any public
     * contract.
     */
    @Suppress("TooGenericExceptionCaught")
    internal fun resetForTest() {
        val old = pools
        pools = Pools()
        try {
            old.shutdown()
        } catch (e: Exception) {
            Logging.error("OneSignalDispatchers.resetForTest failed: ${e.message}", e)
        }
        resetPrewarmForTest()
    }

    internal fun getPerformanceMetrics(): String {
        return try {
            val current = pools
            val serialQueueSize =
                (current.serialIOExecutor as? ThreadPoolExecutor)?.queue?.size?.toString() ?: "n/a"
            val serialCompleted =
                (current.serialIOExecutor as? ThreadPoolExecutor)?.completedTaskCount ?: 0L
            """
            OneSignalDispatchers Performance Metrics:
            - IO Pool: ${current.ioExecutor.activeCount}/${current.ioExecutor.corePoolSize} active/core threads
            - IO Queue: ${current.ioExecutor.queue.size} pending tasks
            - Default Pool: ${current.defaultExecutor.activeCount}/${current.defaultExecutor.corePoolSize} active/core threads
            - Default Queue: ${current.defaultExecutor.queue.size} pending tasks
            - SerialIO Queue: $serialQueueSize pending tasks
            - Total completed tasks: ${current.ioExecutor.completedTaskCount + current.defaultExecutor.completedTaskCount + serialCompleted}
            - Memory usage: ~${(current.ioExecutor.activeCount + current.defaultExecutor.activeCount + 1) * 1024}KB (thread stacks, ~1MB each)
            """.trimIndent()
        } catch (e: Exception) {
            "OneSignalDispatchers not initialized or using fallback dispatchers ${e.message}"
        }
    }

    internal fun getStatus(): String {
        val current = pools
        return """
            OneSignalDispatchers Status:
            - IO Executor: ${executorStatus("ioExecutor") { current.ioExecutor.isShutdown }}
            - Default Executor: ${executorStatus("defaultExecutor") { current.defaultExecutor.isShutdown }}
            - SerialIO Executor: ${executorStatus("serialIOExecutor") { current.serialIOExecutor.isShutdown }}
            - IO Scope: ${scopeStatus("IOScope") { current.IOScope.isActive }}
            - Default Scope: ${scopeStatus("DefaultScope") { current.DefaultScope.isActive }}
            - SerialIO Scope: ${scopeStatus("SerialIOScope") { current.SerialIOScope.isActive }}
        """.trimIndent()
    }

    // internal so tests can exercise the failure branch (when `isShutdown()` itself throws,
    // which happens when the lazy initializer threw and re-throws on every access).
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
