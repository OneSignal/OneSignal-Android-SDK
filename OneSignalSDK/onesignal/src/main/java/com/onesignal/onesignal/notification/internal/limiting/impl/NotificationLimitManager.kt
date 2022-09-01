package com.onesignal.onesignal.notification.internal.limiting.impl

import android.os.Build
import androidx.annotation.RequiresApi
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.notification.internal.common.NotificationHelper
import com.onesignal.onesignal.notification.internal.data.INotificationDataController
import com.onesignal.onesignal.notification.internal.limiting.INotificationLimitManager
import com.onesignal.onesignal.notification.internal.summary.INotificationSummaryManager
import java.util.SortedMap
import java.util.TreeMap

internal class NotificationLimitManager(
    private val _dataController: INotificationDataController,
    private val _applicationService: IApplicationService,
    private val _notificationSummaryManager: INotificationSummaryManager,
) : INotificationLimitManager {

    override suspend fun clearOldestOverLimit(notificationsToMakeRoomFor: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                clearOldestOverLimitStandard(notificationsToMakeRoomFor)
            else {
                _dataController.clearOldestOverLimitFallback(notificationsToMakeRoomFor, INotificationLimitManager.Constants.maxNumberOfNotifications)
            }
        } catch (t: Throwable) {
            // try-catch for Android 6.0.X and possibly 8.0.0 bug work around, getActiveNotifications bug
            _dataController.clearOldestOverLimitFallback(notificationsToMakeRoomFor, INotificationLimitManager.Constants.maxNumberOfNotifications)
        }
    }

    // Cancel the oldest notifications based on what the Android system reports is in the shade.
    // This could be any notification, not just a OneSignal notification
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Throws(Throwable::class)
    private suspend fun clearOldestOverLimitStandard(notificationsToMakeRoomFor: Int) {
        val activeNotifs = NotificationHelper.getActiveNotifications(_applicationService.appContext)
        var notificationsToClear =
            activeNotifs.size - INotificationLimitManager.Constants.maxNumberOfNotifications + notificationsToMakeRoomFor
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
            val didDismiss = _dataController.markAsDismissed(value)

            if (didDismiss) {
                _notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(value)
            }

            if (--notificationsToClear <= 0) break
        }
    }
}
