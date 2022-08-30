package com.onesignal.onesignal.notification.internal.badges.impl

import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.onesignal.core.internal.database.impl.OneSignalDbContract
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.notification.internal.badges.IBadgeCountUpdater
import com.onesignal.onesignal.notification.internal.badges.impl.shortcutbadger.ShortcutBadgeException
import com.onesignal.onesignal.notification.internal.badges.impl.shortcutbadger.ShortcutBadger
import com.onesignal.onesignal.notification.internal.common.NotificationHelper
import com.onesignal.onesignal.notification.internal.data.INotificationQueryHelper
import com.onesignal.onesignal.notification.internal.limiting.INotificationLimitManager

internal class BadgeCountUpdater(
    private val _applicationService: IApplicationService,
    private val _queryHelper: INotificationQueryHelper,
    private val _databaseProvider: IDatabaseProvider
) : IBadgeCountUpdater {
    // Cache for manifest setting.
    private var badgesEnabled = -1
    private fun areBadgeSettingsEnabled(): Boolean {
        if (badgesEnabled != -1) return badgesEnabled == 1
        try {
            val ai = _applicationService.appContext.packageManager.getApplicationInfo(
                _applicationService.appContext.packageName,
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

    private fun areBadgesEnabled(): Boolean {
        return areBadgeSettingsEnabled() && NotificationHelper.areNotificationsEnabled(_applicationService.appContext)
    }

    override fun update() {
        if (!areBadgesEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            updateStandard()
        else
            updateFallback()
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun updateStandard() {
        val activeNotifs = NotificationHelper.getActiveNotifications(_applicationService.appContext)
        var runningCount = 0
        for (activeNotif in activeNotifs) {
            if (NotificationHelper.isGroupSummary(activeNotif)) continue
            runningCount++
        }
        updateCount(runningCount)
    }

    private fun updateFallback() {
        var notificationCount: Int = 0

        _databaseProvider.get().query(
            OneSignalDbContract.NotificationTable.TABLE_NAME,
            null,
            _queryHelper.recentUninteractedWithNotificationsWhere().toString(),
            null, // Where args
            null, // group by
            null, // filter by row groups
            null, // sort order, new to old
            INotificationLimitManager.Constants.maxNumberOfNotifications.toString()
        ).use {
            notificationCount = it.count
        }
        updateCount(notificationCount)
    }

    override fun updateCount(count: Int) {
        if (!areBadgeSettingsEnabled()) return
        try {
            ShortcutBadger.applyCountOrThrow(_applicationService.appContext, count)
        } catch (e: ShortcutBadgeException) {
            // Suppress error as there are normal cases where this will throw
            // Can throw if:
            //    - Badges are not support on the device.
            //    - App does not have a default launch Activity.
        }
    }
}
