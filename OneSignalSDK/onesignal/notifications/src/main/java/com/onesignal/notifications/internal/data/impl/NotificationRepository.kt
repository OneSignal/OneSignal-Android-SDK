package com.onesignal.notifications.internal.data.impl

import android.app.NotificationManager
import android.content.ContentValues
import android.provider.BaseColumns
import android.text.TextUtils
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.core.internal.database.impl.OneSignalDbContract
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.badges.IBadgeCountUpdater
import com.onesignal.notifications.internal.common.NotificationHelper
import com.onesignal.notifications.internal.data.INotificationQueryHelper
import com.onesignal.notifications.internal.data.INotificationRepository
import com.onesignal.notifications.internal.limiting.INotificationLimitManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException

internal class NotificationRepository(
    private val _applicationService: IApplicationService,
    private val _queryHelper: INotificationQueryHelper,
    private val _databaseProvider: IDatabaseProvider,
    private val _time: ITime,
    private val _badgeCountUpdater: IBadgeCountUpdater,
) : INotificationRepository {
    /**
     * Deletes notifications with created timestamps older than 7 days
     * Cleans notification tables
     * 1. NotificationTable.TABLE_NAME
     */
    override suspend fun deleteExpiredNotifications() {
        withContext(Dispatchers.IO) {
            val whereStr: String = OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME.toString() + " < ?"
            val sevenDaysAgoInSeconds: String =
                java.lang.String.valueOf(
                    _time.currentTimeMillis / 1000L - NOTIFICATION_CACHE_DATA_LIFETIME,
                )

            val whereArgs = arrayOf(sevenDaysAgoInSeconds)
            _databaseProvider.os.delete(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                whereStr,
                whereArgs,
            )
        }
    }

    override suspend fun markAsDismissedForOutstanding() {
        withContext(Dispatchers.IO) {
            val appContext = _applicationService.appContext
            val notificationManager: NotificationManager = NotificationHelper.getNotificationManager(appContext)
            val retColumn = arrayOf(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID)
            _databaseProvider.os.query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                columns = retColumn,
                whereClause =
                OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED.toString() + " = 0 AND " +
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0",
            ) {
                if (it.moveToFirst()) {
                    do {
                        val existingId =
                            it.getInt(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID)
                        notificationManager.cancel(existingId)
                    } while (it.moveToNext())
                }
            }

            // Mark all notifications as dismissed unless they were already opened.
            val whereStr: String = OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED.toString() + " = 0"
            val values = ContentValues()
            values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED, 1)
            _databaseProvider.os.update(OneSignalDbContract.NotificationTable.TABLE_NAME, values, whereStr, null)

            _badgeCountUpdater.updateCount(0)
        }
    }

    override suspend fun markAsDismissedForGroup(group: String) {
        withContext(Dispatchers.IO) {
            val appContext = _applicationService.appContext
            val notificationManager: NotificationManager =
                NotificationHelper.getNotificationManager(appContext)

            val retColumn =
                arrayOf<String>(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID)
            val whereArgs = arrayOf(group)
            var whereStr: String =
                OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID.toString() + " = ? AND " +
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0"
            _databaseProvider.os.query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                columns = retColumn,
                whereClause = whereStr,
                whereArgs = whereArgs,
            ) {
                while (it.moveToNext()) {
                    val notificationId =
                        it.getInt(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID)
                    if (notificationId != -1) notificationManager.cancel(notificationId)
                }
            }

            whereStr =
                OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0"
            val values = ContentValues()
            values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED, 1)
            _databaseProvider.os.update(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                values,
                whereStr,
                whereArgs,
            )

            _badgeCountUpdater.update()
        }
    }

    override suspend fun markAsDismissed(androidId: Int): Boolean {
        var didDismiss: Boolean = false

        withContext(Dispatchers.IO) {
            didDismiss = internalMarkAsDismissed(androidId)
        }

        return didDismiss
    }

    private fun internalMarkAsDismissed(androidId: Int): Boolean {
        val appContext = _applicationService.appContext

        val whereStr: String =
            OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID.toString() + " = " + androidId + " AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0"
        val values = ContentValues()
        values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED, 1)
        val records: Int = _databaseProvider.os.update(OneSignalDbContract.NotificationTable.TABLE_NAME, values, whereStr, null)

        val didDismiss = records > 0

        _badgeCountUpdater.update()

        val notificationManager: NotificationManager = NotificationHelper.getNotificationManager(appContext)
        notificationManager.cancel(androidId)

        return didDismiss
    }

    override suspend fun doesNotificationExist(id: String?): Boolean {
        if (id == null || "" == id) {
            return false
        }

        var result = false

        withContext(Dispatchers.IO) {
            val retColumn =
                arrayOf<String>(OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID)
            val whereArgs = arrayOf(id!!)
            _databaseProvider.os.query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                columns = retColumn,
                // Where String
                whereClause = OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID + " = ?",
                whereArgs = whereArgs,
            ) {
                val exists = it.moveToFirst()

                if (exists) {
                    Logging.debug(
                        "Notification notValidOrDuplicated with id duplicated, duplicate FCM message received, skip processing of $id",
                    )
                    result = true
                }
            }
        }

        return result
    }

    override suspend fun createSummaryNotification(
        androidId: Int,
        groupId: String,
    ) {
        withContext(Dispatchers.IO) {
            // There currently isn't a visible notification from for this group_id.
            // Save the group summary notification id so it can be updated later.
            val values = ContentValues()
            values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID, androidId)
            values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID, groupId)
            values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY, 1)
            _databaseProvider.os
                .insertOrThrow(OneSignalDbContract.NotificationTable.TABLE_NAME, null, values)
        }
    }

    // Saving the notification provides the following:
    //   * Prevent duplicates
    //   * Build summary notifications
    //   * Collapse key / id support - Used to lookup the android notification id later
    //   * Redisplay notifications after reboot, upgrade of app, or cold boot after a force kill.
    //   * Future - Public API to get a list of notifications
    override suspend fun createNotification(
        id: String,
        groupId: String?,
        collapseKey: String?,
        shouldDismissIdenticals: Boolean,
        isOpened: Boolean,
        androidId: Int,
        title: String?,
        body: String?,
        expireTime: Long,
        jsonPayload: String,
    ) {
        withContext(Dispatchers.IO) {
            Logging.debug("Saving Notification id=$id")

            try {
                if (shouldDismissIdenticals) {
                    val whereStr =
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + androidId
                    val values = ContentValues()
                    values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED, 1)

                    _databaseProvider.os.update(
                        OneSignalDbContract.NotificationTable.TABLE_NAME,
                        values,
                        whereStr,
                        null,
                    )

                    _badgeCountUpdater.update()
                }

                // Save just received notification to DB
                val values = ContentValues()

                values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID, id)

                if (groupId != null) {
                    values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID, groupId)
                }

                if (collapseKey != null) {
                    values.put(
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_COLLAPSE_ID,
                        collapseKey,
                    )
                }

                values.put(
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED,
                    if (isOpened) 1 else 0,
                )

                if (!isOpened) {
                    values.put(
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID,
                        androidId,
                    )
                }

                if (title != null) {
                    values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_TITLE, title)
                }

                if (body != null) {
                    values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_MESSAGE, body)
                }

                values.put(
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_EXPIRE_TIME,
                    expireTime,
                )
                values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA, jsonPayload)

                _databaseProvider.os.insertOrThrow(
                    OneSignalDbContract.NotificationTable.TABLE_NAME,
                    null,
                    values,
                )
                Logging.debug("Notification saved values: $values")

                if (!isOpened) {
                    _badgeCountUpdater.update()
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun markAsConsumed(
        androidId: Int,
        dismissed: Boolean,
        summaryGroup: String?,
        clearGroupOnSummaryClick: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            var whereStr: String
            var whereArgs: Array<String>? = null
            if (summaryGroup != null) {
                val isGroupless = summaryGroup == NotificationHelper.GROUPLESS_SUMMARY_KEY
                if (isGroupless) {
                    whereStr =
                        OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " IS NULL"
                } else {
                    whereStr = OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " = ?"
                    whereArgs = arrayOf(summaryGroup)
                }
                if (!dismissed) {
                    // Make sure when a notification is not being dismissed it is handled through the dashboard setting
                    if (!clearGroupOnSummaryClick) {
                        /* If the open event shouldn't clear all summary notifications then the SQL query
                         * will look for the most recent notification instead of all grouped notifications */
                        val mostRecentId = getAndroidIdForGroup(summaryGroup, false).toString()
                        whereStr += " AND " + OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = ?"
                        whereArgs =
                            if (isGroupless) {
                                arrayOf(mostRecentId)
                            } else {
                                arrayOf(
                                    summaryGroup,
                                    mostRecentId,
                                )
                            }
                    }
                }
            } else {
                whereStr =
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + androidId
            }

            val values = ContentValues()
            if (dismissed) {
                values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED, 1)
            } else {
                values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED, 1)
            }

            _databaseProvider.os.update(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                values,
                whereStr,
                whereArgs,
            )

            _badgeCountUpdater.update()
        }
    }

    override suspend fun getGroupId(androidId: Int): String? {
        var groupId: String? = null

        withContext(Dispatchers.IO) {
            _databaseProvider.os.query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                // retColumn
                columns = arrayOf(OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID),
                whereClause = OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + androidId,
            ) {
                if (it.moveToFirst()) {
                    groupId =
                        it.getOptString(OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID)
                }
            }
        }

        return groupId
    }

    override suspend fun getAndroidIdFromCollapseKey(collapseKey: String): Int? {
        var androidId: Int? = null

        withContext(Dispatchers.IO) {
            _databaseProvider.os.query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                // retColumn
                columns = arrayOf(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID),
                whereClause =
                OneSignalDbContract.NotificationTable.COLUMN_NAME_COLLAPSE_ID + " = ? AND " +
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 ",
                whereArgs = arrayOf(collapseKey),
            ) {
                if (it.moveToFirst()) {
                    androidId = it.getInt(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID)
                }
            }
        }

        return androidId
    }

    override suspend fun clearOldestOverLimitFallback(
        notificationsToMakeRoomFor: Int,
        maxNumberOfNotificationsInt: Int,
    ) {
        withContext(Dispatchers.IO) {
            val maxNumberOfNotificationsString = maxNumberOfNotificationsInt.toString()

            try {
                _databaseProvider.os.query(
                    OneSignalDbContract.NotificationTable.TABLE_NAME,
                    columns = arrayOf(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID),
                    whereClause = _queryHelper.recentUninteractedWithNotificationsWhere().toString(),
                    // sort order, old to new
                    orderBy = BaseColumns._ID,
                    // limit
                    limit = maxNumberOfNotificationsString + notificationsToMakeRoomFor,
                ) {
                    var notificationsToClear =
                        it.count - maxNumberOfNotificationsInt + notificationsToMakeRoomFor
                    // We have enough room in the notification shade, no need to clear any notifications
                    if (notificationsToClear < 1) {
                        return@query
                    }

                    while (it.moveToNext()) {
                        val existingId = it.getInt(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID)
                        internalMarkAsDismissed(existingId)
                        if (--notificationsToClear <= 0) break
                    }
                }
            } catch (t: Throwable) {
                Logging.error("Error clearing oldest notifications over limit! ", t)
            }
        }
    }

    override suspend fun listNotificationsForGroup(summaryGroup: String): List<INotificationRepository.NotificationData> {
        val listOfNotifications = mutableListOf<INotificationRepository.NotificationData>()

        withContext(Dispatchers.IO) {
            val whereArgs = arrayOf(summaryGroup)

            _databaseProvider.os.query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                columns = COLUMNS_FOR_LIST_NOTIFICATIONS,
                whereClause =
                OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " + // Where String
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0",
                whereArgs = whereArgs,
                // sort order, new to old);
                orderBy = BaseColumns._ID + " DESC",
            ) {
                if (it.moveToFirst()) {
                    do {
                        try {
                            val title = it.getOptString(OneSignalDbContract.NotificationTable.COLUMN_NAME_TITLE)
                            val message = it.getOptString(OneSignalDbContract.NotificationTable.COLUMN_NAME_MESSAGE)
                            val osNotificationId = it.getString(OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID)
                            val existingId = it.getInt(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID)
                            val fullData = it.getString(OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA)
                            val dateTime = it.getLong(OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME)

                            listOfNotifications.add(
                                INotificationRepository.NotificationData(
                                    existingId,
                                    osNotificationId,
                                    fullData,
                                    dateTime,
                                    title,
                                    message,
                                ),
                            )
                        } catch (e: JSONException) {
                            Logging.error("Could not parse JSON of sub notification in group: $summaryGroup")
                        }
                    } while (it.moveToNext())
                }
            }
        }

        return listOfNotifications
    }

    /**
     * Query SQLiteDatabase by group to find the most recent created notification id
     */
    override suspend fun getAndroidIdForGroup(
        group: String,
        getSummaryNotification: Boolean,
    ): Int? {
        var recentId: Int? = null
        val isGroupless = group == NotificationHelper.GROUPLESS_SUMMARY_KEY

        /* Beginning of the query string changes based on being groupless or not
         * since the groupless notifications will have a null group key in the db */
        var whereStr =
            if (isGroupless) OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " IS NULL" else OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " = ?"

        // Look for all active (not opened and not dismissed) notifications, not including summaries
        whereStr += " AND " +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
            OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 AND "

        whereStr +=
            if (getSummaryNotification) {
                OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 1"
            } else {
                OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0"
            }

        val whereArgs = if (isGroupless) null else arrayOf(group)

        withContext(Dispatchers.IO) {
            // Order by timestamp in descending and limit to 1
            _databaseProvider.os.query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                columns = arrayOf(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID),
                whereClause = whereStr,
                whereArgs = whereArgs,
                orderBy = OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME + " DESC",
                limit = "1",
            ) {
                val hasRecord = it.moveToFirst()
                recentId =
                    if (!hasRecord) {
                        null
                    } else {
                        // Get more recent notification id from Cursor
                        it.getInt(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID)
                    }
            }
        }

        return recentId
    }

    override suspend fun listNotificationsForOutstanding(excludeAndroidIds: List<Int>?): List<INotificationRepository.NotificationData> {
        val listOfNotifications = mutableListOf<INotificationRepository.NotificationData>()
        withContext(Dispatchers.IO) {
            val dbQuerySelection = _queryHelper.recentUninteractedWithNotificationsWhere()

            if (excludeAndroidIds != null) {
                dbQuerySelection
                    .append(" AND " + OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " NOT IN (")
                    .append(TextUtils.join(",", excludeAndroidIds))
                    .append(")")
            }

            _databaseProvider.os.query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                columns = COLUMNS_FOR_LIST_NOTIFICATIONS,
                whereClause = dbQuerySelection.toString(),
                // sort order, new to old
                orderBy = BaseColumns._ID + " DESC",
                // limit
                limit = INotificationLimitManager.Constants.maxNumberOfNotifications.toString(),
            ) {
                while (it.moveToNext()) {
                    val title =
                        it.getOptString(OneSignalDbContract.NotificationTable.COLUMN_NAME_TITLE)
                    val message =
                        it.getOptString(OneSignalDbContract.NotificationTable.COLUMN_NAME_MESSAGE)
                    val osNotificationId =
                        it.getString(OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID)
                    val existingId =
                        it.getInt(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID)
                    val fullData =
                        it.getString(OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA)
                    val dateTime =
                        it.getLong(OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME)

                    listOfNotifications.add(
                        INotificationRepository.NotificationData(
                            existingId,
                            osNotificationId,
                            fullData,
                            dateTime,
                            title,
                            message,
                        ),
                    )
                }
            }
        }

        return listOfNotifications
    }

    companion object {
        private const val NOTIFICATION_CACHE_DATA_LIFETIME = 604800L // 7 days in second

        val COLUMNS_FOR_LIST_NOTIFICATIONS =
            arrayOf(
                OneSignalDbContract.NotificationTable.COLUMN_NAME_TITLE,
                OneSignalDbContract.NotificationTable.COLUMN_NAME_MESSAGE,
                OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID,
                OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID,
                OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA,
                OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME,
            )
    }
}
