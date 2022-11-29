package com.onesignal.notifications.internal

import com.onesignal.notifications.INotification
import com.onesignal.notifications.INotificationAction
import com.onesignal.notifications.INotificationClickHandler
import com.onesignal.notifications.INotificationClickResult

/**
 * The data provided to [INotificationClickHandler.notificationClicked] when a notification
 * has been opened by the user.
 */
class NotificationClickResult(
    /** The notification that was opened by the user. **/
    override val notification: INotification,

    /** The action the user took to open the notification. **/
    override val action: INotificationAction
) : INotificationClickResult
