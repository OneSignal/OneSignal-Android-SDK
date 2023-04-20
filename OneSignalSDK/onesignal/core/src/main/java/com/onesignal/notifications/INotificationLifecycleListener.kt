package com.onesignal.notifications

/**
 * Implement this interface and provide an instance to [INotificationsManager.addForegroundLifecycleListener]
 * in order to receive control during the lifecycle of the notification. This handler will *only* receive
 * control when the application is in the foreground when the notification is received.
 *
 * @see [Foreground Notification Received Event | OneSignal Docs](https://documentation.onesignal.com/docs/sdk-notification-event-handlers#foreground-notification-received-event)
 */
interface INotificationLifecycleListener {

    /**
     * Called when a notification is to be displayed to the user. This callback
     * gives the implementor the ability to prevent the notification from displaying to the
     * user.
     *
     * *Note:* this runs after the Notification Service Extension [INotificationServiceExtension]
     * has been called (if one exists), which has the following differences:
     *
     * 1. The [INotificationServiceExtension] is configured within your `AndroidManifest.xml`.
     * 2. The [INotificationServiceExtension] will be called regardless of the state of your
     *    app, while [willDisplay] is *only* called when your app is in focus.
     * 3. The [INotificationServiceExtension] can make changes to the notification, while
     *    [willDisplay] can only indicate not to show it.
     *
     * @param event The notification will display event information.
     */
    fun onWillDisplay(event: INotificationWillDisplayEvent)
}
