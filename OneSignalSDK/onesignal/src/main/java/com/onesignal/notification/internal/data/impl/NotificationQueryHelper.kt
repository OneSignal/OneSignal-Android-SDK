package com.onesignal.notification.internal.data.impl

import com.onesignal.core.internal.database.impl.OneSignalDbContract
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.time.ITime
import com.onesignal.notification.internal.data.INotificationQueryHelper

internal class NotificationQueryHelper(
    private val _configModelStore: ConfigModelStore,
    private val _time: ITime
) : INotificationQueryHelper {

    override fun recentUninteractedWithNotificationsWhere(): StringBuilder {
        val currentTimeSec = _time.currentTimeMillis / 1000L
        val createdAtCutoff = currentTimeSec - 604800L // 1 Week back
        val where = StringBuilder(
            OneSignalDbContract.NotificationTable.COLUMN_NAME_CREATED_TIME + " > " + createdAtCutoff + " AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0"
        )
        val useTtl = _configModelStore.get().restoreTTLFilter
        if (useTtl) {
            val expireTimeWhere =
                " AND " + OneSignalDbContract.NotificationTable.COLUMN_NAME_EXPIRE_TIME + " > " + currentTimeSec
            where.append(expireTimeWhere)
        }
        return where
    }
}
