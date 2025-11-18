package com.onesignal.notifications.internal.generation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.robolectric.annotation.Config

// Mocks used by every test in this file
private class Mocks {
    val notificationDisplayer = mockk<INotificationDisplayer>()

    val applicationService =
        run {
            val mockApplicationService = AndroidMockHelper.applicationService()
            every { mockApplicationService.isInForeground } returns true
            mockApplicationService
        }

    val notificationLifecycleService: INotificationLifecycleService =
        run {
            val mockNotificationLifecycleService = mockk<INotificationLifecycleService>()
            coEvery { mockNotificationLifecycleService.canReceiveNotification(any()) } returns true
            coEvery { mockNotificationLifecycleService.notificationReceived(any()) } just runs
            mockNotificationLifecycleService
        }

    val notificationRepository: INotificationRepository =
        run {
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
            mockNotificationRepository
        }

    val notificationGenerationProcessor =
        NotificationGenerationProcessor(
            applicationService,
            notificationDisplayer,
            MockHelper.configModelStore(),
            notificationRepository,
            mockk(),
            notificationLifecycleService,
            MockHelper.time(1111),
        )

    val notificationPayload: JSONObject =
        JSONObject()
            .put("alert", "test message")
            .put("title", "test title")
            .put(
                "custom",
                JSONObject()
                    .put("i", "UUID1"),
            )
}

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
        val mocks = Mocks()
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } just runs

        // When
        mocks.notificationGenerationProcessor.processNotificationData(context, 1, mocks.notificationPayload, false, 1111)

        // Then
        coVerify(exactly = 1) {
            mocks.notificationDisplayer.displayNotification(
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
            mocks.notificationRepository.createNotification("UUID1", null, null, any(), false, 1, "test title", "test message", any(), any())
        }
    }

    test("processNotificationData should restore notification correctly") {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mocks = Mocks()
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } just runs

        // When
        mocks.notificationGenerationProcessor.processNotificationData(context, 1, mocks.notificationPayload, true, 1111)

        // Then
        coVerify(exactly = 1) {
            mocks.notificationDisplayer.displayNotification(
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
        val mocks = Mocks()
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            val receivedEvent = firstArg<INotificationReceivedEvent>()
            receivedEvent.preventDefault()
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(context, 1, mocks.notificationPayload, false, 1111)

        // Then
    }

    test("processNotificationData should display notification when external callback takes longer than 30 seconds") {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mocks = Mocks()
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } coAnswers {
            delay(40000)
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(context, 1, mocks.notificationPayload, true, 1111)

        // Then
        coVerify(exactly = 1) {
            mocks.notificationDisplayer.displayNotification(
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
        val mocks = Mocks()
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } answers {
            val receivedEvent = firstArg<INotificationWillDisplayEvent>()
            receivedEvent.preventDefault()
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(context, 1, mocks.notificationPayload, false, 1111)

        // Then
    }

    test("processNotificationData should display notification when foreground callback takes longer than 30 seconds") {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mocks = Mocks()
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } coAnswers {
            delay(40000)
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(context, 1, mocks.notificationPayload, true, 1111)

        // Then
        coVerify(exactly = 1) {
            mocks.notificationDisplayer.displayNotification(
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

    test("processNotificationData should immediately drop the notification when will display callback indicates to") {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mocks = Mocks()
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } answers {
            val willDisplayEvent = firstArg<INotificationWillDisplayEvent>()
            // Setting discard parameter to true indicating we should immediately discard
            willDisplayEvent.preventDefault(true)
        }

        // If discard is set to false this should timeout waiting for display()
        withTimeout(1_000) {
            mocks.notificationGenerationProcessor.processNotificationData(context, 1, mocks.notificationPayload, false, 1111)
        }
    }

    test("processNotificationData should immediately drop the notification when received event callback indicates to") {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mocks = Mocks()
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            val receivedEvent = firstArg<INotificationReceivedEvent>()
            receivedEvent.preventDefault(true)
        }
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } just runs

        // If discard is set to false this should timeout waiting for display()
        withTimeout(1_000) {
            mocks.notificationGenerationProcessor.processNotificationData(context, 1, mocks.notificationPayload, false, 1111)
        }
    }

    test("processNotificationData allows the will display callback to prevent default behavior twice") {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mocks = Mocks()
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } coAnswers {
            val willDisplayEvent = firstArg<INotificationWillDisplayEvent>()
            willDisplayEvent.preventDefault(false)
            GlobalScope.launch {
                delay(100)
                willDisplayEvent.preventDefault(true)
                delay(100)
                willDisplayEvent.notification.display()
            }
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(context, 1, mocks.notificationPayload, false, 1111)

        // Then
        coVerify(exactly = 0) {
            mocks.notificationDisplayer.displayNotification(any())
        }
    }

    test("processNotificationData allows the received event callback to prevent default behavior twice") {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mocks = Mocks()
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } coAnswers {
            val receivedEvent = firstArg<INotificationReceivedEvent>()
            receivedEvent.preventDefault(false)
            GlobalScope.launch {
                delay(100)
                receivedEvent.preventDefault(true)
                delay(100)
                receivedEvent.notification.display()
            }
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(context, 1, mocks.notificationPayload, true, 1111)

        // Then
        coVerify(exactly = 0) {
            mocks.notificationDisplayer.displayNotification(any())
        }
    }
})
