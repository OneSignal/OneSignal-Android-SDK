package com.onesignal.notification

import android.content.Context

/**
 * Implement this interface on a class with a default public constructor and provide class with namespace
 * as a value to a new `meta-data` tag with the key name of "com.onesignal.NotificationServiceExtension" in
 * your AndroidManifest.xml.
 * ex. <meta-data android:name="com.onesignal.NotificationServiceExtension" android:value="com.company.MyNotificationExtensionService"></meta-data>
 * <br></br><br></br>
 * Allows for modification of a notification by calling [OSNotification.mutableCopy]
 * instance and passing it into [OSMutableNotification.setExtender]
 * To display the notification, call [OSNotificationReceivedEvent.complete] with a notification instance.
 * To omit displaying a notification call [OSNotificationReceivedEvent.complete] with null.
 */
interface IRemoteNotificationReceivedHandler {
    fun remoteNotificationReceived(context: Context, notificationReceivedEvent: INotificationReceivedEvent)
}
