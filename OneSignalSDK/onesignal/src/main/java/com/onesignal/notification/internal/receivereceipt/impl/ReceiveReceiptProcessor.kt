package com.onesignal.notification.internal.receivereceipt.impl

import com.onesignal.core.internal.backend.BackendException
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

        try {
            _backend.updateNotificationAsReceived(appId, notificationId, subscriptionId, deviceType)
        } catch (ex: BackendException) {
            Logging.error("Receive receipt failed with statusCode: ${ex.statusCode} response: ${ex.response}")
        }
    }
}
