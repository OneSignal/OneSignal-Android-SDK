package com.onesignal.common.threading

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.features.FeatureManager
import com.onesignal.core.internal.features.StartupFlagsCache
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.features.FeatureFlag
import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** How [OneSignalDispatchers] picks pools from the startup flags snapshot. */
@RobolectricTest
class DispatcherBootstrapFlagTests : FunSpec({
    val bootstrap = FeatureFlag.SDK_DISPATCHER_BOOTSTRAP
    val key = bootstrap.key

    afterTest {
        reportListener?.let(Logging::removeListener)
        reportListener = null
        StartupFlagsCache.write(appContext(), emptyList())
        OneSignalDispatchers.resetBootstrapInputsForTest()
        OneSignalDispatchers.resetForTest()
    }

    test("each APP_STARTUP flag is mirrored on its own") {
        val context = appContext()
        val logging = FeatureFlag.SDK_CUSTOM_LOGGING
        StartupFlagsCache.write(context, listOf(logging.key))
        StartupFlagsCache.readFile(context).isEnabled(logging) shouldBe true
        StartupFlagsCache.readFile(context).isEnabled(bootstrap) shouldBe false

        StartupFlagsCache.write(context, listOf(logging.key, key))
        StartupFlagsCache.readFile(context).isEnabled(logging) shouldBe true
        StartupFlagsCache.readFile(context).isEnabled(bootstrap) shouldBe true
    }

    test("a mirrored key selects bootstrap on the next pool generation") {
        val context = appContext()
        StartupFlagsCache.write(context, listOf(key))
        latchFrom(context)

        builtOnCaller() shouldBe false
    }

    test("no mirrored key keeps construction on the caller") {
        val context = appContext()
        StartupFlagsCache.write(context, emptyList())
        latchFrom(context)

        builtOnCaller() shouldBe true
    }

    test("an unreadable file keeps construction on the caller") {
        latchFrom(unreadableContext())

        builtOnCaller() shouldBe true
    }

    test("a pool choice failure falls back to lazy pools instead of throwing") {
        val context = appContext()
        StartupFlagsCache.write(context, listOf(key))
        DispatcherTestHooks.beforePoolChoice = { throw IllegalStateException("choice failed") }
        latchFrom(context)

        builtOnCaller() shouldBe true
    }

    test("a choice failure after an on snapshot runs lazy pools while FeatureManager reports on") {
        val context = appContext()
        StartupFlagsCache.write(context, listOf(key))
        DispatcherTestHooks.beforePoolChoice = {
            FeatureManager.startupFlags { context }
            throw IllegalStateException("choice failed")
        }
        val reports = captureChoiceReports()
        latchFrom(context)

        builtOnCaller() shouldBe true
        FeatureManager(MockHelper.configModelStore(), applicationServiceFor(context)).isEnabled(bootstrap) shouldBe true
        OneSignalDispatchers.reportChoiceToTelemetry()
        reports.single() shouldContain "off, choice failed; pools=lazy"
    }

    test("an unreadable file reaches telemetry once init has installed it") {
        val reports = captureChoiceReports()
        latchFrom(unreadableContext())
        reports shouldBe emptyList()

        OneSignalDispatchers.reportChoiceToTelemetry()
        reports.single() shouldContain "off, cache unreadable; pools=lazy"
        OneSignalDispatchers.reportChoiceToTelemetry()
        reports.size shouldBe 1
    }

    test("a degraded choice after telemetry is up is reported right away") {
        val reports = captureChoiceReports()
        OneSignalDispatchers.resetBootstrapInputsForTest()
        OneSignalDispatchers.reportChoiceToTelemetry()
        OneSignalDispatchers.noteApplicationContext(unreadableContext())
        OneSignalDispatchers.resetForTest()

        reports.single() shouldContain "off, cache unreadable"
    }

    test("a healthy choice is not reported") {
        val context = appContext()
        StartupFlagsCache.write(context, listOf(key))
        val reports = captureChoiceReports()
        latchFrom(context)
        OneSignalDispatchers.reportChoiceToTelemetry()

        reports shouldBe emptyList()
    }

    test("the bootstrap thread runs at normal priority") {
        OneSignalDispatchers.bootstrapEnabledForTest = true
        OneSignalDispatchers.resetForTest()
        val priority = AtomicInteger(-1)
        val seen = CountDownLatch(1)
        DispatcherTestHooks.beforeLaneCreate = { lane ->
            if (lane == "IO") {
                priority.set(Thread.currentThread().priority)
                seen.countDown()
            }
        }

        OneSignalDispatchers.launchOnIO { }

        seen.await(2, TimeUnit.SECONDS) shouldBe true
        priority.get() shouldBe Thread.NORM_PRIORITY
    }

    test("the lazy prewarm thread runs at normal priority") {
        OneSignalDispatchers.bootstrapEnabledForTest = false
        OneSignalDispatchers.resetForTest()
        val priority = AtomicInteger(-1)
        val seen = CountDownLatch(1)
        DispatcherTestHooks.beforeLazyWarm = {
            if (priority.compareAndSet(-1, Thread.currentThread().priority)) seen.countDown()
        }

        OneSignalDispatchers.prewarm()

        seen.await(2, TimeUnit.SECONDS) shouldBe true
        priority.get() shouldBe Thread.NORM_PRIORITY
    }

    test("prewarm latches from the process application when startup did not note a context") {
        StartupFlagsCache.write(appContext(), listOf(key))
        OneSignalDispatchers.resetBootstrapInputsForTest()
        OneSignalDispatchers.resetForTest()

        builtOnCaller() shouldBe false
    }

    test("FeatureManager reports the pools the process actually latched when store and file disagree") {
        val context = appContext()
        StartupFlagsCache.write(context, emptyList())
        latchFrom(context)
        builtOnCaller() shouldBe true

        val store = MockHelper.configModelStore { it.sdkRemoteFeatureFlags = listOf(key) }
        val applicationService = MockHelper.applicationService()
        every { applicationService.appContext } returns context
        val manager = FeatureManager(store, applicationService)

        manager.isEnabled(bootstrap) shouldBe false
        manager.enabledFeatureKeys() shouldBe emptyList()
    }
})

