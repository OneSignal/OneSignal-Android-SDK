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
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

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
@Suppress("TooManyFunctions", "StringLiteralDuplication")
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
    private const val TEST_READY_TIMEOUT_MS = 2_000L
    private const val TEST_READY_POLL_MS = 5L
    internal const val BASE_THREAD_NAME = "OneSignal" // Base thread name prefix
    private const val IO_THREAD_NAME_PREFIX =
        "$BASE_THREAD_NAME-IO" // Thread name prefix for I/O operations
    private const val DEFAULT_THREAD_NAME_PREFIX =
        "$BASE_THREAD_NAME-Default" // Thread name prefix for CPU operations
    private const val SERIAL_IO_THREAD_NAME =
        "$BASE_THREAD_NAME-SerialIO" // Single, named thread for order-sensitive work
    private const val INGRESS_THREAD_NAME = "$BASE_THREAD_NAME-Ingress"

    @Volatile
    internal var beforeLaneCreateForTest: ((String) -> Unit)? = null

    @Volatile
    internal var beforeFallbackCreateForTest: ((String) -> Unit)? = null

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

    private enum class Lane {
        IO,
        DEFAULT,
        SERIAL_IO,
        INGRESS,
    }

    private enum class LaneState {
        COLD,
        STARTING,
        DRAINING,
        READY,
        CLOSED,
    }

    private class LaneTarget(
        val dispatcher: CoroutineDispatcher,
        val executor: ThreadPoolExecutor?,
    ) {
        fun close() {
            executor?.shutdownNow()
        }
    }

    private data class LaneConfig(
        val corePoolSize: Int,
        val maxPoolSize: Int,
        val threadName: String,
        val priority: Int = Thread.NORM_PRIORITY,
    )

    /**
     * A stable dispatcher that queues work until its backing executor has been built and
     * prestarted on the bootstrap thread. CoroutineScope.launch can therefore return its real Job
     * without forcing executor construction or waiting on a lazy lock on the caller thread.
     */
    private class GateDispatcher(
        private val lane: Lane,
        private val requestBootstrap: (GateDispatcher) -> Unit,
    ) : CoroutineDispatcher() {
        private val lock = Any()
        private val pending = ArrayDeque<Pair<CoroutineContext, Runnable>>()
        private var state = LaneState.COLD
        private var target: LaneTarget? = null

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            var readyDispatcher: CoroutineDispatcher? = null
            var shouldBootstrap = false
            var rejected = false

            synchronized(lock) {
                when (state) {
                    LaneState.COLD -> {
                        pending.addLast(context to block)
                        state = LaneState.STARTING
                        shouldBootstrap = true
                    }
                    LaneState.STARTING, LaneState.DRAINING -> pending.addLast(context to block)
                    LaneState.READY -> readyDispatcher = target!!.dispatcher
                    LaneState.CLOSED -> rejected = true
                }
            }

            when {
                shouldBootstrap -> requestBootstrap(this)
                readyDispatcher != null -> dispatchSafely(readyDispatcher!!, context, block)
                rejected -> cancelAndComplete(context, block, "OneSignal dispatcher generation is closed")
            }
        }

        fun requestWarmup() {
            val shouldBootstrap =
                synchronized(lock) {
                    if (state == LaneState.COLD) {
                        state = LaneState.STARTING
                        true
                    } else {
                        false
                    }
                }
            if (shouldBootstrap) requestBootstrap(this)
        }

        @Suppress("TooGenericExceptionCaught")
        fun initialize() {
            createTargetOrFallback()?.let(::drainPending)
        }

        @Suppress("TooGenericExceptionCaught")
        private fun createTargetOrFallback(): LaneTarget? =
            try {
                createTarget(lane)
            } catch (e: Exception) {
                Logging.warn("OneSignalDispatchers: Using fallback for $lane lane: ${e.message}", e)
                try {
                    createFallbackTarget(lane)
                } catch (t: Throwable) {
                    Logging.warn("OneSignalDispatchers: Fallback failed for $lane lane: ${t.message}", t)
                    failPending("OneSignal $lane dispatcher fallback failed")
                    null
                }
            } catch (t: Throwable) {
                Logging.warn("OneSignalDispatchers: Failed to initialize $lane lane: ${t.message}", t)
                failPending("OneSignal $lane dispatcher failed to initialize")
                null
            }

        private fun drainPending(newTarget: LaneTarget) {
            while (true) {
                val next =
                    synchronized(lock) {
                        if (state == LaneState.CLOSED) {
                            null
                        } else {
                            state = LaneState.DRAINING
                            pending.removeFirstOrNull().also {
                                if (it == null) {
                                    target = newTarget
                                    state = LaneState.READY
                                }
                            }
                        }
                    }

                if (next == null) {
                    val closed = synchronized(lock) { state == LaneState.CLOSED }
                    if (closed) newTarget.close()
                    return
                }
                dispatchSafely(newTarget.dispatcher, next.first, next.second)
            }
        }

        fun close() {
            val queued: List<Pair<CoroutineContext, Runnable>>
            val oldTarget: LaneTarget?
            synchronized(lock) {
                if (state == LaneState.CLOSED) return
                state = LaneState.CLOSED
                queued = pending.toList()
                pending.clear()
                oldTarget = target
                target = null
            }
            queued.forEach {
                cancelAndComplete(it.first, it.second, "OneSignal dispatcher generation was reset")
            }
            oldTarget?.close()
        }

        fun bootstrapFailed() {
            failPending("OneSignal dispatcher bootstrap thread failed to start")
        }

        fun status(): String =
            synchronized(lock) {
                when (state) {
                    LaneState.READY -> "Active"
                    LaneState.CLOSED -> "Shutdown"
                    else -> state.name.lowercase().replaceFirstChar { it.uppercase() }
                }
            }

        fun executor(): ThreadPoolExecutor? = synchronized(lock) { target?.executor }

        private fun failPending(reason: String) {
            val queued =
                synchronized(lock) {
                    val copy = pending.toList()
                    pending.clear()
                    state = LaneState.COLD
                    copy
                }
            queued.forEach { cancelAndComplete(it.first, it.second, reason) }
        }

        private fun dispatchSafely(
            dispatcher: CoroutineDispatcher,
            context: CoroutineContext,
            block: Runnable,
        ) {
            try {
                dispatcher.dispatch(context, block)
            } catch (e: RuntimeException) {
                Logging.error("OneSignalDispatchers: $lane dispatch rejected: ${e.message}", e)
                cancelAndComplete(context, block, "OneSignal $lane dispatch rejected")
            }
        }

        private fun cancelAndComplete(
            context: CoroutineContext,
            block: Runnable,
            reason: String,
        ) {
            val job = context[Job] ?: return
            job.cancel(CancellationException(reason))
            block.run()
        }
    }

    private class BootstrapCoordinator {
        private val queue = ConcurrentLinkedQueue<GateDispatcher>()
        private val running = AtomicBoolean(false)

        fun request(gate: GateDispatcher) {
            queue.add(gate)
            startIfNeeded()
        }

        @Suppress("TooGenericExceptionCaught")
        private fun startIfNeeded() {
            if (!running.compareAndSet(false, true)) return
            try {
                Thread(
                    ::runLoop,
                    "$BASE_THREAD_NAME-bootstrap",
                ).apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY - 2
                    start()
                }
            } catch (t: Throwable) {
                running.set(false)
                Logging.warn("OneSignalDispatchers: Failed to start bootstrap thread: ${t.message}", t)
                while (true) {
                    val gate = queue.poll() ?: break
                    gate.bootstrapFailed()
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun runLoop() {
            try {
                while (true) {
                    val gate = queue.poll() ?: return
                    try {
                        gate.initialize()
                    } catch (t: Throwable) {
                        Logging.warn("OneSignalDispatchers: Bootstrap failed for a lane: ${t.message}", t)
                        gate.bootstrapFailed()
                    }
                }
            } finally {
                running.set(false)
                if (queue.isNotEmpty()) startIfNeeded()
            }
        }
    }

    private class Pools {
        private val coordinator = BootstrapCoordinator()
        val IO = GateDispatcher(Lane.IO, coordinator::request)
        val Default = GateDispatcher(Lane.DEFAULT, coordinator::request)
        val SerialIO = GateDispatcher(Lane.SERIAL_IO, coordinator::request)
        val Ingress = GateDispatcher(Lane.INGRESS, coordinator::request)
        val IOScope = CoroutineScope(SupervisorJob() + IO)
        val DefaultScope = CoroutineScope(SupervisorJob() + Default)
        val SerialIOScope = CoroutineScope(SupervisorJob() + SerialIO)
        val IngressScope = CoroutineScope(SupervisorJob() + Ingress)
        private val gates = listOf(IO, Default, SerialIO, Ingress)
        private val scopes = listOf(IOScope, DefaultScope, SerialIOScope, IngressScope)

        fun prewarm() {
            gates.forEach { it.requestWarmup() }
        }

        fun shutdown() {
            scopes.forEach { it.cancel() }
            gates.forEach { it.close() }
        }
    }

    private fun createTarget(lane: Lane): LaneTarget {
        beforeLaneCreateForTest?.invoke(lane.name)
        val config = lane.config()
        val executor =
            ThreadPoolExecutor(
                config.corePoolSize,
                config.maxPoolSize,
                KEEP_ALIVE_TIME_SECONDS,
                TimeUnit.SECONDS,
                LinkedBlockingQueue(QUEUE_CAPACITY),
                OptimizedThreadFactory(config.threadName, config.priority),
            )
        executor.allowCoreThreadTimeOut(false)
        executor.prestartAllCoreThreads()
        return LaneTarget(executor.asCoroutineDispatcher(), executor)
    }

    private fun Lane.config(): LaneConfig =
        when (this) {
            Lane.IO ->
                LaneConfig(
                    IO_CORE_POOL_SIZE,
                    IO_MAX_POOL_SIZE,
                    IO_THREAD_NAME_PREFIX,
                    Thread.NORM_PRIORITY - 1,
                )
            Lane.DEFAULT ->
                LaneConfig(
                    DEFAULT_CORE_POOL_SIZE,
                    DEFAULT_MAX_POOL_SIZE,
                    DEFAULT_THREAD_NAME_PREFIX,
                )
            Lane.SERIAL_IO ->
                LaneConfig(
                    1,
                    1,
                    SERIAL_IO_THREAD_NAME,
                    Thread.NORM_PRIORITY - 1,
                )
            Lane.INGRESS ->
                LaneConfig(
                    1,
                    1,
                    INGRESS_THREAD_NAME,
                    Thread.NORM_PRIORITY - 1,
                )
        }

    private fun createFallbackTarget(lane: Lane): LaneTarget {
        beforeFallbackCreateForTest?.invoke(lane.name)
        val dispatcher =
            when (lane) {
                Lane.IO -> Dispatchers.IO
                Lane.DEFAULT -> Dispatchers.Default
                Lane.SERIAL_IO -> {
                    @Suppress("OPT_IN_USAGE")
                    Dispatchers.IO.limitedParallelism(1)
                }
                Lane.INGRESS -> {
                    @Suppress("OPT_IN_USAGE")
                    Dispatchers.IO.limitedParallelism(1)
                }
            }
        dispatcher.dispatch(kotlin.coroutines.EmptyCoroutineContext, Runnable {})
        return LaneTarget(dispatcher, null)
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

    /** Launches short durable-ingress work on a pool isolated from general SDK I/O. */
    fun launchOnIngress(block: suspend () -> Unit): Job {
        return pools.IngressScope.launch { block() }
    }

    @Volatile
    private var prewarmStarted = false
    private val prewarmLock = Any()

    /**
     * Requests asynchronous initialization and worker prestart for every lane. Dispatch correctness
     * does not depend on this call: a cold first dispatch queues behind the same bootstrap gate and
     * returns without constructing or waiting for a pool on its caller thread.
     */
    @Suppress("TooGenericExceptionCaught")
    fun prewarm() {
        if (prewarmStarted) return
        synchronized(prewarmLock) {
            if (prewarmStarted) return
            prewarmStarted = true
        }
        try {
            pools.prewarm()
        } catch (t: Throwable) {
            synchronized(prewarmLock) { prewarmStarted = false }
            Logging.warn("OneSignalDispatchers.prewarm failed: ${t.message}", t)
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
        beforeLaneCreateForTest = null
        beforeFallbackCreateForTest = null
    }

    @Suppress("ComplexMethod")
    internal fun getPerformanceMetrics(): String {
        val current = pools
        val io = current.IO.executor()
        val default = current.Default.executor()
        val serial = current.SerialIO.executor()
        val ingress = current.Ingress.executor()
        return """
            OneSignalDispatchers Performance Metrics:
            - IO Pool: ${io?.let { "${it.activeCount}/${it.corePoolSize}" } ?: "n/a"} active/core threads
            - IO Queue: ${io?.queue?.size ?: "n/a"} pending tasks
            - Default Pool: ${default?.let { "${it.activeCount}/${it.corePoolSize}" } ?: "n/a"} active/core threads
            - Default Queue: ${default?.queue?.size ?: "n/a"} pending tasks
            - SerialIO Queue: ${serial?.queue?.size ?: "n/a"} pending tasks
            - Ingress Queue: ${ingress?.queue?.size ?: "n/a"} pending tasks
            - Total completed tasks: ${(io?.completedTaskCount ?: 0L) + (default?.completedTaskCount ?: 0L) + (serial?.completedTaskCount ?: 0L) + (ingress?.completedTaskCount ?: 0L)}
            - Memory usage: ~${((io?.activeCount ?: 0) + (default?.activeCount ?: 0) + (serial?.activeCount ?: 0) + (ingress?.activeCount ?: 0)) * 1024}KB (thread stacks, ~1MB each)
        """.trimIndent()
    }

    internal fun getStatus(): String {
        val current = pools
        return """
            OneSignalDispatchers Status:
            - IO Executor: ${current.IO.status()}
            - Default Executor: ${current.Default.status()}
            - SerialIO Executor: ${current.SerialIO.status()}
            - Ingress Executor: ${current.Ingress.status()}
            - IO Scope: ${scopeStatus("IOScope") { current.IOScope.isActive }}
            - Default Scope: ${scopeStatus("DefaultScope") { current.DefaultScope.isActive }}
            - SerialIO Scope: ${scopeStatus("SerialIOScope") { current.SerialIOScope.isActive }}
            - Ingress Scope: ${scopeStatus("IngressScope") { current.IngressScope.isActive }}
        """.trimIndent()
    }

    internal fun awaitReadyForTest(timeoutMs: Long = TEST_READY_TIMEOUT_MS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = pools
            if (listOf(current.IO, current.Default, current.SerialIO, current.Ingress).all { it.status() == "Active" }) {
                return true
            }
            Thread.sleep(TEST_READY_POLL_MS)
        }
        return false
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
