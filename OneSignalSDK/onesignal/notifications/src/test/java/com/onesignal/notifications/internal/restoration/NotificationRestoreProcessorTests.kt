package com.onesignal.notifications.internal.restoration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.OneSignal
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.notifications.internal.badges.IBadgeCountUpdater
import com.onesignal.notifications.internal.common.NotificationHelper
import com.onesignal.notifications.internal.data.INotificationRepository
import com.onesignal.notifications.internal.generation.INotificationGenerationWorkManager
import com.onesignal.notifications.internal.restoration.impl.NotificationRestoreProcessor
import com.onesignal.notifications.internal.restoration.impl.NotificationRestoreWorkManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify

@RobolectricTest
class NotificationRestoreProcessorTests : FunSpec({
    fun notification(id: String) = INotificationRepository.NotificationData(id.hashCode(), id, "{}", 1L, null, null)

    test("a failed enqueue does not skip later notifications or the badge update") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val applicationService = mockk<IApplicationService> { every { appContext } returns context }
        val workManager = mockk<INotificationGenerationWorkManager>()
        every {
            workManager.beginEnqueueingWork(any(), any(), any(), any(), any(), any(), any())
        } answers {
            if (secondArg<String>() == "bad") throw IllegalStateException("enqueue timed out")
            true
        }
        val repository = mockk<INotificationRepository>()
        coEvery { repository.listNotificationsForOutstanding(any()) } returns
            listOf(notification("bad"), notification("good"))
        val badgeCountUpdater = mockk<IBadgeCountUpdater>(relaxed = true)

        val allPersisted =
            NotificationRestoreProcessor(applicationService, workManager, repository, badgeCountUpdater).process()

        allPersisted shouldBe false
        verify(exactly = 1) {
            workManager.beginEnqueueingWork(any(), "good", any(), any(), any(), any(), any())
        }
        verify(exactly = 1) { badgeCountUpdater.update() }
    }

    test("process reports success when every enqueue persists") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val applicationService = mockk<IApplicationService> { every { appContext } returns context }
        val workManager = mockk<INotificationGenerationWorkManager>()
        every { workManager.beginEnqueueingWork(any(), any(), any(), any(), any(), any(), any()) } returns true
        val repository = mockk<INotificationRepository>()
        coEvery { repository.listNotificationsForOutstanding(any()) } returns listOf(notification("good"))

        NotificationRestoreProcessor(applicationService, workManager, repository, mockk(relaxed = true))
            .process() shouldBe true
    }

    test("restore worker retries until every enqueue persists") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val processor = mockk<INotificationRestoreProcessor>()
        coEvery { processor.process() } returns false
        mockkObject(OneSignal)
        mockkObject(NotificationHelper)
        coEvery { OneSignal.initWithContext(any()) } returns true
        every { OneSignal.getService<INotificationRestoreProcessor>() } returns processor
        every { NotificationHelper.areNotificationsEnabled(any(), any()) } returns true
        val params = mockk<WorkerParameters>(relaxed = true)

        try {
            every { params.runAttemptCount } returns 0
            NotificationRestoreWorkManager.NotificationRestoreWorker(context, params).doWork()
                .javaClass shouldBe ListenableWorker.Result.retry().javaClass

            every { params.runAttemptCount } returns NotificationRestoreWorkManager.MAX_RESTORE_ATTEMPTS - 1
            NotificationRestoreWorkManager.NotificationRestoreWorker(context, params).doWork()
                .javaClass shouldBe ListenableWorker.Result.failure().javaClass
        } finally {
            unmockkObject(NotificationHelper)
            unmockkObject(OneSignal)
        }
    }
})
