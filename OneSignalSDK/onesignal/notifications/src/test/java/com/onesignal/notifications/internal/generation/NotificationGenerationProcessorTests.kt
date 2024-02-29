package com.onesignal.notifications.internal.generation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationWillDisplayEvent
import com.onesignal.notifications.internal.data.INotificationRepository
import com.onesignal.notifications.internal.display.INotificationDisplayer
import com.onesignal.notifications.internal.generation.impl.NotificationGenerationProcessor
import com.onesignal.notifications.internal.lifecycle.INotificationLifecycleService
import com.onesignal.notifications.internal.summary.INotificationSummaryManager
import com.onesignal.testhelpers.extensions.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.robolectric.annotation.Config

@Config(
    packageName = "com.onesignal.example",
    sdk = [26],
)
@RobolectricTest
class NotificationGenerationProcessorTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("processNotificationData should set title correctly") {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockTime = MockHelper.time(1111)
        val mockApplicationService = AndroidMockHelper.applicationService()
        every { mockApplicationService.isInForeground } returns true
        val mockNotificationDisplayer = mockk<INotificationDisplayer>()
        coEvery { mockNotificationDisplayer.displayNotification(any()) } returns true
        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.doesNotificationExist(any()) } returns false
        coEvery {
            mockNotificationRepository.createNotification(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } just runs
        val mockNotificationSummaryManager = mockk<INotificationSummaryManager>()
        val mockNotificationLifecycleService = mockk<INotificationLifecycleService>()
        coEvery { mockNotificationLifecycleService.canReceiveNotification(any()) } returns true
        coEvery { mockNotificationLifecycleService.notificationReceived(any()) } just runs
        coEvery { mockNotificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mockNotificationLifecycleService.externalNotificationWillShowInForeground(any()) } just runs

        val notificationGenerationProcessor =
            NotificationGenerationProcessor(
                mockApplicationService,
                mockNotificationDisplayer,
                MockHelper.configModelStore(),
                mockNotificationRepository,
                mockNotificationSummaryManager,
                mockNotificationLifecycleService,
                mockTime,
            )

        val payload =
            JSONObject()
                .put("alert", "test message")
                .put("title", "test title")
                .put(
                    "custom",
                    JSONObject()
                        .put("i", "UUID1"),
                )

        // When
        notificationGenerationProcessor.processNotificationData(context, 1, payload, false, 1111)

        // Then
        coVerify(exactly = 1) {
            mockNotificationDisplayer.displayNotification(
                withArg {
                    it.androidId shouldBe 1
                    it.apiNotificationId shouldBe "UUID1"
                    it.body shouldBe "test message"
                    it.title shouldBe "test title"
                    it.isRestoring shouldBe false
                    it.shownTimeStamp shouldBe 1111
                },
            )
        }
        coVerify(exactly = 1) {
            mockNotificationRepository.createNotification("UUID1", null, null, any(), false, 1, "test title", "test message", any(), any())
        }
    }

    test("processNotificationData should restore notification correctly") {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockTime = MockHelper.time(1111)
        val mockApplicationService = AndroidMockHelper.applicationService()
        every { mockApplicationService.isInForeground } returns true
        val mockNotificationDisplayer = mockk<INotificationDisplayer>()
        coEvery { mockNotificationDisplayer.displayNotification(any()) } returns true
        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.doesNotificationExist(any()) } returns false
        val mockNotificationSummaryManager = mockk<INotificationSummaryManager>()
        val mockNotificationLifecycleService = mockk<INotificationLifecycleService>()
        coEvery { mockNotificationLifecycleService.canReceiveNotification(any()) } returns true
        coEvery { mockNotificationLifecycleService.notificationReceived(any()) } just runs
        coEvery { mockNotificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mockNotificationLifecycleService.externalNotificationWillShowInForeground(any()) } just runs

        val notificationGenerationProcessor =
            NotificationGenerationProcessor(
                mockApplicationService,
                mockNotificationDisplayer,
                MockHelper.configModelStore(),
                mockNotificationRepository,
                mockNotificationSummaryManager,
                mockNotificationLifecycleService,
                mockTime,
            )

        val payload =
            JSONObject()
                .put("alert", "test message")
                .put("title", "test title")
                .put(
                    "custom",
                    JSONObject()
                        .put("i", "UUID1"),
                )

        // When
        notificationGenerationProcessor.processNotificationData(context, 1, payload, true, 1111)

        // Then
        coVerify(exactly = 1) {
            mockNotificationDisplayer.displayNotification(
                withArg {
                    it.androidId shouldBe 1
                    it.apiNotificationId shouldBe "UUID1"
                    it.body shouldBe "test message"
                    it.title shouldBe "test title"
                    it.isRestoring shouldBe true
                    it.shownTimeStamp shouldBe 1111
                },
            )
        }
    }

    test("processNotificationData should not display notification when external callback indicates not to") {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockTime = MockHelper.time(1111)
        val mockApplicationService = AndroidMockHelper.applicationService()
        every { mockApplicationService.isInForeground } returns true
        val mockNotificationDisplayer = mockk<INotificationDisplayer>()
        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.doesNotificationExist(any()) } returns false
        coEvery { mockNotificationRepository.createNotification(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } just runs
        val mockNotificationSummaryManager = mockk<INotificationSummaryManager>()
        val mockNotificationLifecycleService = mockk<INotificationLifecycleService>()
        coEvery { mockNotificationLifecycleService.canReceiveNotification(any()) } returns true
        coEvery { mockNotificationLifecycleService.notificationReceived(any()) } just runs
        coEvery { mockNotificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            val receivedEvent = firstArg<INotificationReceivedEvent>()
            receivedEvent.preventDefault()
        }

        val notificationGenerationProcessor =
            NotificationGenerationProcessor(
                mockApplicationService,
                mockNotificationDisplayer,
                MockHelper.configModelStore(),
                mockNotificationRepository,
                mockNotificationSummaryManager,
                mockNotificationLifecycleService,
                mockTime,
            )

        val payload =
            JSONObject()
                .put("alert", "test message")
                .put("title", "test title")
                .put(
                    "custom",
                    JSONObject()
                        .put("i", "UUID1"),
                )

        // When
        notificationGenerationProcessor.processNotificationData(context, 1, payload, false, 1111)

        // Then
    }

    test("processNotificationData should display notification when external callback takes longer than 30 seconds") {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockTime = MockHelper.time(1111)
        val mockApplicationService = AndroidMockHelper.applicationService()
        every { mockApplicationService.isInForeground } returns true
        val mockNotificationDisplayer = mockk<INotificationDisplayer>()
        coEvery { mockNotificationDisplayer.displayNotification(any()) } returns true
        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.doesNotificationExist(any()) } returns false
        coEvery { mockNotificationRepository.createNotification(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } just runs
        val mockNotificationSummaryManager = mockk<INotificationSummaryManager>()
        val mockNotificationLifecycleService = mockk<INotificationLifecycleService>()
        coEvery { mockNotificationLifecycleService.canReceiveNotification(any()) } returns true
        coEvery { mockNotificationLifecycleService.notificationReceived(any()) } just runs
        coEvery { mockNotificationLifecycleService.externalRemoteNotificationReceived(any()) } coAnswers {
            delay(40000)
        }

        val notificationGenerationProcessor =
            NotificationGenerationProcessor(
                mockApplicationService,
                mockNotificationDisplayer,
                MockHelper.configModelStore(),
                mockNotificationRepository,
                mockNotificationSummaryManager,
                mockNotificationLifecycleService,
                mockTime,
            )

        val payload =
            JSONObject()
                .put("alert", "test message")
                .put("title", "test title")
                .put(
                    "custom",
                    JSONObject()
                        .put("i", "UUID1"),
                )

        // When
        notificationGenerationProcessor.processNotificationData(context, 1, payload, true, 1111)

        // Then
        coVerify(exactly = 1) {
            mockNotificationDisplayer.displayNotification(
                withArg {
                    it.androidId shouldBe 1
                    it.apiNotificationId shouldBe "UUID1"
                    it.body shouldBe "test message"
                    it.title shouldBe "test title"
                    it.isRestoring shouldBe true
                    it.shownTimeStamp shouldBe 1111
                },
            )
        }
    }

    test("processNotificationData should not display notification when foreground callback indicates not to") {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockTime = MockHelper.time(1111)
        val mockApplicationService = AndroidMockHelper.applicationService()
        every { mockApplicationService.isInForeground } returns true
        val mockNotificationDisplayer = mockk<INotificationDisplayer>()
        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.doesNotificationExist(any()) } returns false
        coEvery { mockNotificationRepository.createNotification(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } just runs
        val mockNotificationSummaryManager = mockk<INotificationSummaryManager>()
        val mockNotificationLifecycleService = mockk<INotificationLifecycleService>()
        coEvery { mockNotificationLifecycleService.canReceiveNotification(any()) } returns true
        coEvery { mockNotificationLifecycleService.notificationReceived(any()) } just runs
        coEvery { mockNotificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mockNotificationLifecycleService.externalNotificationWillShowInForeground(any()) } answers {
            val receivedEvent = firstArg<INotificationWillDisplayEvent>()
            receivedEvent.preventDefault()
        }

        val notificationGenerationProcessor =
            NotificationGenerationProcessor(
                mockApplicationService,
                mockNotificationDisplayer,
                MockHelper.configModelStore(),
                mockNotificationRepository,
                mockNotificationSummaryManager,
                mockNotificationLifecycleService,
                mockTime,
            )

        val payload =
            JSONObject()
                .put("alert", "test message")
                .put("title", "test title")
                .put(
                    "custom",
                    JSONObject()
                        .put("i", "UUID1"),
                )

        // When
        notificationGenerationProcessor.processNotificationData(context, 1, payload, false, 1111)

        // Then
    }

    test("processNotificationData should display notification when foreground callback takes longer than 30 seconds") {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockTime = MockHelper.time(1111)
        val mockApplicationService = AndroidMockHelper.applicationService()
        every { mockApplicationService.isInForeground } returns true
        val mockNotificationDisplayer = mockk<INotificationDisplayer>()
        coEvery { mockNotificationDisplayer.displayNotification(any()) } returns true
        val mockNotificationRepository = mockk<INotificationRepository>()
        coEvery { mockNotificationRepository.doesNotificationExist(any()) } returns false
        coEvery { mockNotificationRepository.createNotification(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } just runs
        val mockNotificationSummaryManager = mockk<INotificationSummaryManager>()
        val mockNotificationLifecycleService = mockk<INotificationLifecycleService>()
        coEvery { mockNotificationLifecycleService.canReceiveNotification(any()) } returns true
        coEvery { mockNotificationLifecycleService.notificationReceived(any()) } just runs
        coEvery { mockNotificationLifecycleService.externalNotificationWillShowInForeground(any()) } coAnswers {
            delay(40000)
        }

        val notificationGenerationProcessor =
            NotificationGenerationProcessor(
                mockApplicationService,
                mockNotificationDisplayer,
                MockHelper.configModelStore(),
                mockNotificationRepository,
                mockNotificationSummaryManager,
                mockNotificationLifecycleService,
                mockTime,
            )

        val payload =
            JSONObject()
                .put("alert", "test message")
                .put("title", "test title")
                .put(
                    "custom",
                    JSONObject()
                        .put("i", "UUID1"),
                )

        // When
        notificationGenerationProcessor.processNotificationData(context, 1, payload, true, 1111)

        // Then
        coVerify(exactly = 1) {
            mockNotificationDisplayer.displayNotification(
                withArg {
                    it.androidId shouldBe 1
                    it.apiNotificationId shouldBe "UUID1"
                    it.body shouldBe "test message"
                    it.title shouldBe "test title"
                    it.isRestoring shouldBe true
                    it.shownTimeStamp shouldBe 1111
                },
            )
        }
    }
})
