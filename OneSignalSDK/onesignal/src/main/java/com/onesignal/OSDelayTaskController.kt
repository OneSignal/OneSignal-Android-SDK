package com.onesignal

import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

open class OSDelayTaskController(private val logger: OSLogger) {
    private val maxDelay = 25
    private val minDelay = 0

    private var scheduledThreadPoolExecutor: ScheduledThreadPoolExecutor

    init {
        scheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(1, JobThreadFactory())
    }

    protected open fun getRandomDelay(): Int {
        return (Math.random() * (maxDelay - minDelay + 1) + minDelay).roundToInt()
    }

    open fun delayTaskByRandom(runnable: Runnable) {
        val randomNum = getRandomDelay()

        logger.debug("OSDelayTaskController delaying task $randomNum second from thread: ${Thread.currentThread()}")
        scheduledThreadPoolExecutor.schedule(runnable, randomNum.toLong(), TimeUnit.SECONDS)
    }

    fun shutdownNow() {
        scheduledThreadPoolExecutor.shutdownNow()
    }

    private class JobThreadFactory : ThreadFactory {
        private val delayThreadName = "ONE_SIGNAL_DELAY"

        override fun newThread(runnable: Runnable): Thread {
            return Thread(runnable, delayThreadName)
        }
    }
}
