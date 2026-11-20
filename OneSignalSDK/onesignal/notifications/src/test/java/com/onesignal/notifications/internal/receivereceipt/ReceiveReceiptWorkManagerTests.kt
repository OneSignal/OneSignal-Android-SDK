package com.onesignal.notifications.internal.receivereceipt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.OneSignal
import com.onesignal.common.IDManager
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.internal.common.OSWorkManagerHelper
import com.onesignal.notifications.internal.receivereceipt.impl.ReceiveReceiptWorkManager
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.subscriptions.IPushSubscription
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.robolectric.annotation.Config

@RobolectricTest
@Config(sdk = [28])
class ReceiveReceiptWorkManagerTests : FunSpec({
    lateinit var context: Context
    lateinit var workManager: WorkManager
    lateinit var processor: IReceiveReceiptProcessor

    beforeAny {
        Logging.logLevel = LogLevel.NONE
        context = ApplicationProvider.getApplicationContext()
        workManager = mockk(relaxed = true)
        processor = mockk(relaxed = true)
        mockkObject(OSWorkManagerHelper)
        every { OSWorkManagerHelper.getInstance(any()) } returns workManager
        mockkObject(OneSignal)
        coEvery { OneSignal.initWithContext(any()) } returns true
        every { OneSignal.getService<IReceiveReceiptProcessor>() } returns processor
    }

    afterAny {
        unmockkObject(OneSignal)
        unmockkObject(OSWorkManagerHelper)
    }

    fun enqueueAndCaptureInput(onesignalId: String): Data {
        val applicationService = mockk<IApplicationService>().apply { every { appContext } returns context }
        val subscriptionManager =
            mockk<ISubscriptionManager>().apply {
                every { subscriptions.push } returns
                    mockk<IPushSubscription>().apply { every { id } returns "subscriptionId" }
            }
        val manager =
            ReceiveReceiptWorkManager(
                applicationService,
                MockHelper.configModelStore { it.receiveReceiptEnabled = true },
                subscriptionManager,
                MockHelper.identityModelStore { it.onesignalId = onesignalId },
            )

        manager.enqueueReceiveReceipt("notificationId")

        val request = slot<OneTimeWorkRequest>()
        verify(exactly = 1) {
            workManager.enqueueUniqueWork("notificationId_receive_receipt", ExistingWorkPolicy.KEEP, capture(request))
        }
        return request.captured.workSpec.input
    }

    suspend fun runWorker(input: Data): ListenableWorker.Result {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.inputData } returns input
        return ReceiveReceiptWorkManager.ReceiveReceiptWorker(context, params).doWork()
    }

    test("enqueued receipt carries the backend onesignal id") {
        val input = enqueueAndCaptureInput("onesignalId")

        input.getString("os_onesignal_id") shouldBe "onesignalId"
    }

    test("enqueued receipt leaves out a local placeholder onesignal id") {
        val input = enqueueAndCaptureInput(IDManager.createLocalId())

        input.getString("os_onesignal_id") shouldBe null
    }

    test("worker passes the stored onesignal id to the receipt processor") {
        val input =
            Data.Builder()
                .putString("os_notification_id", "notificationId")
                .putString("os_app_id", "appId")
                .putString("os_subscription_id", "subscriptionId")
                .putString("os_onesignal_id", "onesignalId")
                .build()

        val result = runWorker(input)

        result.javaClass shouldBe ListenableWorker.Result.success().javaClass
        coVerify(exactly = 1) {
            processor.sendReceiveReceipt("appId", "subscriptionId", "notificationId", "onesignalId")
        }
    }

    test("worker passes no onesignal id for work enqueued without one") {
        // Work queued by an SDK version before os_onesignal_id existed has no such key.
        val input =
            Data.Builder()
                .putString("os_notification_id", "notificationId")
                .putString("os_app_id", "appId")
                .putString("os_subscription_id", "subscriptionId")
                .build()

        val result = runWorker(input)

        result.javaClass shouldBe ListenableWorker.Result.success().javaClass
        coVerify(exactly = 1) {
            processor.sendReceiveReceipt("appId", "subscriptionId", "notificationId", null)
        }
    }
})
