package com.onesignal.common.threading

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal const val BASE_THREAD_NAME = "OneSignal"
private const val POOL_MAX = 3
private const val BOUNDED_QUEUE = 200
private const val KEEP_ALIVE_SECONDS = 30L

internal enum class Lane(
    val core: Int,
    val max: Int,
    val queueCapacity: Int,
    val threadName: String,
    val priority: Int,
) {
    IO(2, POOL_MAX, BOUNDED_QUEUE, "$BASE_THREAD_NAME-IO", Thread.NORM_PRIORITY - 1),
    DEFAULT(2, POOL_MAX, BOUNDED_QUEUE, "$BASE_THREAD_NAME-Default", Thread.NORM_PRIORITY),

    // Unbounded like Executors.newSingleThreadExecutor; rejecting would drop ordered lifecycle work.
    SERIAL_IO(1, 1, Int.MAX_VALUE, "$BASE_THREAD_NAME-SerialIO", Thread.NORM_PRIORITY - 1),

    // Kept separate from IO so receiver handoffs never queue behind SDK HTTP.
    INGRESS(1, 1, Int.MAX_VALUE, "$BASE_THREAD_NAME-Ingress", Thread.NORM_PRIORITY - 1),
    ;

    fun fallbackDispatcher(): CoroutineDispatcher =
        when (this) {
            IO -> Dispatchers.IO
            DEFAULT -> Dispatchers.Default
            SERIAL_IO, INGRESS -> {
                @Suppress("OPT_IN_USAGE")
                Dispatchers.IO.limitedParallelism(1)
            }
        }
}

/** Threads start on first task unless the caller prestarts them. */
internal fun newLaneExecutor(lane: Lane): ThreadPoolExecutor =
    ThreadPoolExecutor(
        lane.core,
        lane.max,
        KEEP_ALIVE_SECONDS,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(lane.queueCapacity),
        LaneThreadFactory(lane),
    ).apply {
        allowCoreThreadTimeOut(false)
    }

private class LaneThreadFactory(
    private val lane: Lane,
) : ThreadFactory {
    private val threadNumber = AtomicInteger(1)

    override fun newThread(r: Runnable): Thread =
        Thread(r, "${lane.threadName}-${threadNumber.getAndIncrement()}").apply {
            isDaemon = true
            priority = lane.priority
        }
}
