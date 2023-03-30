package com.onesignal.notifications.internal.limiting

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.notifications.extensions.RobolectricTest
import com.onesignal.notifications.internal.data.INotificationRepository
import com.onesignal.notifications.internal.limiting.impl.NotificationLimitManager
import com.onesignal.notifications.internal.summary.INotificationSummaryManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements

@Config(
    packageName = "com.onesignal.example",
    shadows = [ShadowINotificationLimitManagerConstants::class],
    sdk = [26],
)
@RobolectricTest
@RunWith(KotestTestRunner::class)
class NotificationLimitManagerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("clearOldestOverLimit should make room for one when at limit") {
        /* Given */
        createNotification(ApplicationProvider.getApplicationContext(), 1)
        createNotification(ApplicationProvider.getApplicationContext(), 2)

        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.markAsDismissed(any()) } returns true
        val mockNotificationSummaryManager = spyk<INotificationSummaryManager>()

        val notificationLimitManager = NotificationLimitManager(mockNotificationRepository, AndroidMockHelper.applicationService(), mockNotificationSummaryManager)

        /* When */
        notificationLimitManager.clearOldestOverLimit(1)

        /* Then */
        coVerify(exactly = 1) { mockNotificationRepository.markAsDismissed(1) }
    }

    test("clearOldestOverLimit should not dismiss any when under limit") {
        /* Given */
        createNotification(ApplicationProvider.getApplicationContext(), 1)

        val mockNotificationRepository = mockk<INotificationRepository>()
        val mockNotificationSummaryManager = spyk<INotificationSummaryManager>()

        val notificationLimitManager = NotificationLimitManager(mockNotificationRepository, AndroidMockHelper.applicationService(), mockNotificationSummaryManager)

        /* When */
        notificationLimitManager.clearOldestOverLimit(1)

        /* Then */
        coVerify(exactly = 0) { mockNotificationRepository.markAsDismissed(1) }
    }

    test("clearOldestOverLimit should skip dismissing summary notifications") {
        /* Given */
        createNotification(ApplicationProvider.getApplicationContext(), 1, true)
        createNotification(ApplicationProvider.getApplicationContext(), 2)

        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.markAsDismissed(any()) } returns true
        val mockNotificationSummaryManager = spyk<INotificationSummaryManager>()

        val notificationLimitManager = NotificationLimitManager(mockNotificationRepository, AndroidMockHelper.applicationService(), mockNotificationSummaryManager)

        /* When */
        notificationLimitManager.clearOldestOverLimit(1)

        /* Then */
        coVerify(exactly = 1) { mockNotificationRepository.markAsDismissed(2) }
    }
})

fun createNotification(context: Context, notifId: Int, isSummary: Boolean = false) {
    val notifBuilder = NotificationCompat.Builder(context, "")
    notifBuilder.setWhen(notifId.toLong()) // Android automatically sets this normally.
    if (isSummary) {
        // We should not clear summary notifications, these will go away if all child notifications are canceled
        notifBuilder.setGroupSummary(true)
    }
    NotificationManagerCompat.from(context).notify(notifId, notifBuilder.build())
}

@Implements(value = INotificationLimitManager.Constants::class, looseSignatures = true)
class ShadowINotificationLimitManagerConstants {
    val maxNumberOfNotifications: Int = 2
}
