package com.onesignal.onesignal.internal.notification

import com.onesignal.onesignal.notification.INotification
import com.onesignal.onesignal.notification.INotificationReceivedEvent

internal class NotificationReceivedEvent(
    override val notification: Notification
        ) : INotificationReceivedEvent {

    override fun complete(notification: INotification?) {

    }
}