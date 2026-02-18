package com.onesignal.notifications.internal.generation

import android.content.Context
import com.onesignal.common.threading.suspendifyOnIO
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
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

// Mocks used by every test in this file
private class Mocks {
    val notificationDisplayer = mockk<INotificationDisplayer>()

    val context = mockk<Context>(relaxed = true)

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

    val notificationGenerationProcessor = run {
        val mock = spyk(
            NotificationGenerationProcessor(
                applicationService,
                notificationDisplayer,
                MockHelper.configModelStore(),
                notificationRepository,
                mockk(),
                notificationLifecycleService,
                MockHelper.time(1111),
            ), recordPrivateCalls = true
        )
        every { mock getProperty "EXTERNAL_CALLBACKS_TIMEOUT" } answers { 10L }
        mock
    }

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

class NotificationGenerationProcessorTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE

        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>()?.isEmpty() ?: true }
    }

    test("processNotificationData should set title correctly") {
        // Given
        val mocks = Mocks()
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } just runs

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, false, 1111)

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
        val mocks = Mocks()
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } just runs

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, true, 1111)

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
        val mocks = Mocks()
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            val receivedEvent = firstArg<INotificationReceivedEvent>()
            receivedEvent.preventDefault()
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, false, 1111)

        // Then
        // notificationReceived should be called
        coVerify(exactly = 1) {
            mocks.notificationLifecycleService.notificationReceived(any())
        }
    }

    test("processNotificationData should display notification when external callback takes longer than 30 seconds") {
        // Given
        val mocks = Mocks()
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } coAnswers {
            delay(40000)
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, true, 1111)

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
        val mocks = Mocks()
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } answers {
            val receivedEvent = firstArg<INotificationWillDisplayEvent>()
            receivedEvent.preventDefault()
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, false, 1111)

        // Then
        // notificationReceived should be called
        coVerify(exactly = 1) {
            mocks.notificationLifecycleService.notificationReceived(any())
        }
    }

    test("processNotificationData should display notification when foreground callback takes longer than 30 seconds") {
        // Given
        val mocks = Mocks()
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } coAnswers {
            delay(40000)
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, true, 1111)

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
        val mocks = Mocks()
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } answers {
            val willDisplayEvent = firstArg<INotificationWillDisplayEvent>()
            // Setting discard parameter to true indicating we should immediately discard
            willDisplayEvent.preventDefault(true)
        }

        // If discard is set to false this should timeout waiting for display()
        withTimeout(1_000) {
            mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, false, 1111)
        }
    }

    test("processNotificationData should immediately drop the notification when received event callback indicates to") {
        // Given
        val mocks = Mocks()
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            val receivedEvent = firstArg<INotificationReceivedEvent>()
            receivedEvent.preventDefault(true)
        }
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } just runs

        // If discard is set to false this should timeout waiting for display()
        withTimeout(1_000) {
            mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, false, 1111)
        }
    }

    test("processNotificationData allows the will display callback to prevent default behavior twice") {
        // Given
        val mocks = Mocks()
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } coAnswers {
            val willDisplayEvent = firstArg<INotificationWillDisplayEvent>()
            willDisplayEvent.preventDefault(false)
            suspendifyOnIO {
                delay(100)
                willDisplayEvent.preventDefault(true)
                delay(100)
                willDisplayEvent.notification.display()
            }
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, false, 1111)

        // Then
        coVerify(exactly = 0) {
            mocks.notificationDisplayer.displayNotification(any())
        }
    }

    test("processNotificationData allows the received event callback to prevent default behavior twice") {
        // Given
        val mocks = Mocks()
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } coAnswers {
            val receivedEvent = firstArg<INotificationReceivedEvent>()
            receivedEvent.preventDefault(false)
            suspendifyOnIO {
                delay(100)
                receivedEvent.preventDefault(true)
                delay(100)
                receivedEvent.notification.display()
            }
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, true, 1111)

        // Then
        coVerify(exactly = 0) {
            mocks.notificationDisplayer.displayNotification(any())
        }
    }
})
