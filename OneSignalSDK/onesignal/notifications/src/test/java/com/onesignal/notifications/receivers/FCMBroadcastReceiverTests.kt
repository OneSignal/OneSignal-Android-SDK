package com.onesignal.notifications.receivers

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.OneSignal
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.mocks.IOMockHelper
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify

@RobolectricTest
class FCMBroadcastReceiverTests : FunSpec({
    listener(IOMockHelper)

    beforeAny {
        mockkObject(OneSignalDispatchers)
        every { OneSignalDispatchers.prewarm() } just runs
        mockkObject(OneSignal)
        coEvery { OneSignal.initWithContext(any()) } returns false
    }

    afterAny {
        unmockkAll()
    }

    test("FCMBroadcastReceiver.onReceive makes the explicit prewarm() head-start call for a normal push") {
        // Scope of this test: it asserts the explicit `OneSignalDispatchers.prewarm()` call in
        // onReceive (the goAsync() head start). IOMockHelper stubs `suspendifyOnIO` and prewarm()
        // is mocked, so the exactly=1 count is the receiver's own call — this verifies placement,
        // not end-to-end cold-init behavior.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent("com.google.android.c2dm.intent.RECEIVE").apply {
                putExtra("from", "sender")
                putExtra("message_type", "gcm")
            }

        FCMBroadcastReceiver().onReceive(context, intent)

        verify(exactly = 1) { OneSignalDispatchers.prewarm() }
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
})
