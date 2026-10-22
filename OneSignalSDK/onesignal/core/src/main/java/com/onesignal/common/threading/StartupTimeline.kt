package com.onesignal.common.threading

import android.os.Build
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * When this process's dispatchers and init reached each startup step, on the uptime clock. Splits a
 * slow start into waiting for a thread (init queued to started) versus init's own work (started to
 * ended), and records how much of that main spent blocked. Marks are first-write-wins and lock-free.
 */
@Suppress("TooManyFunctions")
internal object StartupTimeline {
    private const val UNSET = -1L

    // An ANR this long after init ended is not about startup.
    private const val ANR_RELEVANCE_MS = 30_000L

    private val processStart = AtomicLong(UNSET)
    private val poolsChosen = AtomicLong(UNSET)

    @Volatile private var pools = "unknown"

    @Volatile private var choice = "unknown"
    private val ioReady = AtomicLong(UNSET)
    private val lanesReady = AtomicLong(UNSET)
    private val readyLaneBits = AtomicInteger(0)
    private val initQueued = AtomicLong(UNSET)
    private val initStarted = AtomicLong(UNSET)
    private val initEnded = AtomicLong(UNSET)

    @Volatile private var initResult = "pending"
    private val mainWaitTotal = AtomicLong(0)
    private val mainWaits = AtomicInteger(0)
    private val mainWaitSince = AtomicLong(UNSET)
    private val mainLaneBuild = AtomicLong(0)
    private val reported = AtomicBoolean(false)

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
        val start = now()
        mainWaitSince.set(start)
        mainWaits.incrementAndGet()
        try {
            return block()
        } finally {
            mainWaitSince.set(UNSET)
            mainWaitTotal.addAndGet(now() - start)
        }
    }

    /** Runs [block], counting its duration as main building (or waiting on) a lane when called on main. */
    fun <T> mainBuildingLane(block: () -> T): T {
        if (mainTimingLane || !isMainThread()) return block()
        mainTimingLane = true
        val start = now()
        try {
            return block()
        } finally {
            mainLaneBuild.addAndGet(now() - start)
            mainTimingLane = false
        }
    }

    /** True once per process, for whoever logs the timeline. */
    fun claimReport(): Boolean = reported.compareAndSet(false, true)

    /** Every step as milliseconds since process start; "-" for a step not reached. */
    fun summary(): String {
        val origin = origin()
        fun at(value: Long) = if (value == UNSET || origin == UNSET) "-" else (value - origin).toString()
        val ongoing = if (mainWaitSince.get() != UNSET) " main_wait_ongoing=true" else ""
        return "startup_timeline pools=$pools choice=\"$choice\" pools_chosen_ms=${at(poolsChosen.get())} " +
            "io_ready_ms=${at(ioReady.get())} lanes_ready_ms=${at(lanesReady.get())} " +
            "init_queued_ms=${at(initQueued.get())} init_started_ms=${at(initStarted.get())} " +
            "init_ended_ms=${at(initEnded.get())} init_result=$initResult main_wait_ms=${mainWaitMs()} " +
            "main_waits=${mainWaits.get()}$ongoing main_lane_build_ms=${mainLaneBuild.get()} origin=${originName()}"
    }

    /** [summary] for an ANR record, or null when the ANR is long past startup. */
    fun anrSummary(): String? {
        val ended = initEnded.get()
        if (ended != UNSET && now() - ended > ANR_RELEVANCE_MS) return null
        return summary()
    }

    internal fun resetForTest() {
        processStart.set(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Process.getStartUptimeMillis() else UNSET)
        listOf(poolsChosen, ioReady, lanesReady, initQueued, initStarted, initEnded, mainWaitSince).forEach { it.set(UNSET) }
        mainWaitTotal.set(0)
        mainLaneBuild.set(0)
        readyLaneBits.set(0)
        mainWaits.set(0)
        pools = "unknown"
        choice = "unknown"
        initResult = "pending"
        reported.set(false)
    }

    private fun origin(): Long = processStart.get().takeIf { it != UNSET } ?: poolsChosen.get()

    // Below API 24 there is no process start time, so steps are relative to the pool choice.
    private fun originName(): String = if (processStart.get() != UNSET) "process" else "pools"

    private fun mainWaitMs(): Long {
        val since = mainWaitSince.get()
        return mainWaitTotal.get() + if (since != UNSET) now() - since else 0
    }

    // Marks run inside lane builds and init, so a missing Android runtime (plain JVM tests) must not throw.
    @Suppress("SwallowedException")
    private fun now(): Long =
        try {
            SystemClock.uptimeMillis()
        } catch (_: RuntimeException) {
            UNSET
        }

    @Suppress("SwallowedException")
    private fun isMainThread(): Boolean =
        try {
            Looper.myLooper() == Looper.getMainLooper()
        } catch (_: RuntimeException) {
            false
        }
}
