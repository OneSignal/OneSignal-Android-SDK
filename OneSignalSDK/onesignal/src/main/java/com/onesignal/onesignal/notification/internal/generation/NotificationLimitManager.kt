package com.onesignal.onesignal.notification.internal.generation

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.onesignal.onesignal.notification.internal.NotificationHelper
import com.onesignal.onesignal.notification.internal.data.INotificationDataController
import java.util.*

// Ensures old notifications are cleared up to a limit before displaying new ones
internal class NotificationLimitManager(
    private val _dataController: INotificationDataController
) {
    // Android does not allow a package to have more than 49 total notifications being shown.
    //   This limit prevents the following error;
    // E/NotificationService: Package has already posted 50 notifications.
    //                        Not showing more.  package=####
    // Even though it says 50 in the error it is really a limit of 49.
    // See NotificationManagerService.java in the AOSP source
    //
    companion object {
        const val maxNumberOfNotificationsInt: Int = 49
    }

    // Used to cancel the oldest notifications to make room for new notifications we are about to display
    // If we don't make this room users will NOT be alerted of new notifications for the app.
    suspend fun clearOldestOverLimit(context: Context, notificationsToMakeRoomFor: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) clearOldestOverLimitStandard(
                context,
                notificationsToMakeRoomFor
            ) else {
                _dataController.clearOldestOverLimitFallback(notificationsToMakeRoomFor, maxNumberOfNotificationsInt)
            }
        } catch (t: Throwable) {
            // try-catch for Android 6.0.X and possibly 8.0.0 bug work around, getActiveNotifications bug
            _dataController.clearOldestOverLimitFallback(notificationsToMakeRoomFor, maxNumberOfNotificationsInt)
        }
    }

    // Cancel the oldest notifications based on what the Android system reports is in the shade.
    // This could be any notification, not just a OneSignal notification
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Throws(Throwable::class)
    suspend fun clearOldestOverLimitStandard(context: Context, notificationsToMakeRoomFor: Int) {
        val activeNotifs = NotificationHelper.getActiveNotifications(context)
        var notificationsToClear =
            activeNotifs.size - maxNumberOfNotificationsInt + notificationsToMakeRoomFor
        // We have enough room in the notification shade, no need to clear any notifications
        if (notificationsToClear < 1) return

        // Create SortedMap so we can sort notifications based on display time
        val activeNotifIds: SortedMap<Long, Int> = TreeMap()
        for (activeNotif in activeNotifs) {
            if (NotificationHelper.isGroupSummary(activeNotif)) continue
            activeNotifIds[activeNotif.notification.`when`] = activeNotif.id
        }

        // Clear the oldest based on the count in notificationsToClear
        for ((_, value) in activeNotifIds) {
            _dataController.removeNotification(value)
            if (--notificationsToClear <= 0) break
        }
    }
}