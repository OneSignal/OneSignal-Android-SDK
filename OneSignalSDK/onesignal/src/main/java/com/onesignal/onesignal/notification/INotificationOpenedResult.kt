package com.onesignal.onesignal.notification

/**
 * The data provided to [INotificationOpenedHandler.notificationOpened] when a notification
 * has been opened by the user.
 */
interface INotificationOpenedResult {
    /** The notification that was opened by the user. **/
    val notification: INotification

    /** The action the user took to open the notification. **/
    val action: INotificationAction
}
