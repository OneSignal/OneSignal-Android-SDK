package com.onesignal.onesignal.notification

/**
 * Meant to be implemented with [INotificationsManager.setNotificationWillShowInForegroundHandler]
 * <br></br><br></br>
 * Call [INotificationReceivedEvent.complete] with null
 * for not displaying notification or [IMutableNotification] to modify notification before displaying.
 * If [INotificationReceivedEvent.complete] is not called within 25 seconds, original notification will be displayed.
 * <br></br><br></br>
 *
 * @see [Foreground Notification Received Event | OneSignal Docs](https://documentation.onesignal.com/docs/sdk-notification-event-handlers#foreground-notification-received-event)
 */
interface INotificationWillShowInForegroundHandler {
    fun notificationWillShowInForeground(notificationReceivedEvent: INotificationReceivedEvent?)
}