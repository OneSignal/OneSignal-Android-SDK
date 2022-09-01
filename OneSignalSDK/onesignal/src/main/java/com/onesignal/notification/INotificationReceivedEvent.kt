package com.onesignal.notification

/**
 * The event passed into both [IRemoteNotificationReceivedHandler.remoteNotificationReceived] and
 * [INotificationWillShowInForegroundHandler.notificationWillShowInForeground], provides access
 * to the received notification and the ability to change how (or whether) that notification will
 * be displayed to the user.
 */
interface INotificationReceivedEvent {

    /**
     * The notification that has been received.
     */
    val notification: INotification

    /**
     * Call this to continue with notification processing with the provided [INotification].
     * User must call complete within 25 seconds or the original notification will be displayed.
     *
     * @param notification can be null to omit displaying the notification, the original
     * [INotification] found in [notification], or an [IMutableNotification] to modify the
     * notification that is displayed.
     */
    fun complete(notification: INotification?)
}
