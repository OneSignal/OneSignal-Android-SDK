package com.onesignal.onesignal.internal.notification.generation

import com.onesignal.onesignal.internal.notification.work.NotificationGenerationJob

interface IGenerateNotification {
    suspend fun displayNotification(notificationJob: NotificationGenerationJob): Boolean
    suspend fun updateSummaryNotification(notificationJob: NotificationGenerationJob)
}