package com.onesignal.notification

import android.content.Context

/**
 * Implement this interface on a class with a default public constructor and provide class with namespace
 * as a value to a new `meta-data` tag with the key name of "com.onesignal.NotificationServiceExtension" in
 * your AndroidManifest.xml.
 *
 * ex. <meta-data android:name="com.onesignal.NotificationServiceExtension" android:value="com.company.MyNotificationExtensionService"></meta-data>
 *
 * Because it is defined in the Android Manifest, it will receive control regardless of the current state
 * of the application when the notification is received (foreground, background, not open).
 *
 * @see [Android Notification Service Extension | OneSignal Docs](https://documentation.onesignal.com/docs/service-extensions#android-notification-service-extension)
 */
interface IRemoteNotificationReceivedHandler {

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
     * @param context The Android context the notification was received under.
     * @param notificationReceivedEvent Data and methods to control what should be done with the received notification.
     */
    fun remoteNotificationReceived(context: Context, notificationReceivedEvent: INotificationReceivedEvent)
}
