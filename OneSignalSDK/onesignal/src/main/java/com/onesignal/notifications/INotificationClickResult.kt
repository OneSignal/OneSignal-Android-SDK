package com.onesignal.notifications

/**
 * The data provided to [INotificationClickHandler.notificationClicked] when a notification
 * has been opened by the user.
 */
interface INotificationClickResult {
    /**
     * The notification that was opened by the user.
     */
    val notification: INotification

    /**
     * The action the user took to open the notification.
     */
    val action: INotificationAction
}
