package com.onesignal.notification.internal.receivereceipt.impl

import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.logging.Logging
import com.onesignal.notification.internal.backend.INotificationBackendService
import com.onesignal.notification.internal.receivereceipt.IReceiveReceiptProcessor

internal class ReceiveReceiptProcessor(
    private val _deviceService: IDeviceService,
    private val _backend: INotificationBackendService
) : IReceiveReceiptProcessor {

    override suspend fun sendReceiveReceipt(appId: String, subscriptionId: String, notificationId: String) {
        val deviceType = _deviceService.deviceType

        val response = _backend.updateNotificationAsReceived(appId, notificationId, subscriptionId, deviceType)
        if (response.isSuccess) {
            Logging.debug("Receive receipt sent for notificationID: $notificationId")
        } else {
            Logging.error("Receive receipt failed with statusCode: ${response.statusCode} response: ${response.payload}")
        }
    }
}
