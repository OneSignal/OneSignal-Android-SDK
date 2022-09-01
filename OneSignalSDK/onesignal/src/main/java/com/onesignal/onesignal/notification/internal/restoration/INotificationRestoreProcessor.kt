package com.onesignal.onesignal.notification.internal.restoration

import com.onesignal.onesignal.notification.internal.data.INotificationDataController

interface INotificationRestoreProcessor {
    suspend fun process()

    suspend fun processNotification(notification: INotificationDataController.NotificationData, delay: Int = 0)
}
