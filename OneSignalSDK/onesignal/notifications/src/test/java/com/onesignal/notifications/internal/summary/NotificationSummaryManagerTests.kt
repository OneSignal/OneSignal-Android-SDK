package com.onesignal.notifications.internal.summary

import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.internal.common.NotificationHelper
import com.onesignal.notifications.internal.common.NotificationRestoreReason
import com.onesignal.notifications.internal.data.INotificationRepository
import com.onesignal.notifications.internal.display.ISummaryNotificationDisplayer
import com.onesignal.notifications.internal.restoration.INotificationRestoreProcessor
import com.onesignal.notifications.internal.summary.impl.NotificationSummaryManager
import com.onesignal.notifications.shadows.ShadowRoboNotificationManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import org.robolectric.annotation.Config

@Config(
    packageName = "com.onesignal.example",
    shadows = [ShadowRoboNotificationManager::class],
    sdk = [26],
)
@RobolectricTest
class NotificationSummaryManagerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
        ShadowRoboNotificationManager.reset()
    }

    afterAny {
        unmockkObject(NotificationHelper)
    }

    test("updatePossibleDependentSummaryOnDismiss should take no action when dismissed notification is not part of a group") {
        // Given
        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.getGroupId(1) } returns null
        val mockSummaryNotificationDisplayer = mockk<ISummaryNotificationDisplayer>()
        val mockNotificationRestoreProcessor = mockk<INotificationRestoreProcessor>()

        val notificationSummaryManager =
            NotificationSummaryManager(
                AndroidMockHelper.applicationService(),
                mockNotificationRepository,
                mockSummaryNotificationDisplayer,
                MockHelper.configModelStore(),
                mockNotificationRestoreProcessor,
                MockHelper.time(111),
            )

        // When
        notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(1)

        // Then
        coVerify(exactly = 1) { mockNotificationRepository.getGroupId(1) }
    }

    test("updatePossibleDependentSummaryOnDismiss should dismiss summary notification when there are no more notifications in group") {
        // Given
        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.getGroupId(1) } returns "groupId"
        coEvery { mockNotificationRepository.listNotificationsForGroup("groupId") } returns listOf()
        coEvery { mockNotificationRepository.getAndroidIdForGroup("groupId", true) } returns 99
        coEvery { mockNotificationRepository.markAsConsumed(99, true) } just runs
        val mockSummaryNotificationDisplayer = mockk<ISummaryNotificationDisplayer>()
        val mockNotificationRestoreProcessor = mockk<INotificationRestoreProcessor>()

        val notificationSummaryManager =
            NotificationSummaryManager(
                AndroidMockHelper.applicationService(),
                mockNotificationRepository,
                mockSummaryNotificationDisplayer,
                MockHelper.configModelStore(),
                mockNotificationRestoreProcessor,
                MockHelper.time(111),
            )

        // When
        notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(1)

        // Then
        coVerify(exactly = 1) { mockNotificationRepository.getGroupId(1) }
        coVerify(exactly = 1) { mockNotificationRepository.listNotificationsForGroup("groupId") }
        coVerify(exactly = 1) { mockNotificationRepository.getAndroidIdForGroup("groupId", true) }
        coVerify(exactly = 1) { mockNotificationRepository.markAsConsumed(99, true) }
        ShadowRoboNotificationManager.cancelledNotifications.count() shouldBe 1
        ShadowRoboNotificationManager.cancelledNotifications[0] shouldBe 99
    }

    test("updatePossibleDependentSummaryOnDismiss should update summary notification when there are 2 or more notifications in group") {
        // Given
        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.getGroupId(1) } returns "groupId"
        coEvery { mockNotificationRepository.listNotificationsForGroup("groupId") } returns
            listOf(
                INotificationRepository.NotificationData(2, "notificationId2", "{key: \"value2\"}", 1111, "title2", "message2"),
                INotificationRepository.NotificationData(3, "notificationId3", "{key: \"value3\"}", 1111, "title3", "message3"),
            )
        coEvery { mockNotificationRepository.getAndroidIdForGroup("groupId", true) } returns 99
        val mockSummaryNotificationDisplayer = mockk<ISummaryNotificationDisplayer>()
        coEvery { mockSummaryNotificationDisplayer.updateSummaryNotification(any()) } just runs
        val mockNotificationRestoreProcessor = mockk<INotificationRestoreProcessor>()

        val notificationSummaryManager =
            NotificationSummaryManager(
                AndroidMockHelper.applicationService(),
                mockNotificationRepository,
                mockSummaryNotificationDisplayer,
                MockHelper.configModelStore(),
                mockNotificationRestoreProcessor,
                MockHelper.time(111),
            )

        // When
        notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(1)

        // Then
        coVerify(exactly = 1) { mockNotificationRepository.getGroupId(1) }
        coVerify(exactly = 1) { mockNotificationRepository.listNotificationsForGroup("groupId") }
        coVerify(exactly = 1) { mockNotificationRepository.getAndroidIdForGroup("groupId", true) }
        coVerify(exactly = 1) { mockSummaryNotificationDisplayer.updateSummaryNotification(any()) }
    }

    test("updatePossibleDependentSummaryOnDismiss should restore summary notification when there is 1 notification in group") {
        // Given
        // The remaining child is posted, so the regroup rebuild is what repairs its look.
        mockkObject(NotificationHelper)
        every { NotificationHelper.isNotificationActive(any(), any()) } returns true
        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.getGroupId(1) } returns "groupId"
        coEvery { mockNotificationRepository.listNotificationsForGroup("groupId") } returns
            listOf(
                INotificationRepository.NotificationData(2, "notificationId2", "{key: \"value2\"}", 1111, "title2", "message2"),
            )
        coEvery { mockNotificationRepository.getAndroidIdForGroup("groupId", true) } returns 99
        val mockSummaryNotificationDisplayer = mockk<ISummaryNotificationDisplayer>()
        val mockNotificationRestoreProcessor = mockk<INotificationRestoreProcessor>()
        coEvery { mockNotificationRestoreProcessor.processNotification(any(), any(), any()) } just runs

        val notificationSummaryManager =
            NotificationSummaryManager(
                AndroidMockHelper.applicationService(),
                mockNotificationRepository,
                mockSummaryNotificationDisplayer,
                MockHelper.configModelStore(),
                mockNotificationRestoreProcessor,
                MockHelper.time(111),
            )

        // When
        notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(1)

        // Then
        coVerify(exactly = 1) { mockNotificationRepository.getGroupId(1) }
        coVerify(exactly = 2) { mockNotificationRepository.listNotificationsForGroup("groupId") }
        coVerify(exactly = 1) { mockNotificationRepository.getAndroidIdForGroup("groupId", true) }
        coVerify(exactly = 1) {
            mockNotificationRestoreProcessor.processNotification(
                withArg {
                    it.androidId shouldBe 2
                    it.id shouldBe "notificationId2"
                    it.createdAt shouldBe 1111
                    it.title shouldBe "title2"
                    it.message shouldBe "message2"
                },
                // Not a shade restore. The notification is still showing, so suppressing this
                // rebuild must not dismiss or cancel it.
                NotificationRestoreReason.GROUP_REGROUP,
                any(),
            )
        }
    }

    test("updatePossibleDependentSummaryOnDismiss should not regroup the last notification when it is not posted") {
        // The remaining child is mid shade-restore, its own work coming or in flight. A regroup
        // enqueued now would steal that work, since unique work is keyed on the OS id alone, and
        // a suppressed regroup would then strand the row.
        // Given
        mockkObject(NotificationHelper)
        every { NotificationHelper.isNotificationActive(any(), any()) } returns false
        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.getGroupId(1) } returns "groupId"
        coEvery { mockNotificationRepository.listNotificationsForGroup("groupId") } returns
            listOf(
                INotificationRepository.NotificationData(2, "notificationId2", "{key: \"value2\"}", 1111, "title2", "message2"),
            )
        coEvery { mockNotificationRepository.getAndroidIdForGroup("groupId", true) } returns 99
        val mockSummaryNotificationDisplayer = mockk<ISummaryNotificationDisplayer>()
        val mockNotificationRestoreProcessor = mockk<INotificationRestoreProcessor>()

        val notificationSummaryManager =
            NotificationSummaryManager(
                AndroidMockHelper.applicationService(),
                mockNotificationRepository,
                mockSummaryNotificationDisplayer,
                MockHelper.configModelStore(),
                mockNotificationRestoreProcessor,
                MockHelper.time(111),
            )

        // When
        notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(1)

        // Then
        coVerify(exactly = 0) { mockNotificationRestoreProcessor.processNotification(any(), any(), any()) }
    }

    test("clearNotificationOnSummaryClick should do nothing when there is no notifications in group") {
        // Given
        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.getAndroidIdForGroup("groupId", false) } returns null
        val mockSummaryNotificationDisplayer = mockk<ISummaryNotificationDisplayer>()
        val mockNotificationRestoreProcessor = mockk<INotificationRestoreProcessor>()

        val notificationSummaryManager =
            NotificationSummaryManager(
                AndroidMockHelper.applicationService(),
                mockNotificationRepository,
                mockSummaryNotificationDisplayer,
                MockHelper.configModelStore(),
                mockNotificationRestoreProcessor,
                MockHelper.time(111),
            )

        // When
        notificationSummaryManager.clearNotificationOnSummaryClick("groupId")

        // Then
        coVerify(exactly = 1) { mockNotificationRepository.getAndroidIdForGroup("groupId", false) }
    }

    test("clearNotificationOnSummaryClick should do something when there is 1 or more notifications in group") {
        // Given
        val mockConfig =
            MockHelper.configModelStore {
                it.clearGroupOnSummaryClick = true
            }
        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.getAndroidIdForGroup("groupId", false) } returns 1
        coEvery { mockNotificationRepository.getAndroidIdForGroup("groupId", true) } returns 99
        val mockSummaryNotificationDisplayer = mockk<ISummaryNotificationDisplayer>()
        val mockNotificationRestoreProcessor = mockk<INotificationRestoreProcessor>()

        val notificationSummaryManager =
            NotificationSummaryManager(
                AndroidMockHelper.applicationService(),
                mockNotificationRepository,
                mockSummaryNotificationDisplayer,
                mockConfig,
                mockNotificationRestoreProcessor,
                MockHelper.time(111),
            )

        // When
        notificationSummaryManager.clearNotificationOnSummaryClick("groupId")

        // Then
        coVerify(exactly = 1) { mockNotificationRepository.getAndroidIdForGroup("groupId", false) }
        coVerify(exactly = 1) { mockNotificationRepository.getAndroidIdForGroup("groupId", true) }

        ShadowRoboNotificationManager.cancelledNotifications.count() shouldBe 1
        ShadowRoboNotificationManager.cancelledNotifications[0] shouldBe 99
    }
})
