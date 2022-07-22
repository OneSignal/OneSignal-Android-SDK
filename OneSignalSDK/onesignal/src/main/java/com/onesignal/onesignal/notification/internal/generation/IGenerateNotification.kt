package com.onesignal.onesignal.notification.internal.generation

interface IGenerateNotification {
    suspend fun displayNotification(notificationJob: NotificationGenerationJob): Boolean
    suspend fun updateSummaryNotification(notificationJob: NotificationGenerationJob)
}