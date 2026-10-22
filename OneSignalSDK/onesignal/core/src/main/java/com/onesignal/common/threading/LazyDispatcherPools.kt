package com.onesignal.common.threading

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Pre-bootstrap pools. The first dispatch constructs the executor on the caller thread.
 * [prewarm] tries to do that on a daemon thread, and a faster dispatch can still win.
 */
@Suppress("TooGenericExceptionCaught")
internal class LazyDispatcherPools : DispatcherPools {
    private val closed = AtomicBoolean(false)
    private val ioLane =
        LazyExecutorLane("IO", IO_CORE, IO_MAX, QUEUE_CAPACITY, "$BASE-IO", Thread.NORM_PRIORITY - 1) {
            Dispatchers.IO
        }
    private val defaultExecutorLane =
        LazyExecutorLane("DEFAULT", DEFAULT_CORE, DEFAULT_MAX, QUEUE_CAPACITY, "$BASE-Default", Thread.NORM_PRIORITY) {
            Dispatchers.Default
        }
    private val serialLane =
        LazyExecutorLane("SERIAL_IO", 1, 1, Int.MAX_VALUE, "$BASE-SerialIO", Thread.NORM_PRIORITY - 1) {
            @Suppress("OPT_IN_USAGE")
            Dispatchers.IO.limitedParallelism(1)
        }
    private val ingressLane =
        LazyExecutorLane("INGRESS", 1, 1, Int.MAX_VALUE, "$BASE-Ingress", Thread.NORM_PRIORITY - 1) {
            @Suppress("OPT_IN_USAGE")
            Dispatchers.IO.limitedParallelism(1)
        }

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
                    warm("INGRESS") { ingressScope }
                    warm("IO") { ioScope }
                    warm("DEFAULT") { defaultScope }
                    warm("SERIAL_IO") { serialIOScope }
                },
                "$BASE-prewarm",
            )
        thread.isDaemon = true
        // Not lowered: a launch on main blocks behind this thread's lazy builds, so it must not be starved.
        thread.priority = Thread.NORM_PRIORITY
        if (prewarmThread.compareAndSet(null, thread)) startPrewarm(thread)
    }

    private fun startPrewarm(thread: Thread) {
        if (closed.get()) {
            prewarmThread.compareAndSet(thread, null)
            return
        }
        try {
            OneSignalDispatchers.beforeLazyPrewarmStartForTest?.invoke()
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
        lane: String,
        resolve: () -> CoroutineScope,
    ) {
        OneSignalDispatchers.beforeLazyWarmForTest?.invoke(lane)
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
        private val lane: String,
        private val core: Int,
        private val max: Int,
        private val queueCapacity: Int,
        private val threadName: String,
        private val priority: Int,
        private val fallback: () -> CoroutineDispatcher,
    ) : DispatcherLane {
        private val executorLazy: Lazy<ThreadPoolExecutor?> = lazy { createExecutor() }

        private fun createExecutor(): ThreadPoolExecutor? {
            if (closed.get()) return null
            return try {
                buildExecutor()
            } catch (t: Throwable) {
                OneSignalDispatchers.logFailure("lazy $lane fell back", t)
                OneSignalDispatchers.beforeFallbackCreateForTest?.invoke(lane)
                null
            }
        }

        private fun buildExecutor(): ThreadPoolExecutor? {
            OneSignalDispatchers.beforeLaneCreateForTest?.invoke(lane)
            if (closed.get()) return null
            val created = newExecutor()
            OneSignalDispatchers.afterExecutorCreateForTest?.invoke(lane, created)
            if (closed.get()) created.shutdownNow()
            return created.takeUnless { it.isShutdown }
        }

        override val dispatcher: CoroutineDispatcher by lazy {
            val executor = executorLazy.value
            if (executor == null) {
                fallback()
            } else {
                try {
                    executor.asCoroutineDispatcher()
                } catch (t: Throwable) {
                    OneSignalDispatchers.logFailure("lazy $lane fell back", t)
                    fallback()
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
                    executor == null && executorLazy.isInitialized() -> "Fallback"
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

        private fun newExecutor(): ThreadPoolExecutor =
            ThreadPoolExecutor(
                core,
                max,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                LinkedBlockingQueue(queueCapacity),
                NamedThreadFactory(threadName, priority),
            ).apply {
                allowCoreThreadTimeOut(false)
            }
    }

    private class NamedThreadFactory(
        private val namePrefix: String,
        private val priority: Int,
    ) : ThreadFactory {
        private val threadNumber = AtomicInteger(1)

        override fun newThread(r: Runnable): Thread {
            val thread = Thread(r, "$namePrefix-${threadNumber.getAndIncrement()}")
            thread.isDaemon = true
            thread.priority = priority
            return thread
        }
    }

    private companion object {
        private const val BASE = OneSignalDispatchers.BASE_THREAD_NAME
        private const val IO_CORE = 2
        private const val IO_MAX = 3
        private const val DEFAULT_CORE = 2
        private const val DEFAULT_MAX = 3
        private const val QUEUE_CAPACITY = 200
        private const val KEEP_ALIVE_SECONDS = 30L
        private const val PREWARM_JOIN_MS = 250L
    }
}
