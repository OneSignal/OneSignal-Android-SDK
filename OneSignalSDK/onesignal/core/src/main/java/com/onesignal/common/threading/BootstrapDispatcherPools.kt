package com.onesignal.common.threading

import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

private const val BASE = OneSignalDispatchers.BASE_THREAD_NAME

// Optimized pool sizes based on CPU cores and workload analysis
private const val IO_CORE_POOL_SIZE = 2 // Increased for better concurrency
private const val IO_MAX_POOL_SIZE = 3 // Increased for better concurrency
private const val DEFAULT_CORE_POOL_SIZE = 2 // Optimal for CPU operations
private const val DEFAULT_MAX_POOL_SIZE = 3 // Slightly larger for CPU operations
private const val KEEP_ALIVE_TIME_SECONDS =
    30L // Keep threads alive longer to reduce recreation
private const val QUEUE_CAPACITY =
    200 // Increased to handle more queued operations during init, while still preventing memory bloat
private const val IO_THREAD_NAME_PREFIX =
    "$BASE-IO" // Thread name prefix for I/O operations
private const val DEFAULT_THREAD_NAME_PREFIX =
    "$BASE-Default" // Thread name prefix for CPU operations
private const val SERIAL_IO_THREAD_NAME =
    "$BASE-SerialIO" // Single, named thread for order-sensitive work
private const val INGRESS_THREAD_NAME = "$BASE-Ingress"

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
@Suppress("TooManyFunctions")
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
            OneSignalDispatchers.logFailure("$lane fell back", t)
            try {
                createFallbackTarget(lane)
            } catch (fallbackError: Throwable) {
                OneSignalDispatchers.logFailure("$lane fallback failed", fallbackError)
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
                "$BASE-bootstrap",
            ).apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 2
                start()
            }
        } catch (t: Throwable) {
            running.set(false)
            OneSignalDispatchers.logFailure("bootstrap thread failed to start", t)
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
                    OneSignalDispatchers.logFailure("bootstrap lane failed", t)
                    gate.bootstrapFailed()
                }
            }
        } finally {
            running.set(false)
            if (queue.isNotEmpty()) startIfNeeded()
        }
    }
}

internal class BootstrapDispatcherPools : DispatcherPools {
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
    OneSignalDispatchers.beforeLaneCreateForTest?.invoke(lane.name)
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
        OneSignalDispatchers.afterExecutorCreateForTest?.invoke(lane.name, executor)
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
    OneSignalDispatchers.beforeFallbackCreateForTest?.invoke(lane.name)
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
