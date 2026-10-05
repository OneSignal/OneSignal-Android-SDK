package com.onesignal.notifications.receivers

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.OneSignal
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.mocks.IOMockHelper
import com.onesignal.notifications.internal.open.INotificationOpenedProcessor
import com.onesignal.notifications.internal.restoration.INotificationRestoreWorkManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.robolectric.annotation.Config

/**
 * A receiver that calls goAsync() and never finishes keeps the process alive until the framework
 * kills it, so every exit path has to reach finish(). IOMockHelper only catches Exception, so the
 * Errors below escape onReceive after the finally block, which production's suspendify swallows.
 */
@RobolectricTest
@Config(sdk = [28])
class PendingResultFinishTests : FunSpec({
    listener(IOMockHelper)

    beforeAny {
        clearMocks(OneSignalDispatchers, answers = false)
        mockkObject(OneSignal)
    }

    afterAny {
        unmockkObject(OneSignal)
    }

    test("boot restore finishes the pending result when enqueue throws") {
        coEvery { OneSignal.initWithContext(any()) } returns true
        every { OneSignal.getService<INotificationRestoreWorkManager>() } returns
            mockk {
                every { beginEnqueueingWork(any(), any()) } throws NoSuchMethodError("forNamespace")
            }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = BootUpReceiver()
        val pendingResult = receiver.attachPendingResult()

        shouldThrow<NoSuchMethodError> {
            receiver.onReceive(context, Intent())
        }

        verify(exactly = 1) { pendingResult.finish() }
    }

    test("upgrade restore finishes the pending result when enqueue throws") {
        coEvery { OneSignal.initWithContext(any()) } returns true
        every { OneSignal.getService<INotificationRestoreWorkManager>() } returns
            mockk {
                every { beginEnqueueingWork(any(), any()) } throws NoSuchMethodError("forNamespace")
            }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = UpgradeReceiver()
        val pendingResult = receiver.attachPendingResult()

        shouldThrow<NoSuchMethodError> {
            receiver.onReceive(context, Intent())
        }

        verify(exactly = 1) { pendingResult.finish() }
    }

    test("dismiss finishes the pending result when init fails") {
        coEvery { OneSignal.initWithContext(any()) } returns false
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = NotificationDismissReceiver()
        val pendingResult = receiver.attachPendingResult()

        receiver.onReceive(context, Intent())

        verify(exactly = 1) { pendingResult.finish() }
    }

    test("dismiss finishes the pending result when opened processing throws") {
        coEvery { OneSignal.initWithContext(any()) } returns true
        every { OneSignal.getService<INotificationOpenedProcessor>() } returns
            mockk {
                coEvery { processFromContext(any(), any()) } throws NoSuchMethodError("Main")
            }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = NotificationDismissReceiver()
        val pendingResult = receiver.attachPendingResult()
        @OptIn(ExperimentalCoroutinesApi::class)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            shouldThrow<NoSuchMethodError> {
                receiver.onReceive(context, Intent())
            }
        } finally {
            @OptIn(ExperimentalCoroutinesApi::class)
            Dispatchers.resetMain()
        }

        verify(exactly = 1) { pendingResult.finish() }
    }
})
