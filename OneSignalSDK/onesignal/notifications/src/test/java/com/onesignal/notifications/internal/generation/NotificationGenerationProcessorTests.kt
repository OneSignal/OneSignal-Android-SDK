package com.onesignal.notifications.internal.generation

import android.content.Context
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationWillDisplayEvent
import com.onesignal.notifications.internal.common.NotificationHelper
import com.onesignal.notifications.internal.common.NotificationRestoreReason
import com.onesignal.notifications.internal.data.INotificationRepository
import com.onesignal.notifications.internal.display.INotificationDisplayer
import com.onesignal.notifications.internal.generation.impl.NotificationGenerationProcessor
import com.onesignal.notifications.internal.lifecycle.INotificationLifecycleService
import com.onesignal.notifications.internal.summary.INotificationSummaryManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            coEvery { mockNotificationRepository.markAsDismissed(any()) } returns true
            coEvery { mockNotificationRepository.markAsDismissedWithoutCancel(any()) } returns true
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

    val notificationSummaryManager = mockk<INotificationSummaryManager>(relaxed = true)

    val notificationGenerationProcessor = run {
        val mock = spyk(
            NotificationGenerationProcessor(
                applicationService,
                notificationDisplayer,
                MockHelper.configModelStore(),
                notificationRepository,
                notificationSummaryManager,
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
    listener(IOMockHelper)

    // processNotificationData bounds the external callbacks with `withTimeout { launchOnIO { … }.join() }`.
    // IOMockHelper stubs launchOnIO to run inline via runBlocking, which blocks the calling thread inside
    // waitForWake() and defeats withTimeout (the suite hangs forever on the preventDefault-without-display
    // cases). Restore the production async semantics for launchOnIO in this spec by dispatching on a large
    // Dispatchers.IO-backed scope (mirroring the prior GlobalScope.launch(Dispatchers.IO) path) so .join()
    // suspends and withTimeout can cancel it. The scope is cancelled in afterSpec to drop any callback
    // coroutine still parked in waitForWake().
    val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    afterSpec { callbackScope.cancel() }

    beforeAny {
        Logging.logLevel = LogLevel.NONE

        every { OneSignalDispatchers.launchOnIO(any<suspend () -> Unit>()) } answers {
            val block = firstArg<suspend () -> Unit>()
            callbackScope.launch { block() }
        }

        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>()?.isEmpty() ?: true }
    }

    afterAny {
        unmockkObject(NotificationHelper)
    }

    test("processNotificationData should set title correctly") {
        // Given
        val mocks = Mocks()
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } just runs

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, null, 1111)

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
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, NotificationRestoreReason.SHADE_RESTORE, 1111)

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

    test("processNotificationData should tell the received event whether it is restoring") {
        // Given
        val mocks = Mocks()
        val restoringFlags = mutableListOf<Boolean>()
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            restoringFlags.add(firstArg<INotificationReceivedEvent>().restoring)
        }
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } just runs

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, null, 1111)
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 2, mocks.notificationPayload, NotificationRestoreReason.SHADE_RESTORE, 1111)
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 3, mocks.notificationPayload, NotificationRestoreReason.GROUP_REGROUP, 1111)

        // Then
        // Both reasons mean the app has seen this notification before, which is what the flag says.
        restoringFlags shouldBe listOf(false, true, true)
    }

    test("processNotificationData should not display notification when external callback indicates not to") {
        // Given
        val mocks = Mocks()
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            val receivedEvent = firstArg<INotificationReceivedEvent>()
            receivedEvent.preventDefault()
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, null, 1111)

        // Then
        // notificationReceived should be called
        coVerify(exactly = 1) {
            mocks.notificationLifecycleService.notificationReceived(any())
        }
        // Nothing was posted, so it is saved as opened rather than dismissed.
        coVerify(exactly = 0) {
            mocks.notificationRepository.markAsDismissed(any())
        }
    }

    test("processNotificationData should mark a shade restore dismissed without clearing the shade when the received event prevents display") {
        // Given
        val mocks = Mocks()
        // The suite default of 10ms can expire before Dispatchers.IO runs the callback.
        every { mocks.notificationGenerationProcessor getProperty "EXTERNAL_CALLBACKS_TIMEOUT" } answers { 1_000L }
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            firstArg<INotificationReceivedEvent>().preventDefault(true)
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, NotificationRestoreReason.SHADE_RESTORE, 1111)

        // Then
        coVerify(exactly = 0) {
            mocks.notificationDisplayer.displayNotification(any())
        }
        // Without this the notification comes back on every later restore.
        coVerify(exactly = 1) {
            mocks.notificationRepository.markAsDismissedWithoutCancel(1)
        }
        // markAsDismissed cancels the shade. The restore pass includes notifications that are still
        // showing on API 21 and 22, and whenever getActiveNotifications fails.
        coVerify(exactly = 0) {
            mocks.notificationRepository.markAsDismissed(any())
        }
        // Same follow-up as a real dismissal, so a suppressed group child cannot orphan its
        // summary or leave it counting a notification that is gone.
        coVerify(exactly = 1) {
            mocks.notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(1)
        }
    }

    test("processNotificationData should mark a shade restore dismissed when the received event never calls display") {
        // The no-argument preventDefault parks on the display waiter, so this only settles once the
        // callback timeout fires. The outcome has to match preventDefault(true).
        // Given
        val mocks = Mocks()
        every { mocks.notificationGenerationProcessor getProperty "EXTERNAL_CALLBACKS_TIMEOUT" } answers { 200L }
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            firstArg<INotificationReceivedEvent>().preventDefault()
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, NotificationRestoreReason.SHADE_RESTORE, 1111)

        // Then
        coVerify(exactly = 0) {
            mocks.notificationDisplayer.displayNotification(any())
        }
        coVerify(exactly = 1) {
            mocks.notificationRepository.markAsDismissedWithoutCancel(1)
        }
        coVerify(exactly = 0) {
            mocks.notificationRepository.markAsDismissed(any())
        }
        coVerify(exactly = 1) {
            mocks.notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(1)
        }
    }

    test("processNotificationData should not update the summary when a suppressed shade restore was already dismissed") {
        // A false return means the row was already dismissed or opened, so there is nothing for
        // the summary to react to.
        // Given
        val mocks = Mocks()
        every { mocks.notificationGenerationProcessor getProperty "EXTERNAL_CALLBACKS_TIMEOUT" } answers { 1_000L }
        coEvery { mocks.notificationRepository.markAsDismissedWithoutCancel(any()) } returns false
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            firstArg<INotificationReceivedEvent>().preventDefault(true)
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, NotificationRestoreReason.SHADE_RESTORE, 1111)

        // Then
        coVerify(exactly = 1) {
            mocks.notificationRepository.markAsDismissedWithoutCancel(1)
        }
        coVerify(exactly = 0) {
            mocks.notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(any())
        }
    }

    test("processNotificationData should reconcile the summary when a restored sibling already displayed") {
        // Child A displays first, so the summary it rebuilt still counts B. Suppressing B has to
        // reach the summary, or it goes on counting a notification that never comes back.
        // Given
        val mocks = Mocks()
        every { mocks.notificationGenerationProcessor getProperty "EXTERNAL_CALLBACKS_TIMEOUT" } answers { 1_000L }
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } just runs
        var suppress = false
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            if (suppress) firstArg<INotificationReceivedEvent>().preventDefault(true)
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, NotificationRestoreReason.SHADE_RESTORE, 1111)
        suppress = true
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 2, mocks.notificationPayload, NotificationRestoreReason.SHADE_RESTORE, 1111)

        // Then
        coVerify(exactly = 1) {
            mocks.notificationDisplayer.displayNotification(withArg { it.androidId shouldBe 1 })
        }
        coVerify(exactly = 1) {
            mocks.notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(2)
        }
        // The one that displayed stays put.
        coVerify(exactly = 0) {
            mocks.notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(1)
        }
    }

    test("processNotificationData should leave a suppressed regroup alone when the notification is still posted") {
        // A group dropping to one member sends that member back through generation. It is still in
        // the shade and the user never dismissed it, so suppressing the rebuild must not take it
        // down or dismiss the record.
        // Given
        val mocks = Mocks()
        every { mocks.notificationGenerationProcessor getProperty "EXTERNAL_CALLBACKS_TIMEOUT" } answers { 1_000L }
        mockkObject(NotificationHelper)
        every { NotificationHelper.isNotificationActive(any(), any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            firstArg<INotificationReceivedEvent>().preventDefault(true)
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, NotificationRestoreReason.GROUP_REGROUP, 1111)

        // Then
        coVerify(exactly = 0) {
            mocks.notificationDisplayer.displayNotification(any())
        }
        coVerify(exactly = 0) {
            mocks.notificationRepository.markAsDismissed(any())
        }
        coVerify(exactly = 0) {
            mocks.notificationRepository.markAsDismissedWithoutCancel(any())
        }
        coVerify(exactly = 0) {
            mocks.notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(any())
        }
    }

    test("processNotificationData should dismiss a suppressed regroup when the notification is not posted") {
        // A regroup enqueued while the restore pass is still walking the outstanding list steals
        // the sibling's shade-restore work, since unique work is keyed on the OS id alone. Nothing
        // is posted then, so the suppression has to stick like a shade dismiss or the row comes
        // back on the next restore.
        // Given
        val mocks = Mocks()
        every { mocks.notificationGenerationProcessor getProperty "EXTERNAL_CALLBACKS_TIMEOUT" } answers { 1_000L }
        mockkObject(NotificationHelper)
        every { NotificationHelper.isNotificationActive(any(), any()) } returns false
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            firstArg<INotificationReceivedEvent>().preventDefault(true)
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, NotificationRestoreReason.GROUP_REGROUP, 1111)

        // Then
        coVerify(exactly = 0) {
            mocks.notificationDisplayer.displayNotification(any())
        }
        coVerify(exactly = 1) {
            mocks.notificationRepository.markAsDismissedWithoutCancel(1)
        }
        coVerify(exactly = 0) {
            mocks.notificationRepository.markAsDismissed(any())
        }
        coVerify(exactly = 1) {
            mocks.notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(1)
        }
    }

    test("processNotificationData should not update the summary when a suppressed unposted regroup was already dismissed") {
        // Given
        val mocks = Mocks()
        every { mocks.notificationGenerationProcessor getProperty "EXTERNAL_CALLBACKS_TIMEOUT" } answers { 1_000L }
        mockkObject(NotificationHelper)
        every { NotificationHelper.isNotificationActive(any(), any()) } returns false
        coEvery { mocks.notificationRepository.markAsDismissedWithoutCancel(any()) } returns false
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            firstArg<INotificationReceivedEvent>().preventDefault(true)
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, NotificationRestoreReason.GROUP_REGROUP, 1111)

        // Then
        coVerify(exactly = 1) {
            mocks.notificationRepository.markAsDismissedWithoutCancel(1)
        }
        coVerify(exactly = 0) {
            mocks.notificationSummaryManager.updatePossibleDependentSummaryOnDismiss(any())
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
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, NotificationRestoreReason.SHADE_RESTORE, 1111)

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
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, null, 1111)

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
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, NotificationRestoreReason.SHADE_RESTORE, 1111)

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
        // Same CI flake as the preventDefault-twice cases below: the suite default timeout (10ms)
        // can elapse before Dispatchers.IO runs the callback, so discard is never set and display
        // is attempted against an unstubbed mock.
        every { mocks.notificationGenerationProcessor getProperty "EXTERNAL_CALLBACKS_TIMEOUT" } answers { 1_000L }
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } answers {
            val willDisplayEvent = firstArg<INotificationWillDisplayEvent>()
            // Setting discard parameter to true indicating we should immediately discard
            willDisplayEvent.preventDefault(true)
        }

        // If discard is set to false this should timeout waiting for display()
        withTimeout(1_000) {
            mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, null, 1111)
        }
    }

    test("processNotificationData should immediately drop the notification when received event callback indicates to") {
        // Given
        val mocks = Mocks()
        every { mocks.notificationGenerationProcessor getProperty "EXTERNAL_CALLBACKS_TIMEOUT" } answers { 1_000L }
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } answers {
            val receivedEvent = firstArg<INotificationReceivedEvent>()
            receivedEvent.preventDefault(true)
        }
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } just runs

        // If discard is set to false this should timeout waiting for display()
        withTimeout(1_000) {
            mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, null, 1111)
        }
    }

    test("processNotificationData allows the will display callback to prevent default behavior twice") {
        // Given
        val mocks = Mocks()
        // Bump the callback timeout from the suite default (10ms). launchOnIO is dispatched on a
        // real Dispatchers.IO-backed scope in this spec (see the beforeAny override), and on slow
        // CI runners the IO scheduler can take longer than 10ms to dispatch the callback, causing
        // withTimeout to cancel before discard is ever set.
        every { mocks.notificationGenerationProcessor getProperty "EXTERNAL_CALLBACKS_TIMEOUT" } answers { 1_000L }
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } just runs
        coEvery { mocks.notificationLifecycleService.externalNotificationWillShowInForeground(any()) } coAnswers {
            val willDisplayEvent = firstArg<INotificationWillDisplayEvent>()
            willDisplayEvent.preventDefault(false)
            suspendifyOnIO {
                // Second preventDefault(true) wakes the waiter with false; avoid notification.display()
                // which would wake(true) and overwrite the conflated channel (CI flake on fast runners).
                willDisplayEvent.preventDefault(true)
            }
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, null, 1111)

        // Then
        coVerify(exactly = 0) {
            mocks.notificationDisplayer.displayNotification(any())
        }
    }

    test("processNotificationData allows the received event callback to prevent default behavior twice") {
        // Given
        val mocks = Mocks()
        every { mocks.notificationGenerationProcessor getProperty "EXTERNAL_CALLBACKS_TIMEOUT" } answers { 1_000L }
        coEvery { mocks.notificationDisplayer.displayNotification(any()) } returns true
        coEvery { mocks.notificationLifecycleService.externalRemoteNotificationReceived(any()) } coAnswers {
            val receivedEvent = firstArg<INotificationReceivedEvent>()
            receivedEvent.preventDefault(false)
            suspendifyOnIO {
                receivedEvent.preventDefault(true)
            }
        }

        // When
        mocks.notificationGenerationProcessor.processNotificationData(mocks.context, 1, mocks.notificationPayload, NotificationRestoreReason.SHADE_RESTORE, 1111)

        // Then
        coVerify(exactly = 0) {
            mocks.notificationDisplayer.displayNotification(any())
        }
    }
})
