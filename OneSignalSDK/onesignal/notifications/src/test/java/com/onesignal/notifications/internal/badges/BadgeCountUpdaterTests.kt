package com.onesignal.notifications.internal.badges

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.database.ICursor
import com.onesignal.core.internal.database.IDatabase
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
    val database = mockk<IDatabase>(relaxed = true)
    val databaseProvider =
        mockk<IDatabaseProvider> {
            every { os } returns database
        }

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

    fun queryReturnsCount(count: Int) {
        val cursor = mockk<ICursor>()
        every { cursor.count } returns count
        every {
            database.query(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            arg<(ICursor) -> Unit>(8).invoke(cursor)
        }
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

    test("update should use ShortcutBadger on Android N MR1") {
        every { NotificationHelper.getActiveNotifications(any()) } returns emptyArray()

        Mocks().badgeCountUpdater(Build.VERSION_CODES.N_MR1).update()

        verify(exactly = 1) { ShortcutBadger.applyCountOrThrow(any(), 0) }
    }

    test("update should use ShortcutBadger before Android M") {
        val mocks = Mocks()
        mocks.queryReturnsCount(3)

        mocks.badgeCountUpdater(Build.VERSION_CODES.LOLLIPOP_MR1).update()

        verify(exactly = 1) { ShortcutBadger.applyCountOrThrow(any(), 3) }
    }

    test("updateCount should use ShortcutBadger before Android O") {
        Mocks().badgeCountUpdater(Build.VERSION_CODES.O - 1).updateCount(3)

        verify(exactly = 1) { ShortcutBadger.applyCountOrThrow(any(), 3) }
    }
})
