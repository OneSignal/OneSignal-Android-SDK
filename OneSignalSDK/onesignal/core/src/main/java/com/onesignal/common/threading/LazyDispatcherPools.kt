package com.onesignal.common.threading

import com.onesignal.debug.internal.logging.Logging
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pre-bootstrap pools. The first dispatch constructs the executor on the caller thread.
 * [prewarm] tries to do that on a daemon thread, and a faster dispatch can still win.
 */
@Suppress("TooGenericExceptionCaught")
internal class LazyDispatcherPools : DispatcherPools {
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

    override fun prewarm() {
        val thread =
            Thread(
                {
                    warm { ioScope }
                    warm { defaultScope }
                    warm { serialIOScope }
                    warm { ingressScope }
                },
                "$BASE-prewarm",
            )
        thread.isDaemon = true
        thread.priority = Thread.NORM_PRIORITY - 2
        thread.start()
    }

    override fun shutdown() {
        listOf(ioScopeLazy, defaultScopeLazy, serialScopeLazy, ingressScopeLazy).forEach { scope ->
            if (scope.isInitialized()) runCatching { scope.value.cancel() }
        }
        listOf(ioLane, defaultExecutorLane, serialLane, ingressLane).forEach { it.shutdown() }
    }

    private fun warm(resolve: () -> CoroutineScope) {
        runCatching { resolve().launch { } }
    }

    private class LazyExecutorLane(
        private val lane: String,
        private val core: Int,
        private val max: Int,
        private val queueCapacity: Int,
        private val threadName: String,
        private val priority: Int,
        private val fallback: () -> CoroutineDispatcher,
    ) : DispatcherLane {
        private val executorLazy: Lazy<ThreadPoolExecutor?> =
            lazy {
                var created: ThreadPoolExecutor? = null
                try {
                    OneSignalDispatchers.beforeLaneCreateForTest?.invoke(lane)
                    created = newExecutor()
                    OneSignalDispatchers.afterExecutorCreateForTest?.invoke(lane, created)
                    created
                } catch (t: Throwable) {
                    created?.shutdownNow()
                    Logging.warn("OneSignalDispatchers: Using fallback for $lane lane: ${t.message}", t)
                    null
                }
            }

        override val dispatcher: CoroutineDispatcher by lazy {
            val executor = executorLazy.value
            if (executor == null) {
                fallback()
            } else {
                try {
                    executor.asCoroutineDispatcher()
                } catch (t: Throwable) {
                    Logging.warn("OneSignalDispatchers: Using fallback for $lane lane: ${t.message}", t)
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
    }
}
