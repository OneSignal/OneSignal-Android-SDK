package com.onesignal.notifications

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
interface INotificationServiceExtension {
    /**
     * Called when a notification has been received by the device. This method
     * gives the implementor the ability to modify or prevent the notification from displaying to the
     * user entirely.
     *
     * @param event The notification received event information.
     */
    fun onNotificationReceived(event: INotificationReceivedEvent)
}
