package com.onesignal.onesignal.notification.internal

import com.onesignal.onesignal.notification.INotification
import com.onesignal.onesignal.notification.INotificationReceivedEvent

internal class NotificationReceivedEvent(
    override val notification: Notification
        ) : INotificationReceivedEvent {

    var effectiveNotification: Notification? = notification

    override fun complete(notification: INotification?) {
        effectiveNotification = notification as Notification
    }
}