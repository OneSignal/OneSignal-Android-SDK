package com.onesignal.onesignal.notification.internal.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.BaseColumns
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.onesignal.core.internal.database.impl.OneSignalDbContract
import com.onesignal.onesignal.notification.internal.NotificationHelper
import com.onesignal.onesignal.notification.internal.generation.IGenerateNotification
import com.onesignal.onesignal.notification.internal.generation.NotificationGenerationJob
import com.onesignal.onesignal.notification.internal.restoration.NotificationRestoreProcessor
import com.onesignal.onesignal.core.internal.params.IParamsService
import com.onesignal.onesignal.core.internal.logging.Logging
import org.json.JSONException
import org.json.JSONObject

internal class NotificationSummaryManager(
    private val _applicationService: IApplicationService,
    private val _dataController: INotificationDataController,
    private val _generateNotification: IGenerateNotification,
    private val _paramsService: IParamsService,
    private val _notificationRestoreProcessor: NotificationRestoreProcessor
) {

    // A notification was just dismissed, check if it was a child to a summary notification and update it.
    suspend fun updatePossibleDependentSummaryOnDismiss(androidNotificationId: Int) {
        val groupId = _dataController.getGroupId(androidNotificationId)

        if (groupId != null)
            internalUpdateSummaryNotificationAfterChildRemoved(groupId, true)
    }

    // Called from an opened / dismissed / cancel event of a single notification to update it's parent the summary notification.
    suspend fun updateSummaryNotificationAfterChildRemoved(group: String, dismissed: Boolean) {
        internalUpdateSummaryNotificationAfterChildRemoved(group, dismissed)
    }

    private suspend fun internalUpdateSummaryNotificationAfterChildRemoved(group: String, dismissed: Boolean) {

        var notifications = _dataController.listNotificationsForGroup(group)

        val notificationsInGroup = notifications.count()

        val androidNotifId = _dataController.getAndroidIdForGroup(group, true) ?: return

        // If all individual notifications consumed
        //   - Remove summary notification from the shade.
        //   - Mark summary notification as consumed.
        if (notificationsInGroup == 0) {
            // Remove the summary notification from the shade.
            val notificationManager = NotificationHelper.getNotificationManager(_applicationService.appContext!!)
            notificationManager.cancel(androidNotifId)

            // Mark the summary notification as opened or dismissed.
            _dataController.markAsConsumed(androidNotifId, dismissed)
            return
        }

        // Only a single notification now in the group
        //   - Need to recreate a summary notification so it looks like a normal notifications since we
        //        only have one notification now.
        if (notificationsInGroup == 1) {
            restoreSummary(group)
            return
        }

        // 2 or more still left in the group
        //  - Just need to update the summary notification.
        //  - Don't need start a broadcast / service as the extender doesn't support overriding
        //      the summary notification.
        try {
            var firstNotification = notifications.first()
            val notificationJob = NotificationGenerationJob()
            notificationJob.isRestoring = true
            notificationJob.shownTimeStamp = firstNotification.createdAt
            notificationJob.jsonPayload = JSONObject(firstNotification.fullData)
            _generateNotification.updateSummaryNotification(notificationJob)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private suspend fun restoreSummary(group: String) {
        val notifications = _dataController.listNotificationsForGroup(group)
        for (notification in notifications)
            _notificationRestoreProcessor.processNotification(notification)
    }

    /**
     * Clears notifications from the status bar based on a few parameters
     */
    suspend fun clearNotificationOnSummaryClick(group: String) {
        val notificationManager = NotificationHelper.getNotificationManager(_applicationService.appContext!!)
        // Obtain the most recent notification id
        val mostRecentId = _dataController.getAndroidIdForGroup(group, false)
        if (mostRecentId != null) {
            val shouldDismissAll = _paramsService.clearGroupOnSummaryClick
            if (shouldDismissAll) {

                val groupId = if (group == NotificationHelper.grouplessSummaryKey) {
                    // If the group is groupless, obtain the hardcoded groupless summary id
                    NotificationHelper.grouplessSummaryId
                }
                else {
                    // Obtain the group to clear notifications from
                    _dataController.getAndroidIdForGroup(group, true)
                }

                // Clear the entire notification summary
                if (groupId != null)
                    notificationManager.cancel(groupId)

            } else {
                // Clear the most recent notification from the status bar summary
                _dataController.markAsDismissed(mostRecentId)
            }
        }
    }
}