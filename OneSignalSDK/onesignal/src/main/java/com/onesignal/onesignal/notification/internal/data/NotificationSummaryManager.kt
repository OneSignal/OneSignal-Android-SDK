package com.onesignal.onesignal.notification.internal.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.BaseColumns
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
    private val _databaseProvider: IDatabaseProvider,
    private val _dataController: INotificationDataController,
    private val _generateNotification: IGenerateNotification,
    private val _paramsService: IParamsService,
    private val _notificationRestoreProcessor: NotificationRestoreProcessor
) {

    // A notification was just dismissed, check if it was a child to a summary notification and update it.
    suspend fun updatePossibleDependentSummaryOnDismiss(
        context: Context,
        androidNotificationId: Int
    ) {
        val cursor = _databaseProvider.get().query(
            OneSignalDbContract.NotificationTable.TABLE_NAME,
            arrayOf(OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID),  // retColumn
            OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + androidNotificationId,
            null,
            null,
            null,
            null
        )
        if (cursor.moveToFirst()) {
            val group =
                cursor.getString(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID))
            cursor.close()
            if (group != null) updateSummaryNotificationAfterChildRemoved(context, group, true)
        } else cursor.close()
    }

    // Called from an opened / dismissed / cancel event of a single notification to update it's parent the summary notification.
    suspend fun updateSummaryNotificationAfterChildRemoved(
        context: Context,
        group: String,
        dismissed: Boolean
    ) {
        var cursor: Cursor? = null
        try {
            cursor =
                internalUpdateSummaryNotificationAfterChildRemoved(context, group, dismissed)
        } catch (t: Throwable) {
            Logging.error("Error running updateSummaryNotificationAfterChildRemoved!", t)
        } finally {
            if (cursor != null && !cursor.isClosed) cursor.close()
        }
    }

    private suspend fun internalUpdateSummaryNotificationAfterChildRemoved(context: Context, group: String, dismissed: Boolean): Cursor {
        val cursor = _databaseProvider.get().query(
            OneSignalDbContract.NotificationTable.TABLE_NAME,
            arrayOf(
                OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID,
                OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME,
                OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA
            ),
            OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +  // Where String
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0",
            arrayOf(group),  // whereArgs
            null,
            null,
            BaseColumns._ID + " DESC"
        ) // sort order, new to old);
        val notificationsInGroup = cursor.count

        // If all individual notifications consumed
        //   - Remove summary notification from the shade.
        //   - Mark summary notification as consumed.
        if (notificationsInGroup == 0) {
            cursor.close()
            val androidNotifId = getSummaryNotificationId(group)
                ?: return cursor

            // Remove the summary notification from the shade.
            val notificationManager = NotificationHelper.getNotificationManager(context)
            notificationManager.cancel(androidNotifId)

            // Mark the summary notification as opened or dismissed.
            val values = ContentValues()
            values.put(
                if (dismissed) OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED else OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED,
                1
            )
            _databaseProvider.get().update(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                values,
                OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + androidNotifId,
                null
            )
            return cursor
        }

        // Only a single notification now in the group
        //   - Need to recreate a summary notification so it looks like a normal notifications since we
        //        only have one notification now.
        if (notificationsInGroup == 1) {
            cursor.close()
            val androidNotifId = getSummaryNotificationId(group)
                ?: return cursor
            restoreSummary(context, group)
            return cursor
        }

        // 2 or more still left in the group
        //  - Just need to update the summary notification.
        //  - Don't need start a broadcast / service as the extender doesn't support overriding
        //      the summary notification.
        try {
            cursor.moveToFirst()
            val datetime =
                cursor.getLong(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME))
            val jsonStr =
                cursor.getString(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA))
            cursor.close()
            val androidNotifId = getSummaryNotificationId(group)
                ?: return cursor
            val notificationJob = NotificationGenerationJob(context)
            notificationJob.isRestoring = true
            notificationJob.shownTimeStamp = datetime
            notificationJob.jsonPayload = JSONObject(jsonStr)
            _generateNotification.updateSummaryNotification(notificationJob)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return cursor
    }

    private suspend fun restoreSummary(context: Context, group: String) {
        var cursor: Cursor? = null
        val whereArgs = arrayOf(group)
        try {
            cursor = _databaseProvider.get().query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                NotificationRestoreProcessor.COLUMNS_FOR_RESTORE,
                OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0",
                whereArgs,
                null,  // group by
                null,  // filter by row groups
                null
            )

            _notificationRestoreProcessor.showNotificationsFromCursor(cursor, 0)
        } catch (t: Throwable) {
            Logging.error("Error restoring notification records! ", t)
        } finally {
            if (cursor != null && !cursor.isClosed) cursor.close()
        }
    }

    fun getSummaryNotificationId(group: String): Int? {
        var androidNotifId: Int? = null
        var cursor: Cursor? = null
        val whereStr = OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 1"
        val whereArgs = arrayOf(group)
        try {
            // Get the Android Notification ID of the summary notification
            cursor = _databaseProvider.get().query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                arrayOf(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID),  // retColumn
                whereStr,
                whereArgs,
                null,
                null,
                null
            )
            val hasRecord = cursor.moveToFirst()
            if (!hasRecord) {
                cursor.close()
                return null
            }
            androidNotifId =
                cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID))
            cursor.close()
        } catch (t: Throwable) {
            Logging.error("Error getting android notification id for summary notification group: $group", t)
        } finally {
            if (cursor != null && !cursor.isClosed) cursor.close()
        }
        return androidNotifId
    }

    /**
     * Clears notifications from the status bar based on a few parameters
     */
    suspend fun clearNotificationOnSummaryClick(
        context: Context,
        group: String
    ) {
        // Obtain the group to clear notifications from
        var groupId = getSummaryNotificationId(group)
        val isGroupless = group == NotificationHelper.grouplessSummaryKey
        val notificationManager = NotificationHelper.getNotificationManager(context)
        // Obtain the most recent notification id
        val mostRecentId = _dataController.getMostRecentNotifIdFromGroup(group, isGroupless)
        if (mostRecentId != null) {
            val shouldDismissAll = _paramsService.clearGroupOnSummaryClick
            if (shouldDismissAll) {

                // If the group is groupless, obtain the hardcoded groupless summary id
                if (isGroupless) groupId = NotificationHelper.grouplessSummaryId

                // Clear the entire notification summary
                if (groupId != null) notificationManager.cancel(groupId)
            } else {
                // Clear the most recent notification from the status bar summary
                // TODO: This removes from the database, but not the status bar. Need something on top?
                _dataController.removeNotification(mostRecentId)
            }
        }
    }
}