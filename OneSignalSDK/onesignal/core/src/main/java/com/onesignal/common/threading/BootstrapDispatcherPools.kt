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
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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

/**
 * Queues work until the bootstrap thread has built and prestarted the lane's executor,
 * so `launch` returns its Job without building anything or blocking on the caller.
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

    // Built once per READY transition: dispatch runs on every resume, so it must not allocate.
    private var run: GateDecision.Run? = null

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        val decision =
            synchronized(lock) {
                when (state) {
                    LaneState.COLD -> {
                        pending.addLast(context to block)
                        state = LaneState.STARTING
                        GateDecision.Start
                    }
                    LaneState.STARTING, LaneState.DRAINING -> {
                        pending.addLast(context to block)
                        GateDecision.Queued
                    }
                    LaneState.READY -> run!!
                    LaneState.CLOSED -> GateDecision.Rejected
                }
            }

        when (decision) {
            GateDecision.Queued -> Unit
            GateDecision.Start -> requestBootstrap(this)
            is GateDecision.Run -> dispatchSafely(decision.dispatcher, context, block)
            GateDecision.Rejected -> cancelAndComplete(context, block, "OneSignal dispatcher generation is closed")
        }
    }

    private sealed interface GateDecision {
        object Queued : GateDecision

        object Start : GateDecision

        class Run(val dispatcher: CoroutineDispatcher) : GateDecision

        object Rejected : GateDecision
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
                                run = GateDecision.Run(newTarget.dispatcher)
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
            run = null
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
                // Not lowered: main can block waiting for init, which queues behind these builds.
                priority = Thread.NORM_PRIORITY
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
    DispatcherTestHooks.beforeLaneCreate?.invoke(lane.name)
    val executor = newLaneExecutor(lane)
    try {
        DispatcherTestHooks.afterExecutorCreate?.invoke(lane.name, executor)
        executor.prestartAllCoreThreads()
    } catch (t: Throwable) {
        executor.shutdownNow()
        throw t
    }
    return LaneTarget(executor.asCoroutineDispatcher(), executor)
}

private fun createFallbackTarget(lane: Lane): LaneTarget {
    DispatcherTestHooks.beforeFallbackCreate?.invoke(lane.name)
    val dispatcher = lane.fallbackDispatcher()
    dispatcher.dispatch(EmptyCoroutineContext, Runnable {})
    return LaneTarget(dispatcher, null)
}
