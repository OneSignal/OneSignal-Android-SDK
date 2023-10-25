package com.onesignal.notifications.internal.receivereceipt

/**
 * Ensures confirmation of receiving a notification has been established via
 * the Android Work Manager.  Typically this should be used over [IReceiveReceiptProcessor]
 * as the worker will persist across the application lifecycle.
 */
internal interface IReceiveReceiptWorkManager {
    /**
     * Enqueue a worker which will send receipt of receiving a notification.
     *
     * @param notificationId The id of the notification that has been received.
     */
    fun enqueueReceiveReceipt(notificationId: String)
}
