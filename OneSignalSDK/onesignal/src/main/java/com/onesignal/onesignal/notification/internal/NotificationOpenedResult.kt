package com.onesignal.onesignal.notification.internal

import com.onesignal.onesignal.notification.INotification
import com.onesignal.onesignal.notification.INotificationAction
import com.onesignal.onesignal.notification.INotificationOpenedResult

/**
 * The data provided to [INotificationOpenedHandler.notificationOpened] when a notification
 * has been opened by the user.
 */
class NotificationOpenedResult(
    /** The notification that was opened by the user. **/
    override val notification: INotification,

    /** The action the user took to open the notification. **/
    override val action: INotificationAction
) : INotificationOpenedResult
