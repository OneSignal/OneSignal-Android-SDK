package com.onesignal.onesignal.notification.internal.display

import com.onesignal.onesignal.notification.internal.common.NotificationGenerationJob

interface INotificationDisplayer {
    suspend fun displayNotification(notificationJob: NotificationGenerationJob): Boolean
}