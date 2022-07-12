package com.onesignal.onesignal.notification.internal.badges

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.onesignal.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.onesignal.core.internal.database.impl.OneSignalDbContract
import com.onesignal.onesignal.notification.internal.NotificationHelper
import com.onesignal.onesignal.notification.internal.common.INotificationQueryHelper
import com.onesignal.onesignal.notification.internal.generation.NotificationLimitManager
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.shortcutbadger.ShortcutBadger
import com.onesignal.shortcutbadger.ShortcutBadgeException

internal class BadgeCountUpdater(
    private val _queryHelper: INotificationQueryHelper,
    private val _databaseProvider: IDatabaseProvider
) {
    // Cache for manifest setting.
    private var badgesEnabled = -1
    private fun areBadgeSettingsEnabled(context: Context): Boolean {
        if (badgesEnabled != -1) return badgesEnabled == 1
        try {
            val ai = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            val bundle = ai.metaData
            if (bundle != null) {
                val defaultStr = bundle.getString("com.onesignal.BadgeCount")
                badgesEnabled = if ("DISABLE" == defaultStr) 0 else 1
            } else badgesEnabled = 1
        } catch (e: PackageManager.NameNotFoundException) {
            badgesEnabled = 0
            Logging.error("Error reading meta-data tag 'com.onesignal.BadgeCount'. Disabling badge setting.", e)
        }
        return badgesEnabled == 1
    }

    private fun areBadgesEnabled(context: Context): Boolean {
        return areBadgeSettingsEnabled(context) && NotificationHelper.areNotificationsEnabled(context)
    }

    fun update(context: Context) {
        if (!areBadgesEnabled(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            updateStandard(context)
        else
            updateFallback(context)
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun updateStandard(context: Context) {
        val activeNotifs = NotificationHelper.getActiveNotifications(context)
        var runningCount = 0
        for (activeNotif in activeNotifs) {
            if (NotificationHelper.isGroupSummary(activeNotif)) continue
            runningCount++
        }
        updateCount(runningCount, context)
    }

    private fun updateFallback(context: Context) {
        var notificationCount: Int = 0

        _databaseProvider.get().query(
            OneSignalDbContract.NotificationTable.TABLE_NAME,
            null,
            _queryHelper.recentUninteractedWithNotificationsWhere().toString(),
            null,  // Where args
            null,  // group by
            null,  // filter by row groups
            null,  // sort order, new to old
            NotificationLimitManager.maxNumberOfNotificationsInt.toString()
        ).use {
            notificationCount = it.count
        }
        updateCount(notificationCount, context)
    }

    fun updateCount(count: Int, context: Context) {
        if (!areBadgeSettingsEnabled(context)) return
        try {
            ShortcutBadger.applyCountOrThrow(context, count)
        } catch (e: ShortcutBadgeException) {
            // Suppress error as there are normal cases where this will throw
            // Can throw if:
            //    - Badges are not support on the device.
            //    - App does not have a default launch Activity.
        }
    }
}