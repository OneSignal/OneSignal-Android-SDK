package com.onesignal.notifications.internal.restoration

import com.onesignal.notifications.internal.common.NotificationRestoreReason
import com.onesignal.notifications.internal.data.INotificationRepository

internal interface INotificationRestoreProcessor {
    /** Returns true only when every outstanding notification's work was persisted. */
    suspend fun process(): Boolean

    suspend fun processNotification(
        notification: INotificationRepository.NotificationData,
        reason: NotificationRestoreReason,
        delay: Int = 0,
    ): Boolean
}
