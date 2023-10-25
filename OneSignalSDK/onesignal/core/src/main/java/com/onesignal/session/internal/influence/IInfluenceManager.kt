package com.onesignal.session.internal.influence

interface IInfluenceManager {
    /**
     * The influences being tracked.
     */
    val influences: List<Influence>

    /**
     * Indicate a notification has been received by the SDK.
     *
     * @param notificationId The ID of the notification that has been received.
     */
    fun onNotificationReceived(notificationId: String)

    /**
     * Indicate a notification has directly influenced the user.
     *
     * @param
     */
    fun onDirectInfluenceFromNotification(notificationId: String)

    /**
     * Indicate an IAM has been received by the SDK.
     *
     * @param messageId The ID of the IAM that has been received.
     */
    fun onInAppMessageDisplayed(messageId: String)

    /**
     * Indicate an IAM has directly influenced the user.
     */
    fun onDirectInfluenceFromIAM(messageId: String)

    /**
     * Indicate the IAM has been dismissed.
     */
    fun onInAppMessageDismissed()
}
