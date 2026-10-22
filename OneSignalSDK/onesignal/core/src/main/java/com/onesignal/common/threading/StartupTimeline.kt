package com.onesignal.common.threading

import android.os.Build
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * When this process's dispatchers and init reached each startup step, on the uptime clock.
 * One per process: marks are first-write-wins, except init marks, which a retry after a failed init resets.
 */
@Suppress("TooManyFunctions")
internal object StartupTimeline {
    private const val UNSET = -1L
    private const val NANOS_PER_MS = 1_000_000L

    // An ANR this long after init ended is not about startup.
    private const val ANR_RELEVANCE_MS = 30_000L

    private val processStart = AtomicLong(UNSET)
    private val poolsChosen = AtomicLong(UNSET)

    @Volatile private var pools = "unknown"

    @Volatile private var choice = "unknown"
    private val ioReady = AtomicLong(UNSET)
    private val lanesReady = AtomicLong(UNSET)
    private val readyLaneBits = AtomicInteger(0)
    private val initAttempts = AtomicInteger(0)
    private val initQueued = AtomicLong(UNSET)
    private val initStarted = AtomicLong(UNSET)
    private val initEnded = AtomicLong(UNSET)

    @Volatile private var initResult = "pending"
    private val reported = AtomicBoolean(false)

    private val mainWait = MainTimer()
    private val mainLaneBuild = MainTimer()

    // Touched only on main: a scope build reads the dispatcher, and that must not count twice.
    private var mainTimingLane = false

    init {
        resetForTest()
    }

    fun poolsChosen(
        mode: String,
        detail: String,
    ) {
        if (!poolsChosen.compareAndSet(UNSET, now())) return
        pools = mode
        choice = detail
    }

    /** Bootstrap marks a lane once its threads are started; lazy marks it once built, as threads start on first task. */
    fun laneReady(lane: Lane) {
        val at = now()
        if (lane == Lane.IO) ioReady.compareAndSet(UNSET, at)
        val all = (1 shl Lane.values().size) - 1
        while (true) {
            val bits = readyLaneBits.get()
            val next = bits or (1 shl lane.ordinal)
            if (bits == next || readyLaneBits.compareAndSet(bits, next)) {
                if (next == all) lanesReady.compareAndSet(UNSET, at)
                return
            }
        }
    }

    /** Call when init moves to in progress. A retry after a failed init times and reports the new attempt. */
    fun initAttempt() {
        if (initAttempts.incrementAndGet() == 1) return
        listOf(initQueued, initStarted, initEnded).forEach { it.set(UNSET) }
        initResult = "pending"
        reported.set(false)
    }

    fun initQueued() {
        initQueued.compareAndSet(UNSET, now())
    }

    fun initStarted() {
        initStarted.compareAndSet(UNSET, now())
    }

    fun initEnded(result: String) {
        if (initEnded.compareAndSet(UNSET, now())) initResult = result
    }

    /** Runs [block], counting its duration as main blocked on init when called on main. */
    fun <T> mainWaitingForInit(block: () -> T): T {
        if (!isMainThread()) return block()
        return mainWait.time(block)
    }

    /** Runs [block], counting its duration as main building (or waiting on) a lane when called on main. */
    fun <T> mainBuildingLane(block: () -> T): T {
        if (!isMainThread() || mainTimingLane) return block()
        mainTimingLane = true
        try {
            return mainLaneBuild.time(block)
        } finally {
            mainTimingLane = false
        }
    }

    /** True once per init attempt that reaches the end, for whoever logs the timeline. */
    fun claimReport(): Boolean = reported.compareAndSet(false, true)

    /**
     * Steps (`*_ms` except the two below) are milliseconds since [originName], "-" when not reached.
     * `main_wait_ms` and `main_lane_build_ms` are durations.
     */
    fun summary(): String {
        val origin = origin()
        fun at(value: Long) = if (value == UNSET || origin == UNSET) "-" else (value - origin).toString()
        val wait = mainWait.read()
        val build = mainLaneBuild.read()
        return "startup_timeline pools=$pools choice=\"$choice\" pools_chosen_ms=${at(poolsChosen.get())} " +
            "io_ready_ms=${at(ioReady.get())} lanes_ready_ms=${at(lanesReady.get())} " +
            "init_attempts=${initAttempts.get()} init_queued_ms=${at(initQueued.get())} " +
            "init_started_ms=${at(initStarted.get())} init_ended_ms=${at(initEnded.get())} init_result=$initResult " +
            "main_wait_ms=${wait.ms} main_waits=${wait.count}${wait.ongoing("main_wait")} " +
            "main_lane_build_ms=${build.ms}${build.ongoing("main_lane_build")} origin=${originName()}"
    }

    /** [summary] for an ANR record, or null when the ANR is long past startup. */
    fun anrSummary(): String? {
        val ended = initEnded.get()
        if (ended != UNSET && now() - ended >= ANR_RELEVANCE_MS) return null
        return summary()
    }

    internal fun resetForTest() {
        processStart.set(processStartUptime())
        listOf(poolsChosen, ioReady, lanesReady, initQueued, initStarted, initEnded).forEach { it.set(UNSET) }
        mainWait.reset()
        mainLaneBuild.reset()
        readyLaneBits.set(0)
        initAttempts.set(0)
        pools = "unknown"
        choice = "unknown"
        initResult = "pending"
        reported.set(false)
    }

    private fun origin(): Long = processStart.get().takeIf { it != UNSET } ?: poolsChosen.get()

    // Below API 24 there is no process start time, so steps are relative to the pool choice.
    private fun originName(): String = if (processStart.get() != UNSET) "process" else "pools"

    // Runs in the object's initializer, where a throw would make the class unloadable.
    @Suppress("SwallowedException")
    private fun processStartUptime(): Long =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Process.getStartUptimeMillis() else UNSET
        } catch (_: RuntimeException) {
            UNSET
        }

    // Marks run inside lane builds and init, so a missing Android runtime (plain JVM tests) must not throw.
    @Suppress("SwallowedException")
    private fun now(): Long =
        try {
            SystemClock.uptimeMillis()
        } catch (_: RuntimeException) {
            System.nanoTime() / NANOS_PER_MS
        }

    @Suppress("SwallowedException")
    private fun isMainThread(): Boolean =
        try {
            Looper.myLooper() == Looper.getMainLooper()
        } catch (_: RuntimeException) {
            false
        }

    private class Timing(
        val ms: Long,
        val count: Int,
        private val running: Boolean,
    ) {
        fun ongoing(name: String) = if (running) " ${name}_ongoing=true" else ""
    }

    // Total and in-progress time change together, so a reading never drops or double counts a span.
    private class MainTimer {
        private var total = 0L
        private var count = 0
        private var since = UNSET

        fun <T> time(block: () -> T): T {
            val start = now()
            synchronized(this) {
                since = start
                count++
            }
            try {
                return block()
            } finally {
                val end = now()
                synchronized(this) {
                    since = UNSET
                    total += end - start
                }
            }
        }

        @Synchronized
        fun read() = Timing(total + if (since != UNSET) now() - since else 0, count, since != UNSET)

        @Synchronized
        fun reset() {
            total = 0
            count = 0
            since = UNSET
        }
    }
}
