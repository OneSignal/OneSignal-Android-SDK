package com.onesignal.notifications.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.OneSignal
import com.onesignal.common.services.IServiceProvider
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.suspendifyOnDefault
import com.onesignal.mocks.IOMockHelper
import com.onesignal.notifications.internal.open.INotificationOpenedProcessor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.runBlocking
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper

class TestNotificationOpenedActivity : NotificationOpenedActivityBase() {
    var wasFinishCalledOnMainThread = false
    var wasProcessIntentCalled = false

    override fun finish() {
        wasFinishCalledOnMainThread = Looper.myLooper() == Looper.getMainLooper()
        super.finish()
    }

    override fun getIntent(): Intent {
        return Intent().apply {
            putExtra("some_key", "some_value") // simulate a valid OneSignal intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This triggers processIntent inside base
    }

    override fun processIntent() {
        wasProcessIntentCalled = true
        super.processIntent()
    }
}

@RobolectricTest
class NotificationOpenedActivityTest : FunSpec({
    // processIntent() now calls OneSignalDispatchers.prewarm(); IOMockHelper stubs it to a no-op so
    // setup() doesn't spawn the real OneSignal-prewarm daemon + executors that persist across the JVM.
    listener(IOMockHelper)

    beforeAny {
        clearMocks(OneSignalDispatchers, answers = false)
    }

    test("processIntent calls prewarm before suspendifyOnDefault") {
        Robolectric.buildActivity(TestNotificationOpenedActivity::class.java).setup()

        verify(atLeast = 1) { OneSignalDispatchers.prewarm() }
        verifyOrder {
            OneSignalDispatchers.prewarm()
            suspendifyOnDefault(any<suspend () -> Unit>())
        }
    }

    test("finishSafely should be called on main thread") {
        val controller = Robolectric.buildActivity(TestNotificationOpenedActivity::class.java)
        val activity = controller.setup().get()
        Handler(Looper.getMainLooper()).post {
            activity.finish()
        }
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        activity.wasFinishCalledOnMainThread shouldBe true
    }

    test("processIntent should be called during activity setup") {
        val controller = Robolectric.buildActivity(TestNotificationOpenedActivity::class.java)
        val activity = controller.setup().get()

        activity.wasProcessIntentCalled shouldBe true
    }

    // Ordering guard (not the SDK-4845 regression itself): the trampoline must not be finished by
    // the synchronous part of processIntent, and on the success path finish() must run only after
    // processFromContext (preserving the Xiaomi startActivity()-before-finish() ordering).
    // suspendifyOnDefault is stubbed to capture (not run) its block so we control when it executes.
    test("finishes the base trampoline only after open processing completes") {
        val serviceProvider = mockk<IServiceProvider>(relaxed = true)
        val processor = mockk<INotificationOpenedProcessor>(relaxed = true)
        every { serviceProvider.getService(INotificationOpenedProcessor::class.java) } returns processor
        mockkObject(OneSignal)
        try {
            every { OneSignal.services } returns serviceProvider
            coEvery { OneSignal.initWithContext(any<Context>()) } returns true

            var capturedBlock: (suspend () -> Unit)? = null
            every { suspendifyOnDefault(any()) } answers { capturedBlock = firstArg() }

            val activity = Robolectric.buildActivity(TestNotificationOpenedActivity::class.java).setup().get()

            // The synchronous part of processIntent must NOT finish the activity or run processing yet.
            activity.isFinishing shouldBe false
            coVerify(exactly = 0) { processor.processFromContext(any(), any()) }

            runBlocking { capturedBlock!!.invoke() }
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            coVerify(exactly = 1) { processor.processFromContext(activity, any()) }
            activity.isFinishing shouldBe true
        } finally {
            unmockkObject(OneSignal)
        }
    }

    // Core SDK-4845 regression: the init-failure early-return path must still dismiss the trampoline.
    test("always finishes the base trampoline even when initialization fails") {
        val serviceProvider = mockk<IServiceProvider>(relaxed = true)
        val processor = mockk<INotificationOpenedProcessor>(relaxed = true)
        every { serviceProvider.getService(INotificationOpenedProcessor::class.java) } returns processor
        mockkObject(OneSignal)
        try {
            every { OneSignal.services } returns serviceProvider
            coEvery { OneSignal.initWithContext(any<Context>()) } returns false

            var capturedBlock: (suspend () -> Unit)? = null
            every { suspendifyOnDefault(any()) } answers { capturedBlock = firstArg() }

            val activity = Robolectric.buildActivity(TestNotificationOpenedActivity::class.java).setup().get()

            runBlocking { capturedBlock!!.invoke() }
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            // Early return on init failure must still dismiss the trampoline via the finally block.
            coVerify(exactly = 0) { processor.processFromContext(any(), any()) }
            activity.isFinishing shouldBe true
        } finally {
            unmockkObject(OneSignal)
        }
    }

    // Core SDK-4845 regression: when open processing throws, the trampoline must still be dismissed.
    // The exception propagates (suspendifyWithCompletion catches/logs it in production), but the
    // finally block must have finished the activity before it unwinds.
    test("always finishes the base trampoline even when open processing throws") {
        val serviceProvider = mockk<IServiceProvider>(relaxed = true)
        val processor = mockk<INotificationOpenedProcessor>(relaxed = true)
        every { serviceProvider.getService(INotificationOpenedProcessor::class.java) } returns processor
        coEvery { processor.processFromContext(any(), any()) } throws RuntimeException("boom")
        mockkObject(OneSignal)
        try {
            every { OneSignal.services } returns serviceProvider
            coEvery { OneSignal.initWithContext(any<Context>()) } returns true

            var capturedBlock: (suspend () -> Unit)? = null
            every { suspendifyOnDefault(any()) } answers { capturedBlock = firstArg() }

            val activity = Robolectric.buildActivity(TestNotificationOpenedActivity::class.java).setup().get()

            val result = runCatching { runBlocking { capturedBlock!!.invoke() } }
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            result.isFailure shouldBe true
            coVerify(exactly = 1) { processor.processFromContext(activity, any()) }
            activity.isFinishing shouldBe true
        } finally {
            unmockkObject(OneSignal)
        }
    }
})
