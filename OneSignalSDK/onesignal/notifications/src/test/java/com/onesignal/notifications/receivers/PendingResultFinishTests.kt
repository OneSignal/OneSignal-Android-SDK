package com.onesignal.notifications.receivers

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.mocks.IOMockHelper
import com.onesignal.notifications.internal.ingress.NotificationIngress
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.robolectric.annotation.Config

/**
 * A failed handoff stays open for the deadline. Finishing it here would report the broadcast as done.
 */
@RobolectricTest
@Config(sdk = [28])
class PendingResultFinishTests : FunSpec({
    listener(IOMockHelper)

    beforeAny {
        clearMocks(OneSignalDispatchers, answers = false)
        mockkObject(NotificationIngress)
    }

    afterAny {
        unmockkObject(NotificationIngress)
    }

    test("boot restore finishes when enqueue completes") {
        coEvery { NotificationIngress.enqueueRestore(any()) } returns Unit
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = BootUpReceiver()
        val pendingResult = receiver.attachPendingResult()

        receiver.onReceive(context, Intent())

        verify(exactly = 1) { pendingResult.finish() }
    }

    test("boot restore leaves the pending result open when enqueue throws") {
        coEvery { NotificationIngress.enqueueRestore(any()) } throws NoSuchMethodError("forNamespace")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = BootUpReceiver()
        val pendingResult = receiver.attachPendingResult()

        shouldThrow<NoSuchMethodError> {
            receiver.onReceive(context, Intent())
        }

        verify(exactly = 0) { pendingResult.finish() }
    }

    test("upgrade restore leaves the pending result open when enqueue throws") {
        coEvery { NotificationIngress.enqueueRestore(any()) } throws NoSuchMethodError("forNamespace")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = UpgradeReceiver()
        val pendingResult = receiver.attachPendingResult()

        shouldThrow<NoSuchMethodError> {
            receiver.onReceive(context, Intent())
        }

        verify(exactly = 0) { pendingResult.finish() }
    }

    test("dismiss finishes when the handoff completes") {
        coEvery { NotificationIngress.persistDismiss(any(), any()) } returns Unit
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = NotificationDismissReceiver()
        val pendingResult = receiver.attachPendingResult()

        receiver.onReceive(context, Intent())

        verify(exactly = 1) { pendingResult.finish() }
    }

    test("dismiss leaves the pending result open when persist throws") {
        coEvery { NotificationIngress.persistDismiss(any(), any()) } throws NoSuchMethodError("forNamespace")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = NotificationDismissReceiver()
        val pendingResult = receiver.attachPendingResult()

        shouldThrow<NoSuchMethodError> {
            receiver.onReceive(context, Intent())
        }

        verify(exactly = 0) { pendingResult.finish() }
    }
})
