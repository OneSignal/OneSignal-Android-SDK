package com.onesignal.notification.internal.display

import com.onesignal.notification.internal.common.NotificationGenerationJob

internal interface INotificationDisplayer {
    suspend fun displayNotification(notificationJob: NotificationGenerationJob): Boolean
}
