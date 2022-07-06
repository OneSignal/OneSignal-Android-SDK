package com.onesignal.onesignal.internal.notification.data

import android.app.NotificationManager
import android.content.ContentValues
import android.database.Cursor
import com.onesignal.onesignal.internal.application.IApplicationService
import com.onesignal.onesignal.internal.common.time.ITime
import com.onesignal.onesignal.internal.database.IDatabase
import com.onesignal.onesignal.internal.database.OneSignalDbContract
import com.onesignal.onesignal.internal.notification.NotificationConstants
import com.onesignal.onesignal.internal.notification.NotificationFormatHelper
import com.onesignal.onesignal.internal.notification.NotificationHelper
import com.onesignal.onesignal.internal.notification.work.NotificationWorkManager
import com.onesignal.onesignal.logging.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

internal class NotificationDataController(
    private val _applicationService: IApplicationService,
    private val _workManager: NotificationWorkManager,
    private val _database: IDatabase,
    private val _time: ITime) {

    /**
     * We clean outdated cache from several places within the OneSignal SDK here
     * 1. Notifications & unique outcome events linked to notification ids (1 week)
     * 2. Cached In App Messaging Sets in SharedPreferences (impressions, clicks, views) and SQL IAMs
     */
    suspend fun cleanOldCachedData() {
        cleanNotificationCache()
    }

    /**
     * Deletes notifications with created timestamps older than 7 days
     * Cleans notification tables
     * 1. NotificationTable.TABLE_NAME
     */
    private suspend fun cleanNotificationCache() = coroutineScope {
        launch(Dispatchers.Default) {
            val whereStr: String = OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME.toString() + " < ?"
            val sevenDaysAgoInSeconds: String = java.lang.String.valueOf(
                _time.currentTimeMillis / 1000L - NOTIFICATION_CACHE_DATA_LIFETIME
            )

            val whereArgs = arrayOf(sevenDaysAgoInSeconds)
            _database.delete(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                whereStr,
                whereArgs
            )
        }
    }

    suspend fun clearOneSignalNotifications() = coroutineScope {
        launch(Dispatchers.Default) {
            val appContext = _applicationService.appContext ?: return@launch
            val notificationManager: NotificationManager = NotificationHelper.getNotificationManager(appContext)
            val retColumn = arrayOf(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID)
            val cursor: Cursor = _database.query(
                                        OneSignalDbContract.NotificationTable.TABLE_NAME,
                                        retColumn,
                               OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED.toString() + " = 0 AND " +
                                        OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0",
                            null,
                                null,  // group by
                                 null,  // filter by row groups
                                null // sort order
                                        )

            if (cursor.moveToFirst()) {
                do {
                    val existingId =
                        cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID))
                    notificationManager.cancel(existingId)
                } while (cursor.moveToNext())
            }

            // Mark all notifications as dismissed unless they were already opened.
            val whereStr: String = OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED.toString() + " = 0"
            val values = ContentValues()
            values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED, 1)
            _database.update(OneSignalDbContract.NotificationTable.TABLE_NAME, values, whereStr, null)

            // TODO: Implement
            // BadgeCountUpdater.updateCount(0, appContext)
            cursor.close()
        }
    }

    suspend fun removeGroupedNotifications(group: String) = coroutineScope {
        launch(Dispatchers.Default) {
            val appContext = _applicationService.appContext ?: return@launch
            val notificationManager: NotificationManager =
                NotificationHelper.getNotificationManager(appContext)

            val retColumn =
                arrayOf<String>(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID)
            val whereArgs = arrayOf(group)
            var whereStr: String =
                OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID.toString() + " = ? AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0"
            val cursor: Cursor = _database.query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                retColumn,
                whereStr,
                whereArgs,
                null, null, null
            )

            while (cursor.moveToNext()) {
                val notificationId =
                    cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID))
                if (notificationId != -1) notificationManager.cancel(notificationId)
            }
            cursor.close()
            whereStr =
                OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID.toString() + " = ? AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0"
            val values = ContentValues()
            values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED, 1)
            _database.update(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                values,
                whereStr,
                whereArgs
            )

            // TODO: Implement
            // BadgeCountUpdater.update(dbHelper, appContext)
        }
    }

    suspend fun removeNotification(id: Int) = coroutineScope {
        launch(Dispatchers.Default) {
            val appContext = _applicationService.appContext ?: return@launch

            val whereStr: String =
                OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID.toString() + " = " + id + " AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0"
            val values = ContentValues()
            values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED, 1)
            val records: Int = _database.update(OneSignalDbContract.NotificationTable.TABLE_NAME, values, whereStr, null)
            if (records > 0) {
                // TODO: Implement
//                NotificationSummaryManager.updatePossibleDependentSummaryOnDismiss(
//                    appContext,
//                    _database,
//                    id
//                )
            }

            // TODO: Implement
            // BadgeCountUpdater.update(dbHelper, appContext)

            val notificationManager: NotificationManager = NotificationHelper.getNotificationManager(appContext)
            notificationManager.cancel(id)
        }
    }

    suspend fun notValidOrDuplicated(jsonPayload: JSONObject?) : Boolean {
        val id: String? = NotificationFormatHelper.getOSNotificationIdFromJson(jsonPayload)

        if (id == null) {
            Logging.debug("Notification notValidOrDuplicated with id null")
            return true
        }

        return isDuplicateNotification(id)
    }

    private suspend fun isDuplicateNotification(id: String?) : Boolean = coroutineScope {
        if (id == null || "" == id) {
            return@coroutineScope false
        }

        if (!_workManager.addNotificationIdProcessed(id)) {
            Logging.debug("Notification notValidOrDuplicated with id duplicated")
            return@coroutineScope true
        }

        var result = false

        launch(Dispatchers.Default) {

            val retColumn =
                arrayOf<String>(OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID)
            val whereArgs = arrayOf(id!!)
            val cursor: Cursor = _database.query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                retColumn,
                OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID.toString() + " = ?",  // Where String
                whereArgs,
                null,
                null,
                null
            )
            val exists = cursor.moveToFirst()
            cursor.close()
            if (exists) {
                Logging.debug("Notification notValidOrDuplicated with id duplicated, duplicate FCM message received, skip processing of $id")
                result = true
            }
        }

        return@coroutineScope result
    }

    companion object {
        private const val NOTIFICATION_CACHE_DATA_LIFETIME = 604800L // 7 days in second
        private const val OS_NOTIFICATIONS_THREAD = "OS_NOTIFICATIONS_THREAD"
    }
}