package com.onesignal.notification

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
     * Called when a user taps on a notification to open it.
     *
     * @param result an [INotificationOpenedResult] with the user's response and properties of this notification
     */
    fun notificationOpened(result: INotificationOpenedResult)
}
