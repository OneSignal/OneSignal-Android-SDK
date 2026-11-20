package com.onesignal.notifications.internal.receivereceipt

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.backend.INotificationBackendService
import com.onesignal.notifications.internal.receivereceipt.impl.ReceiveReceiptProcessor
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

class ReceiveReceiptProcessorTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    fun androidDevice() =
        mockk<IDeviceService>().apply {
            every { deviceType } returns IDeviceService.DeviceType.Android
        }

    test("sendReceiveReceipt reports the receipt with the onesignal id captured at enqueue time") {
        // Given
        val mockBackend = mockk<INotificationBackendService>()
        coEvery { mockBackend.updateNotificationAsReceived(any(), any(), any(), any(), any()) } returns Unit
        val processor = ReceiveReceiptProcessor(androidDevice(), mockBackend)

        // When
        processor.sendReceiveReceipt("appId", "subscriptionId", "notificationId", "onesignalId")

        // Then
        coVerify(exactly = 1) {
            mockBackend.updateNotificationAsReceived(
                "appId",
                "notificationId",
                "subscriptionId",
                IDeviceService.DeviceType.Android,
                "onesignalId",
            )
        }
    }

    test("sendReceiveReceipt reports the receipt without a onesignal id when none was captured") {
        // Given: the work manager enqueued the receipt while the user only had a local placeholder
        // id, so it handed the worker null. The processor must pass that through unchanged rather
        // than look the id up again, which could name a user who logged in after the receipt.
        val mockBackend = mockk<INotificationBackendService>()
        coEvery { mockBackend.updateNotificationAsReceived(any(), any(), any(), any(), any()) } returns Unit
        val processor = ReceiveReceiptProcessor(androidDevice(), mockBackend)

        // When
        processor.sendReceiveReceipt("appId", "subscriptionId", "notificationId", null)

        // Then
        coVerify(exactly = 1) {
            mockBackend.updateNotificationAsReceived(
                "appId",
                "notificationId",
                "subscriptionId",
                IDeviceService.DeviceType.Android,
                null,
            )
        }
    }

    test("sendReceiveReceipt swallows a backend failure so the worker does not retry") {
        // Given
        val mockBackend = mockk<INotificationBackendService>()
        coEvery { mockBackend.updateNotificationAsReceived(any(), any(), any(), any(), any()) } throws
            BackendException(500, null)
        val processor = ReceiveReceiptProcessor(androidDevice(), mockBackend)

        // When / Then
        shouldNotThrowAny {
            processor.sendReceiveReceipt("appId", "subscriptionId", "notificationId", "onesignalId")
        }
    }
})
