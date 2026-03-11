package com.onesignal.notifications.internal.badges

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.notifications.internal.badges.impl.BadgeCountUpdater
import com.onesignal.notifications.internal.badges.impl.shortcutbadger.ShortcutBadger
import com.onesignal.notifications.internal.common.NotificationHelper
import com.onesignal.notifications.internal.data.INotificationQueryHelper
import io.kotest.core.spec.style.FunSpec
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify

private class Mocks {
    val applicationService = mockk<IApplicationService>()
    val queryHelper = mockk<INotificationQueryHelper>(relaxed = true)
    val databaseProvider = mockk<IDatabaseProvider>(relaxed = true)

    init {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        val applicationInfo = ApplicationInfo()

        every { applicationService.appContext } returns context
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.onesignal.example"
        every {
            packageManager.getApplicationInfo("com.onesignal.example", PackageManager.GET_META_DATA)
        } returns applicationInfo
    }

    fun badgeCountUpdater(sdkInt: Int) =
        BadgeCountUpdater.createForTesting(
            applicationService,
            queryHelper,
            databaseProvider,
            sdkInt,
        )
}

class BadgeCountUpdaterTests : FunSpec({
    beforeEach {
        mockkObject(NotificationHelper)
        every { NotificationHelper.areNotificationsEnabled(any()) } returns true
        mockkStatic(ShortcutBadger::class)
        every { ShortcutBadger.applyCountOrThrow(any(), any()) } just Runs
    }

    afterEach {
        unmockkStatic(ShortcutBadger::class)
        unmockkObject(NotificationHelper)
    }

    test("update should not use ShortcutBadger on Android O") {
        Mocks().badgeCountUpdater(Build.VERSION_CODES.O).update()

        verify(exactly = 0) { ShortcutBadger.applyCountOrThrow(any(), any()) }
    }

    test("updateCount should not use ShortcutBadger on Android O") {
        Mocks().badgeCountUpdater(Build.VERSION_CODES.O).updateCount(3)

        verify(exactly = 0) { ShortcutBadger.applyCountOrThrow(any(), any()) }
    }

    test("updateCount should use ShortcutBadger before Android O") {
        val mocks = Mocks()
        val updater =
            BadgeCountUpdater(
                mocks.applicationService,
                mocks.queryHelper,
                mocks.databaseProvider,
            )

        updater.updateCount(3)

        verify(exactly = 1) { ShortcutBadger.applyCountOrThrow(any(), 3) }
    }
})
