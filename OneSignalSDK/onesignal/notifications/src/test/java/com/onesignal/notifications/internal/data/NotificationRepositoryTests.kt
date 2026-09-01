package com.onesignal.notifications.internal.data

import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.mocks.DatabaseMockHelper
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.internal.badges.IBadgeCountUpdater
import com.onesignal.notifications.internal.data.impl.NotificationRepository
import com.onesignal.notifications.shadows.ShadowRoboNotificationManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.robolectric.annotation.Config

@Config(
    packageName = "com.onesignal.example",
    shadows = [ShadowRoboNotificationManager::class],
    sdk = [26],
)
@RobolectricTest
class NotificationRepositoryTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
        ShadowRoboNotificationManager.reset()
    }

    fun repository(badgeCountUpdater: IBadgeCountUpdater = mockk(relaxed = true)): NotificationRepository {
        val database = DatabaseMockHelper.databaseProvider("notification")
        every { database.second.update(any(), any(), any(), any()) } returns 1
        return NotificationRepository(
            AndroidMockHelper.applicationService(),
            mockk(relaxed = true),
            database.first,
            MockHelper.time(1111),
            badgeCountUpdater,
        )
    }

    test("markAsDismissedWithoutCancel should not cancel the notification from the shade") {
        repository().markAsDismissedWithoutCancel(7)

        ShadowRoboNotificationManager.cancelledNotifications shouldBe emptyList()
    }

    test("markAsDismissedWithoutCancel should refresh the badge count") {
        // Below API 23 the badge counts undismissed rows, so it has to drop here even though the
        // notification stays in the shade.
        val badgeCountUpdater = mockk<IBadgeCountUpdater>(relaxed = true)

        repository(badgeCountUpdater).markAsDismissedWithoutCancel(7)

        verify(exactly = 1) { badgeCountUpdater.update() }
    }

    test("markAsDismissed should cancel the notification from the shade") {
        repository().markAsDismissed(7)

        ShadowRoboNotificationManager.cancelledNotifications shouldBe listOf(7)
    }
})
