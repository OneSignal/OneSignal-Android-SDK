package com.onesignal.notifications

/**
 * An interface used to process a OneSignal notification the user just clicked on.
 * <br></br>
 * Set this during OneSignal init in
 * [INotificationsManager.addClickListener]
 * <br></br><br></br>
 * @see [NotificationOpenedHandler | OneSignal Docs](https://documentation.onesignal.com/docs/android-native-sdk.notificationopenedhandler)
 */
interface INotificationClickListener {
    /**
     * Called when a user clicks on a notification.
     *
     * @param event an [INotificationClickEvent] with the user's response and properties of this notification.
     */
    fun onClick(event: INotificationClickEvent)
}
