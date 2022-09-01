package com.onesignal.onesignal.notification

interface INotificationReceivedEvent {
    val notification: INotification

    /**
     * Method to continue with notification processing.
     * User must call complete within 25 seconds or the original notification will be displayed.
     *
     * @param notification can be null to omit displaying the notification,
     * or IMutableNotification to modify the notification to display
     */
    fun complete(notification: INotification?)
}