private fun appContext(): Context = ApplicationProvider.getApplicationContext()

private var reportListener: ILogListener? = null

/** Warnings [OneSignalDispatchers] sends through [Logging] about its pool choice. */
private fun captureChoiceReports(): List<String> {
    val reports = CopyOnWriteArrayList<String>()
    val listener = ILogListener { if (it.level == LogLevel.WARN && "sdk_dispatcher_bootstrap" in it.entry) reports.add(it.entry) }
    reportListener = listener
    Logging.addListener(listener)
    return reports
}

private fun unreadableContext(): Context {
    val broken = mockk<Context>()
    every { broken.applicationContext } returns broken
    every { broken.getSharedPreferences(any(), any()) } throws SecurityException("prefs unavailable")
    return broken
}

private fun applicationServiceFor(context: Context) = MockHelper.applicationService().also { every { it.appContext } returns context }

private fun latchFrom(context: Context) {
    OneSignalDispatchers.resetBootstrapInputsForTest()
    OneSignalDispatchers.noteApplicationContext(context)
    OneSignalDispatchers.resetForTest()
}

/** True when the IO lane is built on the thread that first launched work. */
private fun builtOnCaller(): Boolean {
    val callerThreadId = AtomicLong()
    val createThreadId = AtomicLong()
    val created = CountDownLatch(1)
    DispatcherTestHooks.beforeLaneCreate = { lane ->
        if (lane == "IO") {
            createThreadId.set(Thread.currentThread().id)
            created.countDown()
        }
    }
    Thread {
        callerThreadId.set(Thread.currentThread().id)
        OneSignalDispatchers.launchOnIO { }
    }.start()
    created.await(2, TimeUnit.SECONDS) shouldBe true
    return createThreadId.get() == callerThreadId.get()
}
