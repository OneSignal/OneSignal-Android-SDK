package com.onesignal.notification.internal.restoration

import com.onesignal.notification.internal.data.INotificationDataController

internal interface INotificationRestoreProcessor {
    suspend fun process()

    suspend fun processNotification(notification: INotificationDataController.NotificationData, delay: Int = 0)
}
