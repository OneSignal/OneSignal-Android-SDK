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
     * @param onesignalId The backend-assigned OneSignal id of the user the subscription belonged to
     * when the notification was received, or null when the SDK did not have one.
     */
    suspend fun sendReceiveReceipt(
        appId: String,
        subscriptionId: String,
        notificationId: String,
        onesignalId: String?,
    )
}
