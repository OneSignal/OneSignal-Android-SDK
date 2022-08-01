package com.onesignal.onesignal.notification.internal

import com.onesignal.onesignal.notification.INotification
import com.onesignal.onesignal.notification.INotificationReceivedEvent

internal class NotificationReceivedEvent(
    override val notification: Notification
        ) : INotificationReceivedEvent {

    override fun complete(notification: INotification?) {

    }
}