package com.onesignal.onesignal.notification.internal.receipt

interface IReceiveReceiptService {
    /**
     * Indicate that the notification with ID provided has been
     * received on the device.
     *
     * @param osNotificationId The OneSignal notification ID
     */
    fun notificationReceived(osNotificationId: String)
}