package com.onesignal.notifications.internal.restoration.impl

import android.os.Build
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.badges.IBadgeCountUpdater
import com.onesignal.notifications.internal.common.NotificationHelper
import com.onesignal.notifications.internal.data.INotificationRepository
import com.onesignal.notifications.internal.generation.INotificationGenerationWorkManager
import com.onesignal.notifications.internal.restoration.INotificationRestoreProcessor
import kotlinx.coroutines.delay
import org.json.JSONObject

internal class NotificationRestoreProcessor(
    private val _applicationService: IApplicationService,
    private val _workManager: INotificationGenerationWorkManager,
    private val _dataController: INotificationRepository,
    private val _badgeCountUpdater: IBadgeCountUpdater,
) : INotificationRestoreProcessor {
    override suspend fun process() {
        Logging.info("Restoring notifications")

        try {
            var excludeAndroidIds = getVisibleNotifications()
            var outstandingNotifications = _dataController.listNotificationsForOutstanding(excludeAndroidIds)

            for (notification in outstandingNotifications) {
                processNotification(notification, DELAY_BETWEEN_NOTIFICATION_RESTORES_MS)
            }

            _badgeCountUpdater.update()
        } catch (t: Throwable) {
            Logging.warn("Error restoring notification records! ", t)
        }
    }

    override suspend fun processNotification(
        notification: INotificationRepository.NotificationData,
        delay: Int,
    ) {
        _workManager.beginEnqueueingWork(
            _applicationService.appContext,
            notification.id,
            notification.androidId,
            JSONObject(notification.fullData),
            notification.createdAt,
            true,
            false,
        )

        if (delay > 0) {
            delay(delay.toLong())
        }
    }

    /**
     * Retrieve the list of notifications that are currently in the shade
     * this is used to prevent notifications from being restored twice in M and newer.
     * This is important mostly for Android O as they can't be redisplayed in a silent way unless
     * they are displayed under a different channel which isn't ideal.
     * For pre-O devices this still have the benefit of being more efficient
     */
    private fun getVisibleNotifications(): List<Int>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val activeNotifs = NotificationHelper.getActiveNotifications(_applicationService.appContext)
        if (activeNotifs.isEmpty()) return null
        val activeNotifIds = mutableListOf<Int>()
        for (activeNotif in activeNotifs)
            activeNotifIds.add(activeNotif.id)

        return activeNotifIds
    }

    companion object {
        // Delay to prevent logcat messages and possibly skipping some notifications
        //    This prevents the following error;
        // E/NotificationService: Package enqueue rate is 10.56985. Shedding events. package=####
        private const val DELAY_BETWEEN_NOTIFICATION_RESTORES_MS = 200
        const val DEFAULT_TTL_IF_NOT_IN_PAYLOAD = 259200
    }
}
