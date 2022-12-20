package com.onesignal.notifications

/**
 * An interface used to process a OneSignal notification the user just clicked on.
 * <br></br>
 * Set this during OneSignal init in
 * [INotificationsManager.setNotificationClickHandler]
 * <br></br><br></br>
 * @see [NotificationOpenedHandler | OneSignal Docs](https://documentation.onesignal.com/docs/android-native-sdk.notificationopenedhandler)
 */
interface INotificationClickHandler {
    /**
     * Called when a user clicks on a notification.
     *
     * @param result an [INotificationClickResult] with the user's response and properties of this notification.
     */
    fun notificationClicked(result: INotificationClickResult)
}
