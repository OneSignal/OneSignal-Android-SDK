package com.onesignal.notification

/**
 * Implement this interface and provide an instance to [INotificationsManager.setNotificationWillShowInForegroundHandler]
 * in order to receive control prior to a notification being shown to the user.  This allows for
 * the viewing, modifying, and possibly removing the display of a notification.
 *
 * This handler will *only* receive control when the application is in the foreground when the
 * notification is received.
 *
 * @see [Foreground Notification Received Event | OneSignal Docs](https://documentation.onesignal.com/docs/sdk-notification-event-handlers#foreground-notification-received-event)
 */
interface INotificationWillShowInForegroundHandler {

    /**
     * Called when a notification has been received, prior to it being displayed to the user.  This method
     * gives the implementor the ability to modify or prevent the notification from displaying to the
     * user entirely.
     *
     * * The notification received can be accessed via [INotificationReceivedEvent.notification]
     * * The notification can be modified via [INotification.mutableCopy] and passing an extender to
     *   [IMutableNotification.setExtender].
     *
     * To display the notification, call [INotificationReceivedEvent.complete] with a notification instance.
     * To omit displaying a notification call [INotificationReceivedEvent.complete] with null.
     *
     * @param notificationReceivedEvent Data and methods to control what should be done with the received notification.
     */
    fun notificationWillShowInForeground(notificationReceivedEvent: INotificationReceivedEvent)
}
