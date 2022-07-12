package com.onesignal.onesignal.notification.internal.work

import android.database.Cursor
import android.os.Build
import android.provider.BaseColumns
import android.text.TextUtils
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.onesignal.core.internal.database.impl.OneSignalDbContract
import com.onesignal.onesignal.notification.internal.NotificationHelper
import com.onesignal.onesignal.notification.internal.common.NotificationQueryHelper
import com.onesignal.onesignal.notification.internal.generation.NotificationLimitManager
import com.onesignal.onesignal.core.internal.logging.Logging
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.lang.StringBuilder
import java.util.ArrayList

internal class NotificationRestoreProcessor(
    private val _applicationService: IApplicationService,
    private val _workManager: INotificationGenerationWorkManager,
    private val _databaseProvider: IDatabaseProvider,
    private val _queryHelper: NotificationQueryHelper
){
    suspend fun process() {
        Logging.info("Restoring notifications")

        val dbQuerySelection = _queryHelper.recentUninteractedWithNotificationsWhere()
        skipVisibleNotifications(dbQuerySelection)
        queryAndRestoreNotificationsAndBadgeCount(dbQuerySelection)
    }

    private suspend fun queryAndRestoreNotificationsAndBadgeCount(dbQuerySelection: StringBuilder) {
        Logging.info("Querying DB for notifications to restore: $dbQuerySelection")

        try {
            _databaseProvider.get().query(
                OneSignalDbContract.NotificationTable.TABLE_NAME,
                COLUMNS_FOR_RESTORE,
                dbQuerySelection.toString(),
                null,
                null,  // group by
                null,  // filter by row groups
                BaseColumns._ID + " DESC",  // sort order, new to old
                NotificationLimitManager.maxNumberOfNotificationsInt.toString() // limit
            ).use {
                showNotificationsFromCursor(it, DELAY_BETWEEN_NOTIFICATION_RESTORES_MS)
            }

            // TODO: Implement
            //BadgeCountUpdater.update(dbHelper, context)
        } catch (t: Throwable) {
            Logging.error("Error restoring notification records! ", t)
        }
    }


    /**
     * Retrieve the list of notifications that are currently in the shade
     * this is used to prevent notifications from being restored twice in M and newer.
     * This is important mostly for Android O as they can't be redisplayed in a silent way unless
     * they are displayed under a different channel which isn't ideal.
     * For pre-O devices this still have the benefit of being more efficient
     */
    private fun skipVisibleNotifications(dbQuerySelection: StringBuilder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val activeNotifs = NotificationHelper.getActiveNotifications(_applicationService.appContext!!)
        if (activeNotifs.isEmpty()) return
        val activeNotifIds = ArrayList<Int?>()
        for (activeNotif in activeNotifs) activeNotifIds.add(activeNotif.id)
        dbQuerySelection
            .append(" AND " + OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " NOT IN (")
            .append(TextUtils.join(",", activeNotifIds))
            .append(")")
    }

    /**
     * Restores a set of notifications back to the notification shade based on an SQL cursor
     * @param cursor - Source cursor to generate notifications from
     * @param delay - Delay to slow down process to ensure we don't spike CPU and I/O on the device
     */
    suspend fun showNotificationsFromCursor(cursor: Cursor, delay: Int) {
        if (!cursor.moveToFirst())
            return

        do {
            val osNotificationId =
                cursor.getString(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID))
            val existingId =
                cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID))
            val fullData =
                cursor.getString(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA))
            val dateTime =
                cursor.getLong(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME))

            _workManager.beginEnqueueingWork(
                _applicationService.appContext!!,
                osNotificationId,
                existingId,
                JSONObject(fullData),
                dateTime,
                true,
                false
            )

            if (delay > 0)
                delay(delay.toLong())

        } while (cursor.moveToNext())
    }

    companion object {
        // Delay to prevent logcat messages and possibly skipping some notifications
        //    This prevents the following error;
        // E/NotificationService: Package enqueue rate is 10.56985. Shedding events. package=####
        private const val DELAY_BETWEEN_NOTIFICATION_RESTORES_MS = 200
        const val DEFAULT_TTL_IF_NOT_IN_PAYLOAD = 259200

        val COLUMNS_FOR_RESTORE = arrayOf(
            OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID,
            OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID,
            OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA,
            OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME
        )
    }
}