package com.onesignal.common.threading

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Pre-bootstrap pools. The first dispatch constructs the executor on the caller thread.
 * [prewarm] tries to do that on a daemon thread, and a faster dispatch can still win.
 */
@Suppress("TooGenericExceptionCaught")
internal class LazyDispatcherPools : DispatcherPools {
    private val closed = AtomicBoolean(false)
    private val ioLane = LazyExecutorLane(Lane.IO)
    private val defaultExecutorLane = LazyExecutorLane(Lane.DEFAULT)
    private val serialLane = LazyExecutorLane(Lane.SERIAL_IO)
    private val ingressLane = LazyExecutorLane(Lane.INGRESS)

    override val io: DispatcherLane = ioLane
    override val defaultLane: DispatcherLane = defaultExecutorLane
    override val serialIO: DispatcherLane = serialLane
    override val ingress: DispatcherLane = ingressLane

    private val ioScopeLazy = lazy { CoroutineScope(SupervisorJob() + ioLane.dispatcher) }
    private val defaultScopeLazy = lazy { CoroutineScope(SupervisorJob() + defaultExecutorLane.dispatcher) }
    private val serialScopeLazy = lazy { CoroutineScope(SupervisorJob() + serialLane.dispatcher) }
    private val ingressScopeLazy = lazy { CoroutineScope(SupervisorJob() + ingressLane.dispatcher) }

    override val ioScope: CoroutineScope get() = ioScopeLazy.value
    override val defaultScope: CoroutineScope get() = defaultScopeLazy.value
    override val serialIOScope: CoroutineScope get() = serialScopeLazy.value
    override val ingressScope: CoroutineScope get() = ingressScopeLazy.value

    private val prewarmThread = AtomicReference<Thread?>(null)

    override fun prewarm() {
        if (closed.get()) return
        val thread =
            Thread(
                {
                    // Ingress first: receivers have a ~10s broadcast budget.
                    warm(Lane.INGRESS) { ingressScope }
                    warm(Lane.IO) { ioScope }
                    warm(Lane.DEFAULT) { defaultScope }
                    warm(Lane.SERIAL_IO) { serialIOScope }
                },
                "$BASE_THREAD_NAME-prewarm",
            )
        thread.isDaemon = true
        thread.priority = Thread.NORM_PRIORITY - 2
        if (prewarmThread.compareAndSet(null, thread)) startPrewarm(thread)
    }

    private fun startPrewarm(thread: Thread) {
        if (closed.get()) {
            prewarmThread.compareAndSet(thread, null)
            return
        }
        try {
            DispatcherTestHooks.beforeLazyPrewarmStart?.invoke()
            thread.start()
        } catch (t: Throwable) {
            // start() failed after the ref was published. Clear it or every retry hits a failed CAS.
            prewarmThread.compareAndSet(thread, null)
            throw t
        }
    }

    override fun shutdown() {
        closed.set(true)
        prewarmThread.getAndSet(null)?.let { thread ->
            thread.interrupt()
            runCatching { thread.join(PREWARM_JOIN_MS) }
        }
        listOf(ioScopeLazy, defaultScopeLazy, serialScopeLazy, ingressScopeLazy).forEach { scope ->
            if (scope.isInitialized()) runCatching { scope.value.cancel() }
        }
        listOf(ioLane, defaultExecutorLane, serialLane, ingressLane).forEach { it.shutdown() }
    }

    private fun warm(
        lane: Lane,
        resolve: () -> CoroutineScope,
    ) {
        DispatcherTestHooks.beforeLazyWarm?.invoke(lane.name)
        if (closed.get()) return
        val scope = resolveScope(resolve) ?: return
        if (closed.get()) {
            runCatching { scope.cancel() }
        } else {
            runCatching { scope.launch { } }.onFailure(::logWarmFailure)
        }
    }

    private fun resolveScope(resolve: () -> CoroutineScope): CoroutineScope? =
        try {
            resolve()
        } catch (t: Throwable) {
            logWarmFailure(t)
            null
        }

    private fun logWarmFailure(t: Throwable) {
        if (closed.get()) return
        OneSignalDispatchers.logFailure("lazy prewarm failed", t)
    }

    private inner class LazyExecutorLane(
        private val lane: Lane,
    ) : DispatcherLane {
        private val executorLazy: Lazy<ThreadPoolExecutor?> = lazy { createExecutor() }

        private fun createExecutor(): ThreadPoolExecutor? {
            if (closed.get()) return null
            return try {
                buildExecutor()
            } catch (t: Throwable) {
                OneSignalDispatchers.logFailure("lazy $lane fell back", t)
                DispatcherTestHooks.beforeFallbackCreate?.invoke(lane.name)
                null
            }
        }

        private fun buildExecutor(): ThreadPoolExecutor? {
            DispatcherTestHooks.beforeLaneCreate?.invoke(lane.name)
            if (closed.get()) return null
            val created = newLaneExecutor(lane)
            DispatcherTestHooks.afterExecutorCreate?.invoke(lane.name, created)
            if (closed.get()) created.shutdownNow()
            return created.takeUnless { it.isShutdown }
        }

        override val dispatcher: CoroutineDispatcher by lazy {
            val executor = executorLazy.value
            if (executor == null) {
                lane.fallbackDispatcher()
            } else {
                try {
                    executor.asCoroutineDispatcher()
                } catch (t: Throwable) {
                    OneSignalDispatchers.logFailure("lazy $lane fell back", t)
                    lane.fallbackDispatcher()
                }
            }
        }

        override fun executor(): ThreadPoolExecutor? =
            if (executorLazy.isInitialized()) executorLazy.value else null

        override fun status(): String =
            try {
                val executor = if (executorLazy.isInitialized()) executorLazy.value else null
                when {
                    closed.get() -> "Shutdown"
                    executor == null && executorLazy.isInitialized() -> "Active"
                    executor == null -> "Cold"
                    executor.isShutdown -> "Shutdown"
                    else -> "Active"
                }
            } catch (_: Throwable) {
                "Cold"
            }

        fun shutdown() {
            if (!executorLazy.isInitialized()) return
            runCatching { executorLazy.value?.shutdownNow() }
        }
    }

    private companion object {
        private const val PREWARM_JOIN_MS = 250L
    }
}
