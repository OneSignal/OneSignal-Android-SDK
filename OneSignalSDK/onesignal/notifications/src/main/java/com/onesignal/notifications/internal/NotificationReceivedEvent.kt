package com.onesignal.notifications.internal

import com.onesignal.notifications.INotification
import com.onesignal.notifications.INotificationReceivedEvent

internal class NotificationReceivedEvent(
    override val notification: Notification
) : INotificationReceivedEvent {

    var effectiveNotification: Notification? = notification

    override fun complete(notification: INotification?) {
        effectiveNotification = notification as Notification?
    }
}
