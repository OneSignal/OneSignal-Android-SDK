package com.onesignal.notifications.receivers

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.OneSignal
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.mocks.IOMockHelper
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder

/**
 * Verifies each cold-start broadcast-receiver entry point makes the explicit
 * [OneSignalDispatchers.prewarm] head-start call before its first dispatch. [IOMockHelper] stubs
 * `suspendifyOnIO` (run inline) and `prewarm()`, so these assert placement/ordering of the explicit
 * call, not end-to-end cold-init behavior.
 *
 * Not covered here (no unit-test path): `ADMMessageHandler` / `ADMMessageHandlerJob` (the
 * `com.amazon.device.messaging.*` base classes are not on the test classpath) and
 * `OneSignalHmsEventBridge` (`com.huawei.hms.push.*`). Those calls are verified by inspection.
 */
@RobolectricTest
class PrewarmEntryPointTests : FunSpec({
    listener(IOMockHelper)

    beforeAny {
        // IOMockHelper owns the OneSignalDispatchers object mock (incl. the prewarm() stub) for the
        // whole spec. Clear only its recorded calls here so each test's prewarm() count starts at
        // zero, while keeping IOMockHelper's stubbed answers (answers = false).
        clearMocks(OneSignalDispatchers, answers = false)
        mockkObject(OneSignal)
        coEvery { OneSignal.initWithContext(any()) } returns false
    }

    afterAny {
        // Tear down only the mock this spec owns. OneSignalDispatchers and the ThreadUtils statics
        // are owned by IOMockHelper and torn down in its afterSpec — unmockkAll() here would strip
        // them mid-spec and break the remaining tests.
        unmockkObject(OneSignal)
    }

    test("BootUpReceiver.onReceive prewarms before dispatch") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BootUpReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        verify(exactly = 1) { OneSignalDispatchers.prewarm() }
        verifyOrder {
            OneSignalDispatchers.prewarm()
            suspendifyOnIO(any<suspend () -> Unit>())
        }
    }

    test("UpgradeReceiver.onReceive prewarms before dispatch") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        UpgradeReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        verify(exactly = 1) { OneSignalDispatchers.prewarm() }
        verifyOrder {
            OneSignalDispatchers.prewarm()
            suspendifyOnIO(any<suspend () -> Unit>())
        }
    }

    test("NotificationDismissReceiver.onReceive prewarms before dispatch") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationDismissReceiver().onReceive(context, Intent())
        verify(exactly = 1) { OneSignalDispatchers.prewarm() }
        verifyOrder {
            OneSignalDispatchers.prewarm()
            suspendifyOnIO(any<suspend () -> Unit>())
        }
    }
})
