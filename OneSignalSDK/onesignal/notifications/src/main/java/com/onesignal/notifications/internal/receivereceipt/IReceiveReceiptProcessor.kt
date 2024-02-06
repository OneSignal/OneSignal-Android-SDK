package com.onesignal.notifications.internal.receivereceipt

/**
 * Sends confirmation of receiving a notification has been established. Most
 * likely the [IReceiveReceiptWorkManager] should be used as that has a higher
 * assurance of success.
 */
internal interface IReceiveReceiptProcessor {
    /**
     * Send the receive receipt to the backend on the current thread.
     *
     * @param appId The id of the application the notification was received under.
     * @param subscriptionId The id of the subscription the notification was received under.
     * @param notificationId The id of the notification that has been received.
     */
    suspend fun sendReceiveReceipt(
        appId: String,
        subscriptionId: String,
        notificationId: String,
    )
}
