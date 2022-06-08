package com.onesignal.onesignal.notification

/**
 * Meant to be implemented with [INotificationsManager.setNotificationWillShowInForegroundHandler]
 * <br></br><br></br>
 * Call [INotificationReceivedEvent.complete] with null
 * for not displaying notification or [IMutableNotification] to modify notification before displaying.
 * If [INotificationReceivedEvent.complete] is not called within 25 seconds, original notification will be displayed.
 * <br></br><br></br>
 * TODO: Update docs with new NotificationReceivedHandler
 * @see [NotificationReceivedHandler | OneSignal Docs](https://documentation.onesignal.com/docs/android-native-sdk.notificationreceivedhandler)
 */
interface INotificationWillShowInForegroundHandler {
    fun notificationWillShowInForeground(notificationReceivedEvent: INotificationReceivedEvent?)
}