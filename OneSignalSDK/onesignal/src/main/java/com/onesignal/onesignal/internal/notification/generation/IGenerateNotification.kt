package com.onesignal.onesignal.internal.notification.generation

import com.onesignal.onesignal.internal.notification.work.NotificationGenerationJob

interface IGenerateNotification {
    fun displayNotification(notificationJob: NotificationGenerationJob): Boolean
}