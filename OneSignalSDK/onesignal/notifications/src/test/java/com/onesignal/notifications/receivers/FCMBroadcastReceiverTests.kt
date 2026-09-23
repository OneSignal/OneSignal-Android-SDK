package com.onesignal.notifications.receivers

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.OneSignal
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.suspendifyOnIngress
import com.onesignal.mocks.IOMockHelper
import com.onesignal.notifications.internal.ingress.NotificationIngress
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder

@RobolectricTest
class FCMBroadcastReceiverTests : FunSpec({
    listener(IOMockHelper)

    beforeAny {
        // IOMockHelper owns the OneSignalDispatchers object mock (incl. the prewarm() stub) for the
        // whole spec. Clear only its recorded calls so each test's prewarm() count starts at zero,
        // while keeping IOMockHelper's stubbed answers (answers = false).
        clearMocks(OneSignalDispatchers, answers = false)
        mockkObject(OneSignal)
        coEvery { OneSignal.initWithContext(any()) } returns false
        mockkObject(NotificationIngress)
        every { NotificationIngress.persistFcm(any(), any(), any()) } returns true
    }

    afterAny {
        // Tear down only the mock this spec owns. OneSignalDispatchers and the ThreadUtils statics
        // are owned by IOMockHelper and torn down in its afterSpec — unmockkAll() here would strip
        // them mid-spec and break the remaining tests.
        unmockkObject(OneSignal)
        unmockkObject(NotificationIngress)
    }

    test("FCMBroadcastReceiver.onReceive makes the explicit prewarm() head-start call before dispatch for a normal push") {
        // Scope of this test: it asserts the explicit `OneSignalDispatchers.prewarm()` call in
        // onReceive (the goAsync() head start) happens before the ingress dispatch.
        // IOMockHelper stubs `suspendifyOnIngress` (run inline) and prewarm(), so this verifies
        // placement/ordering, not end-to-end cold-init behavior.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent("com.google.android.c2dm.intent.RECEIVE").apply {
                putExtra("from", "sender")
                putExtra("message_type", "gcm")
            }

        FCMBroadcastReceiver().onReceive(context, intent)

        verify(exactly = 1) { OneSignalDispatchers.prewarm() }
        verifyOrder {
            OneSignalDispatchers.prewarm()
            suspendifyOnIngress(any<suspend () -> Unit>(), any<() -> Unit>())
        }
    }

    test("FCMBroadcastReceiver.onReceive skips prewarm for token update intents") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent("com.google.android.c2dm.intent.RECEIVE").apply {
                putExtra("from", "google.com/iid")
            }

        FCMBroadcastReceiver().onReceive(context, intent)

        verify(exactly = 0) { OneSignalDispatchers.prewarm() }
    }

    test("FCMBroadcastReceiver does not claim payloads rejected by durable ingress") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent("com.google.android.c2dm.intent.RECEIVE").apply {
                putExtra("from", "sender")
                putExtra("message_type", "gcm")
            }
        every { NotificationIngress.persistFcm(any(), any(), any()) } returns false

        FCMBroadcastReceiver().onReceive(context, intent)

        verify(exactly = 1) { NotificationIngress.persistFcm(context, intent, any()) }
    }

    test("FCMBroadcastReceiver ignores non-GCM message types") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent("com.google.android.c2dm.intent.RECEIVE").apply {
                putExtra("from", "sender")
                putExtra("message_type", "deleted_messages")
            }

        FCMBroadcastReceiver().onReceive(context, intent)

        verify(exactly = 0) { NotificationIngress.persistFcm(any(), any(), any()) }
    }
})
