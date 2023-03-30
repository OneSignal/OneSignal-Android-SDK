package com.onesignal.notifications.internal.display

import android.app.Notification
import androidx.core.app.NotificationCompat
import com.onesignal.notifications.internal.common.NotificationGenerationJob
import com.onesignal.notifications.internal.display.impl.IntentGeneratorForAttachingToNotifications
import com.onesignal.notifications.internal.display.impl.NotificationDisplayBuilder
import org.json.JSONObject

internal interface ISummaryNotificationDisplayer {
    suspend fun createSummaryNotification(
        notificationJob: NotificationGenerationJob,
        notifBuilder: NotificationDisplayBuilder.OneSignalNotificationBuilder?,
        groupAlertBehavior: Int,
    )
    suspend fun updateSummaryNotification(notificationJob: NotificationGenerationJob)
    fun createGenericPendingIntentsForGroup(
        notifBuilder: NotificationCompat.Builder?,
        intentGenerator: IntentGeneratorForAttachingToNotifications,
        gcmBundle: JSONObject,
        group: String,
        notificationId: Int,
    )
    fun createSingleNotificationBeforeSummaryBuilder(
        notificationJob: NotificationGenerationJob,
        notifBuilder: NotificationCompat.Builder?,
    ): Notification
    suspend fun createGrouplessSummaryNotification(
        notificationJob: NotificationGenerationJob,
        intentGenerator: IntentGeneratorForAttachingToNotifications,
        grouplessNotifCount: Int,
        groupAlertBehavior: Int,
    )
}
