package com.onesignal.common.threading

import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Lane failures go through [Logging], whose listeners can launch straight back onto the failing lane. */
@RobolectricTest
class DispatcherFailureLogTests : FunSpec({
    // Top-level tests only: the Robolectric extension deadlocks on nested containers.
    fun usePools(bootstrap: Boolean) {
        Logging.logLevel = LogLevel.NONE
        OneSignalDispatchers.bootstrapEnabledForTest = bootstrap
        OneSignalDispatchers.resetForTest()
    }

    afterEach {
        OneSignalDispatchers.bootstrapEnabledForTest = null
        OneSignalDispatchers.resetForTest()
    }

    test("logcat lines follow the customer's SDK log level") {
        ShadowLog.clear()
        try {
            Logging.logLevel = LogLevel.NONE
            OneSignalDispatchers.logRelease("probe at none", IllegalStateException("x"))
            Logging.logLevel = LogLevel.INFO
            OneSignalDispatchers.logRelease("probe at info")
        } finally {
            Logging.logLevel = LogLevel.WARN
        }

        val lines = ShadowLog.getLogsForTag("OneSignal").map { it.msg }
        lines.any { "probe at none" in it } shouldBe false
        lines.any { "probe at info" in it } shouldBe true
    }

    listOf(true to "bootstrap", false to "lazy").forEach { (bootstrap, mode) ->
        test("$mode pools: a lane failure reaches log listeners") {
            usePools(bootstrap)
            val logged = CountDownLatch(1)
            val listener = ILogListener { if ("IO fell back" in it.entry) logged.countDown() }
            DispatcherTestHooks.beforeLaneCreate = { lane ->
                if (lane == "IO") throw IllegalStateException("no threads")
            }
            Logging.addListener(listener)
            try {
                OneSignalDispatchers.launchOnIO { }
                logged.await(2, TimeUnit.SECONDS) shouldBe true
            } finally {
                Logging.removeListener(listener)
            }
        }

        test("$mode pools: a listener that launches work does not re-enter lane creation") {
            usePools(bootstrap)
            val attempts = AtomicInteger()
            DispatcherTestHooks.beforeLaneCreate = { lane ->
                if (lane == "IO") {
                    attempts.incrementAndGet()
                    throw IllegalStateException("no threads")
                }
            }
            val listener = ILogListener { OneSignalDispatchers.launchOnIO { } }
            Logging.addListener(listener)
            try {
                val ran = CountDownLatch(1)
                OneSignalDispatchers.launchOnIO { ran.countDown() }
                ran.await(2, TimeUnit.SECONDS) shouldBe true
            } finally {
                Logging.removeListener(listener)
            }

            attempts.get() shouldBeLessThanOrEqual 2
        }
    }

    test("a listener that launches on a pool choice failure reuses the published pools") {
        Logging.logLevel = LogLevel.NONE
        OneSignalDispatchers.resetBootstrapInputsForTest()
        OneSignalDispatchers.reportChoiceToTelemetry()
        val choices = AtomicInteger()
        DispatcherTestHooks.beforePoolChoice = {
            choices.incrementAndGet()
            throw IllegalStateException("choice failed")
        }
        val reported = CountDownLatch(1)
        val listener =
            ILogListener {
                if ("choice failed" in it.entry) {
                    reported.countDown()
                    OneSignalDispatchers.launchOnIO { }
                }
            }
        Logging.addListener(listener)
        try {
            OneSignalDispatchers.unpublishPoolsForTest()
            val ran = CountDownLatch(1)
            OneSignalDispatchers.launchOnIO { ran.countDown() }
            ran.await(2, TimeUnit.SECONDS) shouldBe true
            reported.await(2, TimeUnit.SECONDS) shouldBe true
        } finally {
            Logging.removeListener(listener)
            OneSignalDispatchers.resetBootstrapInputsForTest()
        }

        choices.get() shouldBe 1
    }

})
