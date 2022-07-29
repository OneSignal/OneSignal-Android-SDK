package com.onesignal.onesignal.notification

/**
 * An interface used to process a OneSignal notification the user just tapped on.
 * <br></br>
 * Set this during OneSignal init in
 * [INotificationsManager.setNotificationOpenedHandler]
 * <br></br><br></br>
 * @see [NotificationOpenedHandler | OneSignal Docs](https://documentation.onesignal.com/docs/android-native-sdk.notificationopenedhandler)
 */
interface INotificationOpenedHandler {
    /**
     * Fires when a user taps on a notification.
     *
     * @param result a [INotificationOpenedResult] with the user's response and properties of this notification
     */
    fun notificationOpened(result: INotificationOpenedResult?)
}