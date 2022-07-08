package com.onesignal.onesignal.internal.notification.data

import android.app.NotificationManager
import android.content.ContentValues
import android.provider.BaseColumns
import com.onesignal.onesignal.internal.application.IApplicationService
import com.onesignal.onesignal.internal.common.time.ITime
import com.onesignal.onesignal.internal.database.IDatabase
import com.onesignal.onesignal.internal.database.OneSignalDbContract
import com.onesignal.onesignal.internal.notification.NotificationHelper
import com.onesignal.onesignal.internal.notification.badges.BadgeCountUpdater
import com.onesignal.onesignal.internal.notification.common.INotificationQueryHelper
import com.onesignal.onesignal.logging.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONException

internal class NotificationDataController(
    private val _applicationService: IApplicationService,
    private val _queryHelper: INotificationQueryHelper,
    private val _database: IDatabase,
    private val _time: ITime,
    private val _badgeCountUpdater: BadgeCountUpdater) : INotificationDataController {

    /**
     * We clean outdated cache from several places within the OneSignal SDK here
     * 1. Notifications & unique outcome events linked to notification ids (1 week)
     * 2. Cached In App Messaging Sets in SharedPreferences (impressions, clicks, views) and SQL IAMs
     */
    override suspend fun cleanOldCachedData() {
        cleanNotificationCache()
    }

    /**
     * Deletes notifications with created timestamps older than 7 days
     * Cleans notification tables
     * 1. NotificationTable.TABLE_NAME
     */
    override suspend fun cleanNotificationCache() = coroutineScope {
        withContext(Dispatchers.Default) {
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

    override suspend fun clearOneSignalNotifications() = coroutineScope {
        withContext(Dispatchers.Default) {
            val appContext = _applicationService.appContext ?: return@withContext
            val notificationManager: NotificationManager = NotificationHelper.getNotificationManager(appContext)
            val retColumn = arrayOf(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID)
            _database.query(OneSignalDbContract.NotificationTable.TABLE_NAME,
                            retColumn,
                   OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED.toString() + " = 0 AND " +
                            OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0",
                null,
                    null,  // group by
                     null,  // filter by row groups
                    null // sort order
                            ).use {
                if (it.moveToFirst()) {
                    do {
                        val existingId =
                            it.getInt(it.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID))
                        notificationManager.cancel(existingId)
                    } while (it.moveToNext())
                }
            }

            // Mark all notifications as dismissed unless they were already opened.
            val whereStr: String = OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED.toString() + " = 0"
            val values = ContentValues()
            values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED, 1)
            _database.update(OneSignalDbContract.NotificationTable.TABLE_NAME, values, whereStr, null)

            _badgeCountUpdater.updateCount(0, appContext)
        }
    }

    override suspend fun removeGroupedNotifications(group: String) = coroutineScope {
        withContext(Dispatchers.Default) {
            val appContext = _applicationService.appContext ?: return@withContext
            val notificationManager: NotificationManager =
                NotificationHelper.getNotificationManager(appContext)

            val retColumn =
                arrayOf<String>(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID)
            val whereArgs = arrayOf(group)
            var whereStr: String =
                OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID.toString() + " = ? AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0"
            _database.query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                retColumn,
                whereStr,
                whereArgs,
                null, null, null
            ).use {
                while (it.moveToNext()) {
                    val notificationId =
                        it.getInt(it.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID))
                    if (notificationId != -1) notificationManager.cancel(notificationId)
                }
            }

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

            _badgeCountUpdater.update(appContext)
        }
    }

    override suspend fun removeNotification(id: Int) = coroutineScope {
        withContext(Dispatchers.Default) {
            val appContext = _applicationService.appContext ?: return@withContext

            val whereStr: String =
                OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID.toString() + " = " + id + " AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0"
            val values = ContentValues()
            values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED, 1)
            val records: Int = _database.update(OneSignalDbContract.NotificationTable.TABLE_NAME, values, whereStr, null)
            if (records > 0) {
                // TODO Implement (Circular dependency issues)
//                NotificationSummaryManager.updatePossibleDependentSummaryOnDismiss(appContext, id)
            }

             _badgeCountUpdater.update(appContext)

            val notificationManager: NotificationManager = NotificationHelper.getNotificationManager(appContext)
            notificationManager.cancel(id)
        }
    }

    override suspend fun isDuplicateNotification(id: String?) : Boolean = coroutineScope {
        if (id == null || "" == id) {
            return@coroutineScope false
        }

        var result = false

        withContext(Dispatchers.Default) {

            val retColumn =
                arrayOf<String>(OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID)
            val whereArgs = arrayOf(id!!)
            _database.query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                retColumn,
                OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID.toString() + " = ?",  // Where String
                whereArgs,
                null,
                null,
                null
            ).use {
                val exists = it.moveToFirst()

                if (exists) {
                    Logging.debug("Notification notValidOrDuplicated with id duplicated, duplicate FCM message received, skip processing of $id")
                    result = true
                }
            }
        }

        return@coroutineScope result
    }

    // Saving the notification provides the following:
    //   * Prevent duplicates
    //   * Build summary notifications
    //   * Collapse key / id support - Used to lookup the android notification id later
    //   * Redisplay notifications after reboot, upgrade of app, or cold boot after a force kill.
    //   * Future - Public API to get a list of notifications
    override suspend fun saveNotification(id: String,
                                          groupId: String?,
                                          collapseKey: String?,
                                          isShown: Boolean,
                                          isOpened: Boolean,
                                          androidId: Int,
                                          title: String?,
                                          body: String?,
                                          expireTime: Long,
                                          jsonPayload: String) = coroutineScope {
        withContext(Dispatchers.Default) {
            Logging.debug("Saving Notification id=$id")

            try {
                val appContext = _applicationService.appContext ?: return@withContext

                // When notification was displayed, count any notifications with duplicated android
                // notification ids as dismissed.
                if (isShown) {
                    val whereStr =
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + androidId
                    val values = ContentValues()
                    values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED, 1)

                    _database.update(
                        OneSignalDbContract.NotificationTable.TABLE_NAME,
                        values,
                        whereStr,
                        null
                    )

                    _badgeCountUpdater.update(appContext)
                }

                // Save just received notification to DB
                val values = ContentValues()

                values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID, id)

                if (groupId != null)
                    values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID, groupId)

                if (collapseKey != null)
                    values.put(
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_COLLAPSE_ID,
                        collapseKey
                    )

                values.put(
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED,
                    if (isOpened) 1 else 0
                )

                if (!isOpened)
                    values.put(
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID,
                        androidId
                    )

                if (title != null)
                    values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_TITLE, title)

                if (body != null)
                    values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_MESSAGE, body)

                values.put(
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_EXPIRE_TIME,
                    expireTime
                )
                values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA, jsonPayload)

                _database.insertOrThrow(
                    OneSignalDbContract.NotificationTable.TABLE_NAME,
                    null,
                    values
                )
                Logging.debug("Notification saved values: $values")

                if (!isOpened) {
                    _badgeCountUpdater.update(appContext)
                }

            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun markAsDismissed(androidId: Int) = coroutineScope {
        Logging.debug("Marking restored or disabled notifications as dismissed: $androidId")

        withContext(Dispatchers.Default) {
            val whereStr =
                OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + androidId

            val values = ContentValues()
            values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED, 1)
            _database.update(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                values,
                whereStr,
                null
            )

            _badgeCountUpdater.update(_applicationService.appContext!!)
        }

        Logging.debug("Marking restored or disabled notifications as dismissed: $androidId")
    }

    override suspend fun getAndroidIdFromCollapseKey(collapseKey: String) : Int? = coroutineScope {

        var androidId: Int? = null

        withContext(Dispatchers.Default) {
            _database.query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                arrayOf(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID),  // retColumn
                OneSignalDbContract.NotificationTable.COLUMN_NAME_COLLAPSE_ID + " = ? AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 ",
                arrayOf(collapseKey),
                null,
                null,
                null
            ).use {
                if (it.moveToFirst()) {
                    androidId = it.getInt(it.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID))
                }
            }
        }

        return@coroutineScope androidId
    }

    override suspend fun clearOldestOverLimitFallback(notificationsToMakeRoomFor: Int, maxNumberOfNotificationsInt: Int) = coroutineScope {
        withContext(Dispatchers.Default) {
            val maxNumberOfNotificationsString = maxNumberOfNotificationsInt.toString()

            try {
                _database.query(
                    OneSignalDbContract.NotificationTable.TABLE_NAME,
                    arrayOf(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID),
                    _queryHelper.recentUninteractedWithNotificationsWhere().toString(),
                    null,
                    null,
                    null,
                    BaseColumns._ID,  // sort order, old to new
                    maxNumberOfNotificationsString + notificationsToMakeRoomFor // limit
                ).use {
                    var notificationsToClear =
                        it.count - maxNumberOfNotificationsInt + notificationsToMakeRoomFor
                    // We have enough room in the notification shade, no need to clear any notifications
                    if (notificationsToClear < 1)
                        return@use

                    while (it.moveToNext()) {
                        val existingId =
                            it.getInt(it.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID))
                        removeNotification(existingId)
                        if (--notificationsToClear <= 0) break
                    }
                }
            } catch (t: Throwable) {
                Logging.error("Error clearing oldest notifications over limit! ", t)
            }
        }
    }

    /**
     * Query SQLiteDatabase by group to find the most recent created notification id
     */
    override suspend fun getMostRecentNotifIdFromGroup(group: String, isGroupless: Boolean): Int? = coroutineScope {

        var recentId: Int? = null

        /* Beginning of the query string changes based on being groupless or not
         * since the groupless notifications will have a null group key in the db */
        var whereStr =
            if (isGroupless) OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " IS NULL" else OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " = ?"

        // Look for all active (not opened and not dismissed) notifications, not including summaries
        whereStr += " AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0"
        val whereArgs = if (isGroupless) null else arrayOf(group)

        withContext(Dispatchers.Default) {
            // Order by timestamp in descending and limit to 1
            _database.query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                null,
                whereStr,
                whereArgs,
                null,
                null,
                OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME + " DESC",
                "1"
            ).use {
                val hasRecord = it.moveToFirst()
                if (!hasRecord) {
                    recentId = null
                }

                // Get more recent notification id from Cursor
                recentId =
                    it.getInt(it.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID))
            }
        }

        return@coroutineScope recentId
    }

    companion object {
        private const val NOTIFICATION_CACHE_DATA_LIFETIME = 604800L // 7 days in second
        private const val OS_NOTIFICATIONS_THREAD = "OS_NOTIFICATIONS_THREAD"
    }
}