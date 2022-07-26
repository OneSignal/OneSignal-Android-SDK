package com.onesignal.onesignal.notification.internal.receivereceipt.impl

import com.onesignal.onesignal.core.internal.backend.api.ApiException
import com.onesignal.onesignal.core.internal.device.IDeviceService
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.onesignal.core.user.IUserManager
import com.onesignal.onesignal.notification.internal.receivereceipt.IReceiveReceiptProcessor
import java.lang.NullPointerException

internal class ReceiveReceiptProcessor(
    private var _deviceService: IDeviceService,
            private var _userManager: IUserManager,
            private var _configModelStore: ConfigModelStore) : IReceiveReceiptProcessor {

    override suspend fun sendReceiveReceipt(notificationId: String) {
        val config = _configModelStore.get()
        val appId: String = config!!.appId ?: ""
        val playerId: String = _userManager!!.subscriptions.push?.id.toString()

        var deviceType: Int? = null

        try {
            deviceType = _deviceService!!.deviceType
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }

        val finalDeviceType = deviceType
        Logging.debug("ReceiveReceiptWorker: Device Type is: $finalDeviceType")

        try {
            // TODO: Implement
            //_apiService!!.updateNotificationAsReceived(appId, notificationId, playerId, deviceType)
            Logging.debug("Receive receipt sent for notificationID: $notificationId")
        } catch (ae: ApiException) {
            Logging.error("Receive receipt failed with statusCode: ${ae.statusCode} response: $ae.response")
        }
    }
}