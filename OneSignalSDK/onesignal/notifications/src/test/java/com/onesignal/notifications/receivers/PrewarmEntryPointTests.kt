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

/**
 * Verifies each cold-start broadcast-receiver entry point makes the explicit
 * [OneSignalDispatchers.prewarm] head-start call before its first dispatch (SDK-4794 whack-a-mole
 * strategy). [IOMockHelper] stubs `suspendifyOnIO` and `prewarm()` is mocked, so these assert
 * placement of the explicit call, not end-to-end cold-init behavior.
 *
 * Not covered here (no unit-test path): `ADMMessageHandler` / `ADMMessageHandlerJob` (the
 * `com.amazon.device.messaging.*` base classes are not on the test classpath) and
 * `OneSignalHmsEventBridge` (`com.huawei.hms.push.*`). Those calls are verified by inspection.
 */
@RobolectricTest
class PrewarmEntryPointTests : FunSpec({
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

    test("BootUpReceiver.onReceive prewarms before dispatch") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BootUpReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        verify(exactly = 1) { OneSignalDispatchers.prewarm() }
    }

    test("UpgradeReceiver.onReceive prewarms before dispatch") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        UpgradeReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        verify(exactly = 1) { OneSignalDispatchers.prewarm() }
    }

    test("NotificationDismissReceiver.onReceive prewarms before dispatch") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationDismissReceiver().onReceive(context, Intent())
        verify(exactly = 1) { OneSignalDispatchers.prewarm() }
    }
})
