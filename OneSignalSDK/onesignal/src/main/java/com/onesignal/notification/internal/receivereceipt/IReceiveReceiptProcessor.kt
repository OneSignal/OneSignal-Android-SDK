package com.onesignal.notification.internal.receivereceipt

/**
 * Sends confirmation of receiving a notification has been established. Most
 * likely the [IReceiveReceiptWorkManager] should be used as that has a higher
 * assurance of success.
 */
interface IReceiveReceiptProcessor {

    /**
     * Send the receive receipt to the backend on the current thread.
     *
     * @param notificationId The id of the notification that has been received.
     */
    suspend fun sendReceiveReceipt(notificationId: String)
}
