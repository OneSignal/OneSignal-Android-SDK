package com.onesignal.onesignal.internal.notification

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    /**
     * Determine whether notifications are enabled/disabled either for the entire app or for
     * a specific channel within the application.  Note, channels are only applicable beyond
     * API 26.
     *
     * @param context The app context to check notification enablement against.
     * @param channelId The optional channel ID to check enablement for.
     */
    fun areNotificationsEnabled(context: Context, channelId: String? = null) : Boolean {
        try {
            val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (!notificationsEnabled)
                return false

            // Channels were introduced in O
            if(channelId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                val channel = getNotificationManager(context)?.getNotificationChannel(channelId)
                return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
            }
        } catch (t: Throwable) {
        }
        return true
    }

    fun getNotificationManager(context: Context): NotificationManager? {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
}