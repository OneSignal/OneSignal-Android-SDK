package com.onesignal.onesignal.internal.notification

import com.onesignal.onesignal.internal.notification.work.NotificationController
import com.onesignal.onesignal.notification.INotification
import com.onesignal.onesignal.notification.INotificationReceivedEvent

internal class NotificationReceivedEvent(
    private val notificationController: NotificationController,
    override val notification: INotification
        ) : INotificationReceivedEvent {

    override fun complete(notification: INotification?) {

    }
}