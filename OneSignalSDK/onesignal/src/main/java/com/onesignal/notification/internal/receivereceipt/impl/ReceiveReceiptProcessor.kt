package com.onesignal.notification.internal.receivereceipt.impl

import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.user.ISubscriptionManager
import com.onesignal.notification.internal.backend.INotificationBackendService
import com.onesignal.notification.internal.receivereceipt.IReceiveReceiptProcessor

internal class ReceiveReceiptProcessor(
    private val _deviceService: IDeviceService,
    private val _subscriptionManager: ISubscriptionManager,
    private val _configModelStore: ConfigModelStore,
    private val _backend: INotificationBackendService
) : IReceiveReceiptProcessor {

    override suspend fun sendReceiveReceipt(notificationId: String) {
        // TODO: There is a potential problem where this could get called before these things are setup.  Typically if there is
        //       outstanding work and we are starting up again.  How to resolve?
        val config = _configModelStore.get()
        val appId: String = config.appId ?: ""
        val subscriptionId: String = _subscriptionManager.subscriptions.push?.id.toString()
        val deviceType = _deviceService.deviceType

        val response = _backend.updateNotificationAsReceived(appId, notificationId, subscriptionId, deviceType)
        if (response.isSuccess) {
            Logging.debug("Receive receipt sent for notificationID: $notificationId")
        } else {
            Logging.error("Receive receipt failed with statusCode: ${response.statusCode} response: ${response.payload}")
        }
    }
}
