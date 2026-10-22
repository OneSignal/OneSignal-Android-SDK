package com.onesignal.common.threading

import android.os.Looper
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.ILogListener
import com.onesignal.debug.internal.crash.buildAnrCrashData
import com.onesignal.debug.internal.crash.buildBackgroundBlockCrashData
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.internal.InitState
import com.onesignal.internal.OneSignalImp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CompletableDeferred
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

@RobolectricTest
class StartupTimelineTests : FunSpec({
    beforeTest {
        StartupTimeline.resetForTest()
    }

    afterTest {
        OneSignalDispatchers.resetBootstrapInputsForTest()
        OneSignalDispatchers.resetForTest()
        StartupTimeline.resetForTest()
    }

    test("tests run on the main looper, which the main-thread counters depend on") {
        (Looper.myLooper() == Looper.getMainLooper()) shouldBe true
    }

    test("each step is reported once, and only the first mark counts") {
        StartupTimeline.poolsChosen("lazy", "off, no cache yet")
        advance(10)
        StartupTimeline.poolsChosen("bootstrap", "on")
        StartupTimeline.initQueued()
        advance(30)
        StartupTimeline.initStarted()
        advance(200)
        StartupTimeline.initEnded("success")

        val summary = StartupTimeline.summary()
        summary shouldContain "pools=lazy choice=\"off, no cache yet\""
        field(summary, "init_started_ms") - field(summary, "init_queued_ms") shouldBe 30
        field(summary, "init_ended_ms") - field(summary, "init_started_ms") shouldBe 200
        summary shouldContain "init_result=success"
    }

    test("lanes are ready only when every lane is, and a lane reported twice does not count twice") {
        StartupTimeline.poolsChosen("lazy", "test")
        StartupTimeline.laneReady(Lane.INGRESS)
        StartupTimeline.laneReady(Lane.IO)
        StartupTimeline.laneReady(Lane.IO)
        StartupTimeline.laneReady(Lane.DEFAULT)
        StartupTimeline.summary() shouldContain "lanes_ready_ms=-"

        advance(5)
        StartupTimeline.laneReady(Lane.SERIAL_IO)
        val summary = StartupTimeline.summary()
        field(summary, "lanes_ready_ms") - field(summary, "io_ready_ms") shouldBe 5
    }

    test("main blocked on init is counted, including a wait still going") {
        StartupTimeline.mainWaitingForInit {
            advance(400)
            StartupTimeline.summary() shouldContain "main_wait_ongoing=true"
            field(StartupTimeline.summary(), "main_wait_ms") shouldBe 400
        }
        val summary = StartupTimeline.summary()
        summary shouldNotContain "main_wait_ongoing"
        field(summary, "main_wait_ms") shouldBe 400
        field(summary, "main_waits") shouldBe 1
    }

    test("a background thread waiting on init is not main blocked") {
        thread { StartupTimeline.mainWaitingForInit { advance(400) } }.join()

        field(StartupTimeline.summary(), "main_waits") shouldBe 0
    }

    test("a lane build nested in a scope build on main counts once") {
        StartupTimeline.mainBuildingLane {
            advance(100)
            StartupTimeline.mainBuildingLane { advance(50) }
        }

        field(StartupTimeline.summary(), "main_lane_build_ms") shouldBe 150
    }

    test("a lane build still going on main is counted, so an ANR during it shows the build") {
        StartupTimeline.mainBuildingLane {
            advance(100)
            val summary = StartupTimeline.summary()
            summary shouldContain "main_lane_build_ongoing=true"
            field(summary, "main_lane_build_ms") shouldBe 100
        }
        StartupTimeline.summary() shouldNotContain "main_lane_build_ongoing"
    }

    test("with lazy pools, main building the first lane is counted and every lane reports ready") {
        OneSignalDispatchers.bootstrapEnabledForTest = false
        OneSignalDispatchers.unpublishPoolsForTest()
        DispatcherTestHooks.beforeLaneCreate = { advance(40) }

        OneSignalDispatchers.launchOnIO { }

        val summary = StartupTimeline.summary()
        summary shouldContain "pools=lazy"
        (field(summary, "main_lane_build_ms") >= 40) shouldBe true
        summary shouldNotContain "io_ready_ms=-"
    }

    test("with bootstrap pools, main never builds a lane") {
        OneSignalDispatchers.bootstrapEnabledForTest = true
        OneSignalDispatchers.unpublishPoolsForTest()
        DispatcherTestHooks.beforeLaneCreate = { advance(40) }

        OneSignalDispatchers.launchOnIO { }
        OneSignalDispatchers.prewarm(null)
        OneSignalDispatchers.awaitReadyForTest() shouldBe true

        val summary = StartupTimeline.summary()
        summary shouldContain "pools=bootstrap"
        field(summary, "main_lane_build_ms") shouldBe 0
        summary shouldNotContain "lanes_ready_ms=-"
    }

    test("init end logs the timeline once, counting main still blocked in waitForInit") {
        val os = OneSignalImp()
        os.setField("initState", InitState.IN_PROGRESS)
        os.setField("suspendCompletion", CompletableDeferred<Unit>())
        val completeInit = os.method("completeInit", InitState::class.java)
        val lines = CopyOnWriteArrayList<String>()
        val listener = ILogListener { if (it.entry.contains("startup_timeline")) lines += it.entry }
        Logging.addListener(listener)
        try {
            thread {
                awaitUntil { StartupTimeline.summary().contains("main_wait_ongoing=true") }
                advance(300)
                completeInit.invoke(os, InitState.SUCCESS)
            }
            os.method("waitForInit", String::class.java).invoke(os, null)
            completeInit.invoke(os, InitState.SUCCESS)
        } finally {
            Logging.removeListener(listener)
        }

        lines.size shouldBe 1
        lines[0] shouldContain "init_result=success"
        field(lines[0], "main_waits") shouldBe 1
        (field(lines[0], "main_wait_ms") >= 300) shouldBe true
    }

    test("a log listener that throws on the timeline does not fail init") {
        val os = OneSignalImp()
        os.setField("initState", InitState.IN_PROGRESS)
        os.setField("suspendCompletion", CompletableDeferred<Unit>())
        val listener = ILogListener { if (it.entry.contains("startup_timeline")) throw IllegalStateException("customer") }
        Logging.addListener(listener)
        try {
            os.method("completeInit", InitState::class.java).invoke(os, InitState.SUCCESS)
        } finally {
            Logging.removeListener(listener)
        }

        os.getField("initState") shouldBe InitState.SUCCESS
    }

    test("waitForInit before init started is not counted as main blocked") {
        val os = OneSignalImp()

        runCatching { os.method("waitForInit", String::class.java).invoke(os, null) }.isFailure shouldBe true

        field(StartupTimeline.summary(), "main_waits") shouldBe 0
    }

    test("a retry after a failed init is timed and reported as its own attempt") {
        StartupTimeline.initAttempt()
        StartupTimeline.initQueued()
        StartupTimeline.initStarted()
        StartupTimeline.initEnded("failed")
        StartupTimeline.claimReport() shouldBe true

        advance(1_000)
        StartupTimeline.initAttempt()
        StartupTimeline.summary() shouldContain "init_attempts=2 init_queued_ms=- init_started_ms=- init_ended_ms=- init_result=pending"
        StartupTimeline.initQueued()
        StartupTimeline.initStarted()
        StartupTimeline.initEnded("success")

        StartupTimeline.summary() shouldContain "init_result=success"
        StartupTimeline.claimReport() shouldBe true
        StartupTimeline.claimReport() shouldBe false
    }

    test("ANR records carry the timeline during startup, after the existing message") {
        StartupTimeline.initQueued()
        val timeline = StartupTimeline.anrSummary()!!

        buildAnrCrashData("main", emptyArray(), 6_000L, timeline).exceptionMessage shouldBe
            "Application Not Responding: Main thread blocked for 6000ms | $timeline"
        buildBackgroundBlockCrashData("main", emptyArray(), 11_000L, timeline).exceptionMessage shouldEndWith " | $timeline"
        buildAnrCrashData("main", emptyArray(), 6_000L).exceptionMessage shouldNotContain "startup_timeline"
    }

    test("an ANR 30 seconds or more after init ended carries no timeline") {
        StartupTimeline.initEnded("success")
        advance(29_999)
        (StartupTimeline.anrSummary() != null) shouldBe true

        advance(1)
        StartupTimeline.anrSummary() shouldBe null
    }
})

private fun awaitUntil(condition: () -> Boolean) {
    val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
    while (!condition()) {
        check(System.nanoTime() < deadline) { "condition not met in 10s" }
        Thread.sleep(5)
    }
}

private fun OneSignalImp.getField(name: String): Any? =
    OneSignalImp::class.java.getDeclaredField(name).apply { isAccessible = true }.get(this)

private fun OneSignalImp.setField(
    name: String,
    value: Any,
) = OneSignalImp::class.java.getDeclaredField(name).apply { isAccessible = true }.set(this, value)

private fun OneSignalImp.method(
    name: String,
    vararg types: Class<*>,
) = OneSignalImp::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }

private fun advance(ms: Long) = ShadowSystemClock.advanceBy(Duration.ofMillis(ms))

private fun field(
    summary: String,
    name: String,
): Long = Regex("$name=(-?\\d+)").find(summary)!!.groupValues[1].toLong()
