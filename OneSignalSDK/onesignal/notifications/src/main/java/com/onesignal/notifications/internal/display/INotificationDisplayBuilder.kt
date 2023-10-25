package com.onesignal.notifications.internal.display

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import com.onesignal.notifications.internal.common.NotificationGenerationJob
import com.onesignal.notifications.internal.display.impl.IntentGeneratorForAttachingToNotifications
import com.onesignal.notifications.internal.display.impl.NotificationDisplayBuilder
import org.json.JSONObject

internal interface INotificationDisplayBuilder {
    val defaultLargeIcon: Bitmap?
    val defaultSmallIconId: Int

    // NotificationCompat unfortunately doesn't correctly support some features
    // such as sounds and heads-up notifications with GROUP_ALERT_CHILDREN on
    // Android 6.0 and older.
    // This includes:
    //    Android 6.0 - No Sound or heads-up
    //    Android 5.0 - Sound, but no heads-up
    fun getGroupAlertBehavior(): Int

    fun getTitle(fcmJson: JSONObject): CharSequence

    fun getNewDismissActionPendingIntent(
        requestCode: Int,
        intent: Intent,
    ): PendingIntent

    fun getNewBaseDismissIntent(notificationId: Int): Intent

    fun getBaseOneSignalNotificationBuilder(notificationJob: NotificationGenerationJob): NotificationDisplayBuilder.OneSignalNotificationBuilder

    fun removeNotifyOptions(builder: NotificationCompat.Builder?)

    // Xiaomi requires the following to show a custom notification icons.
    // Without this MIUI 8 will only show the app icon on the left.
    //  When a large icon is set the small icon will no longer show.
    fun addXiaomiSettings(
        oneSignalNotificationBuilder: NotificationDisplayBuilder.OneSignalNotificationBuilder?,
        notification: Notification,
    )

    fun addNotificationActionButtons(
        fcmJson: JSONObject,
        intentGenerator: IntentGeneratorForAttachingToNotifications,
        mBuilder: NotificationCompat.Builder?,
        notificationId: Int,
        groupSummary: String?,
    )
}
