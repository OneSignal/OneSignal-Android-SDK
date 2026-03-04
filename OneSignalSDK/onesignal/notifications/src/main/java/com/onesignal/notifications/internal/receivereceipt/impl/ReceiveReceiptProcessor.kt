package com.onesignal.notifications.internal.receivereceipt.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.backend.INotificationBackendService
import com.onesignal.notifications.internal.receivereceipt.IReceiveReceiptProcessor

internal class ReceiveReceiptProcessor(
    private val _deviceService: IDeviceService,
    private val _backend: INotificationBackendService,
) : IReceiveReceiptProcessor {
    override suspend fun sendReceiveReceipt(
        appId: String,
        subscriptionId: String,
        notificationId: String,
    ) {
        val deviceType = _deviceService.deviceType

        try {
            _backend.updateNotificationAsReceived(appId, notificationId, subscriptionId, deviceType)
        } catch (ex: BackendException) {
            Logging.info("Receive receipt failed with statusCode: ${ex.statusCode} response: ${ex.response}")
        }
    }
}
