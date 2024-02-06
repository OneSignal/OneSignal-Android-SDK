package com.onesignal.notifications.internal.restoration

import com.onesignal.notifications.internal.data.INotificationRepository

internal interface INotificationRestoreProcessor {
    suspend fun process()

    suspend fun processNotification(
        notification: INotificationRepository.NotificationData,
        delay: Int = 0,
    )
}
