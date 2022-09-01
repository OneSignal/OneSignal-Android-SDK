package com.onesignal.notification.internal

import com.onesignal.notification.INotification
import com.onesignal.notification.INotificationReceivedEvent

internal class NotificationReceivedEvent(
    override val notification: Notification
) : INotificationReceivedEvent {

    var effectiveNotification: Notification? = notification

    override fun complete(notification: INotification?) {
        effectiveNotification = notification as Notification
    }
}
