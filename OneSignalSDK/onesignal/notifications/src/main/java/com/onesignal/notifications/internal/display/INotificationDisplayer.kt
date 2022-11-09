package com.onesignal.notifications.internal.display

import com.onesignal.notifications.internal.common.NotificationGenerationJob

interface INotificationDisplayer {
    suspend fun displayNotification(notificationJob: NotificationGenerationJob): Boolean
}
