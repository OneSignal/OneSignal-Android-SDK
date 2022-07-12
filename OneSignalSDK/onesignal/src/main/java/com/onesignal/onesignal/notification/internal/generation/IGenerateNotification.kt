package com.onesignal.onesignal.notification.internal.generation

import com.onesignal.onesignal.notification.internal.work.NotificationGenerationJob

interface IGenerateNotification {
    suspend fun displayNotification(notificationJob: NotificationGenerationJob): Boolean
    suspend fun updateSummaryNotification(notificationJob: NotificationGenerationJob)
}