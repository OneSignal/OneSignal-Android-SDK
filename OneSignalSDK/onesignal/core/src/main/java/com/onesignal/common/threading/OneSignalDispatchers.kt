package com.onesignal.common.threading

import android.content.Context
import android.util.Log
import com.onesignal.core.internal.features.BootstrapCacheRead
import com.onesignal.core.internal.features.DispatcherBootstrapCache
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
    private const val RELEASE_LOG_TAG = "OneSignal"
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
    internal var afterExecutorCreateForTest: ((String, ThreadPoolExecutor) -> Unit)? = null

    @Volatile
    internal var beforeFallbackCreateForTest: ((String) -> Unit)? = null

    /** Lazy prewarm, before the lane lazy runs. Blocking here must not deadlock shutdown. */
    @Volatile
    internal var beforeLazyWarmForTest: ((String) -> Unit)? = null

    /** After the lazy prewarm thread is published, before [Thread.start]. */
    @Volatile
    internal var beforeLazyPrewarmStartForTest: (() -> Unit)? = null

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
        val queueCapacity: Int = QUEUE_CAPACITY,
    )

    /**
     * A stable dispatcher that queues work until its backing executor has been built and
     * prestarted on the bootstrap thread. CoroutineScope.launch can therefore return its real Job
     * without forcing executor construction or waiting on a lazy lock on the caller thread.
     */
    private class GateDispatcher(
        private val lane: Lane,
        private val requestBootstrap: (GateDispatcher) -> Unit,
    ) : CoroutineDispatcher(), DispatcherLane {
        override val dispatcher: CoroutineDispatcher get() = this

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

        // Catches Throwable because thread creation in prestartAllCoreThreads fails with OutOfMemoryError,
        // which is exactly the case the Dispatchers fallback exists for.
        @Suppress("TooGenericExceptionCaught")
        private fun createTargetOrFallback(): LaneTarget? =
            try {
                createTarget(lane)
            } catch (t: Throwable) {
                logRelease("$lane fell back", t)
                try {
                    createFallbackTarget(lane)
                } catch (fallbackError: Throwable) {
                    logRelease("$lane fallback failed", fallbackError)
                    failPending("OneSignal $lane dispatcher fallback failed")
                    null
                }
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

        override fun status(): String =
            synchronized(lock) {
                when (state) {
                    LaneState.READY -> "Active"
                    LaneState.CLOSED -> "Shutdown"
                    else -> state.name.lowercase().replaceFirstChar { it.uppercase() }
                }
            }

        override fun executor(): ThreadPoolExecutor? = synchronized(lock) { target?.executor }

        private fun failPending(reason: String) {
            val queued =
                synchronized(lock) {
                    val copy = pending.toList()
                    pending.clear()
                    // CLOSED is terminal: reopening would let a discarded generation build an executor
                    // that no later reset tears down.
                    if (state != LaneState.CLOSED) state = LaneState.COLD
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
            context[Job]?.cancel(CancellationException(reason))
            // Mirrors kotlinx's rejected-execution handling: the caller may be the main thread,
            // so the cancelled continuation's catch/finally must not run inline here.
            Dispatchers.IO.dispatch(context, block)
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
                logRelease("bootstrap thread failed to start", t)
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
                        logRelease("bootstrap lane failed", t)
                        gate.bootstrapFailed()
                    }
                }
            } finally {
                running.set(false)
                if (queue.isNotEmpty()) startIfNeeded()
            }
        }
    }

    private class Pools : DispatcherPools {
        private val coordinator = BootstrapCoordinator()
        private val ioGate = GateDispatcher(Lane.IO, coordinator::request)
        private val defaultGate = GateDispatcher(Lane.DEFAULT, coordinator::request)
        private val serialGate = GateDispatcher(Lane.SERIAL_IO, coordinator::request)
        private val ingressGate = GateDispatcher(Lane.INGRESS, coordinator::request)
        override val io: DispatcherLane = ioGate
        override val defaultLane: DispatcherLane = defaultGate
        override val serialIO: DispatcherLane = serialGate
        override val ingress: DispatcherLane = ingressGate
        override val ioScope = CoroutineScope(SupervisorJob() + ioGate)
        override val defaultScope = CoroutineScope(SupervisorJob() + defaultGate)
        override val serialIOScope = CoroutineScope(SupervisorJob() + serialGate)
        override val ingressScope = CoroutineScope(SupervisorJob() + ingressGate)

        // Ingress bootstraps first because receivers have a ~10s broadcast budget.
        private val gates = listOf(ingressGate, ioGate, defaultGate, serialGate)
        private val scopes = listOf(ingressScope, ioScope, defaultScope, serialIOScope)

        override fun prewarm() {
            gates.forEach { it.requestWarmup() }
        }

        override fun shutdown() {
            scopes.forEach { it.cancel() }
            gates.forEach { it.close() }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun createTarget(lane: Lane): LaneTarget {
        beforeLaneCreateForTest?.invoke(lane.name)
        val config = lane.config()
        val executor =
            ThreadPoolExecutor(
                config.corePoolSize,
                config.maxPoolSize,
                KEEP_ALIVE_TIME_SECONDS,
                TimeUnit.SECONDS,
                LinkedBlockingQueue(config.queueCapacity),
                OptimizedThreadFactory(config.threadName, config.priority),
            )
        try {
            executor.allowCoreThreadTimeOut(false)
            afterExecutorCreateForTest?.invoke(lane.name, executor)
            executor.prestartAllCoreThreads()
        } catch (t: Throwable) {
            executor.shutdownNow()
            throw t
        }
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
                    // Unbounded like Executors.newSingleThreadExecutor; rejecting would drop ordered lifecycle work.
                    Int.MAX_VALUE,
                )
            // Kept separate from IO so receiver handoffs never queue behind SDK HTTP.
            Lane.INGRESS ->
                LaneConfig(
                    1,
                    1,
                    INGRESS_THREAD_NAME,
                    Thread.NORM_PRIORITY - 1,
                    Int.MAX_VALUE,
                )
        }

    private fun createFallbackTarget(lane: Lane): LaneTarget {
        beforeFallbackCreateForTest?.invoke(lane.name)
        val dispatcher =
            when (lane) {
                Lane.IO -> Dispatchers.IO
                Lane.DEFAULT -> Dispatchers.Default
                Lane.SERIAL_IO, Lane.INGRESS -> {
                    @Suppress("OPT_IN_USAGE")
                    Dispatchers.IO.limitedParallelism(1)
                }
            }
        dispatcher.dispatch(kotlin.coroutines.EmptyCoroutineContext, Runnable {})
        return LaneTarget(dispatcher, null)
    }

    @Volatile
    private var poolsRef: DispatcherPools? = null
    private val poolsLock = Any()

    @Volatile
    private var appContext: Context? = null

    /** Test override. Null reads the cached flag, and no cache means off. */
    @Volatile
    internal var bootstrapEnabledForTest: Boolean? = null

    // Pools latch on first use, so context has to be recorded before the first prewarm or launch.
    @Suppress("TooGenericExceptionCaught")
    internal fun noteApplicationContext(context: Context) {
        if (appContext != null) return
        appContext =
            try {
                context.applicationContext
            } catch (t: Throwable) {
                Logging.warn("OneSignalDispatchers: application context unavailable: ${t.message}", t)
                null
            }
    }

    internal fun resetBootstrapInputsForTest() {
        appContext = null
        bootstrapEnabledForTest = null
    }

    private fun currentPools(): DispatcherPools {
        poolsRef?.let { return it }
        // Flag read does disk I/O. Keep it outside the lock so other launches are not stuck behind it.
        val choice = choosePools()
        val published =
            synchronized(poolsLock) {
                val existing = poolsRef
                if (existing != null) {
                    choice.pools.shutdown()
                    existing
                } else {
                    poolsRef = choice.pools
                    choice.pools
                }
            }
        if (published === choice.pools && bootstrapEnabledForTest == null) {
            val mode = if (choice.pools is LazyDispatcherPools) "lazy" else "bootstrap"
            logRelease("${choice.detail}; pools=$mode", warn = choice.warn)
        }
        return published
    }

    private fun newPools(): DispatcherPools = choosePools().pools

    private fun choosePools(): PoolChoice {
        val override = bootstrapEnabledForTest
        return if (override != null) {
            PoolChoice(poolsFor(override), "test", false)
        } else {
            chooseFromCache()
        }
    }

    private fun chooseFromCache(): PoolChoice {
        val context = appContext ?: rememberApplicationContext()
        val read = context?.let { DispatcherBootstrapCache.read(it) }
        val detail =
            when (read) {
                BootstrapCacheRead.ENABLED -> "on"
                BootstrapCacheRead.DISABLED -> "off, flag not in cache"
                BootstrapCacheRead.UNREADABLE -> "off, cache unreadable"
                null -> "off, no application"
            }
        return PoolChoice(poolsFor(read == BootstrapCacheRead.ENABLED), detail, warn = read == null)
    }

    private fun poolsFor(enabled: Boolean): DispatcherPools = if (enabled) Pools() else LazyDispatcherPools()

    private class PoolChoice(
        val pools: DispatcherPools,
        val detail: String,
        val warn: Boolean,
    )

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun rememberApplicationContext(): Context? {
        if (appContext != null) return appContext
        // Receivers call prewarm() with no Context when the startup provider is disabled.
        val resolved =
            try {
                val method = Class.forName("android.app.ActivityThread").getMethod("currentApplication")
                method.invoke(null) as? Context
            } catch (_: Throwable) {
                null
            }
        if (resolved != null) appContext = resolved.applicationContext
        return appContext
    }

    /** Logcat, not [Logging]: the SDK log level is often NONE in release, and Logging can dispatch. */
    @Suppress("SwallowedException")
    internal fun logRelease(
        message: String,
        error: Throwable? = null,
        warn: Boolean = error != null,
    ) {
        val line = "sdk_dispatcher_bootstrap: $message"
        try {
            if (warn) Log.w(RELEASE_LOG_TAG, line, error) else Log.i(RELEASE_LOG_TAG, line)
        } catch (_: RuntimeException) {
            // JVM unit tests throw from android.util.Log. A log must not fail the latch.
        }
    }

    // Dispatchers and scopes delegate to the current generation so [resetForTest] can swap them.
    val IO: CoroutineDispatcher get() = currentPools().io.dispatcher

    val Default: CoroutineDispatcher get() = currentPools().defaultLane.dispatcher

    val SerialIO: CoroutineDispatcher get() = currentPools().serialIO.dispatcher

    fun launchOnIO(block: suspend () -> Unit): Job {
        return currentPools().ioScope.launch { block() }
    }

    fun launchOnDefault(block: suspend () -> Unit): Job {
        return currentPools().defaultScope.launch { block() }
    }

    /** Launches [block] on the single-thread serial IO dispatcher (FIFO across all callers). */
    fun launchOnSerialIO(block: suspend () -> Unit): Job {
        return currentPools().serialIOScope.launch { block() }
    }

    /** Launches short durable-ingress work on a lane isolated from general SDK I/O. */
    fun launchOnIngress(block: suspend () -> Unit): Job {
        return currentPools().ingressScope.launch { block() }
    }

    @Volatile
    private var prewarmStarted = false
    private val prewarmLock = Any()

    /**
     * Warms every lane off the caller when it can.
     * Bootstrap queues construction. Lazy construction runs on the caller, or blocks behind prewarm.
     */
    @Suppress("TooGenericExceptionCaught")
    fun prewarm() {
        if (prewarmStarted) return
        synchronized(prewarmLock) {
            if (prewarmStarted) return
            prewarmStarted = true
        }
        try {
            currentPools().prewarm()
        } catch (t: Throwable) {
            synchronized(prewarmLock) { prewarmStarted = false }
            logRelease("prewarm failed", t)
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
     * (e.g. HttpClientTests' `launchOnIO {…}.join()`, OperationRepo, LoggerIdResolver) then see
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
        val created = newPools()
        val old: DispatcherPools?
        synchronized(poolsLock) {
            old = poolsRef
            poolsRef = created
        }
        try {
            old?.shutdown()
        } catch (e: Exception) {
            Logging.error("OneSignalDispatchers.resetForTest failed: ${e.message}", e)
        }
        resetPrewarmForTest()
        beforeLaneCreateForTest = null
        afterExecutorCreateForTest = null
        beforeFallbackCreateForTest = null
        beforeLazyWarmForTest = null
        beforeLazyPrewarmStartForTest = null
    }

    @Suppress("ComplexMethod")
    internal fun getPerformanceMetrics(): String {
        val current = currentPools()
        val io = current.io.executor()
        val default = current.defaultLane.executor()
        val serial = current.serialIO.executor()
        val ingress = current.ingress.executor()
        return """
            OneSignalDispatchers Performance Metrics:
            - IO Pool: ${io?.let { "${it.activeCount}/${it.corePoolSize}" } ?: "n/a"} active/core threads
            - IO Queue: ${io?.queue?.size ?: "n/a"} pending tasks
            - Default Pool: ${default?.let { "${it.activeCount}/${it.corePoolSize}" } ?: "n/a"} active/core threads
            - Default Queue: ${default?.queue?.size ?: "n/a"} pending tasks
            - SerialIO Queue: ${serial?.queue?.size ?: "n/a"} pending tasks
            - Ingress Queue: ${ingress?.queue?.size ?: "n/a"} pending tasks
            - Total completed tasks: ${listOfNotNull(io, default, serial, ingress).sumOf { it.completedTaskCount }}
            - Memory usage: ~${listOfNotNull(io, default, serial, ingress).sumOf { it.activeCount } * 1024}KB (thread stacks, ~1MB each)
        """.trimIndent()
    }

    internal fun getStatus(): String {
        val current = currentPools()
        return """
            OneSignalDispatchers Status:
            - IO Executor: ${current.io.status()}
            - Default Executor: ${current.defaultLane.status()}
            - SerialIO Executor: ${current.serialIO.status()}
            - Ingress Executor: ${current.ingress.status()}
            - IO Scope: ${scopeStatus("IOScope") { current.ioScope.isActive }}
            - Default Scope: ${scopeStatus("DefaultScope") { current.defaultScope.isActive }}
            - SerialIO Scope: ${scopeStatus("SerialIOScope") { current.serialIOScope.isActive }}
            - Ingress Scope: ${scopeStatus("IngressScope") { current.ingressScope.isActive }}
        """.trimIndent()
    }

    internal fun awaitReadyForTest(timeoutMs: Long = TEST_READY_TIMEOUT_MS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = currentPools()
            if (listOf(current.io, current.defaultLane, current.serialIO, current.ingress).all { it.status() == "Active" }) {
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
