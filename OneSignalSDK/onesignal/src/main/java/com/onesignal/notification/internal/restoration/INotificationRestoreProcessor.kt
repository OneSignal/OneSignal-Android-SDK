package com.onesignal.notification.internal.restoration

import com.onesignal.notification.internal.data.INotificationRepository

internal interface INotificationRestoreProcessor {
    suspend fun process()

    suspend fun processNotification(notification: INotificationRepository.NotificationData, delay: Int = 0)
}
