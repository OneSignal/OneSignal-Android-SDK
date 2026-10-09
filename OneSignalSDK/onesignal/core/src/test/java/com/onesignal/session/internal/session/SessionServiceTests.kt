package com.onesignal.session.internal.session

import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.runOnSerialIO
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.features.FeatureFlag
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.session.internal.session.impl.SessionService
import com.onesignal.session.internal.session.operations.CreateSessionOperation
import com.onesignal.session.internal.session.operations.UpdateSessionOperation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest

// Mocks used by every test in this file
private class Mocks(
    var sessionsApiEnabled: Boolean = false,
) {
    var currentTime = 1111L
    var elapsedRealtime = 5000L

    private val mockSessionModelStore = MockHelper.sessionModelStore()

    val time: ITime =
        mockk<ITime>().also {
            every { it.currentTimeMillis } answers { currentTime }
            every { it.elapsedRealtimeMillis } answers { elapsedRealtime }
        }

    val featureManager: IFeatureManager =
        mockk<IFeatureManager>().also {
            every { it.isEnabled(FeatureFlag.SDK_SESSIONS_V2_API_CUTOVER) } answers { sessionsApiEnabled }
        }

    val identityModelStore = MockHelper.identityModelStore { it.onesignalId = ONESIGNAL_ID }
    val configModelStore = MockHelper.configModelStore { it.pushSubscriptionId = SUBSCRIPTION_ID }

    val enqueued = mutableListOf<Operation>()
    val operationRepo: IOperationRepo =
        mockk<IOperationRepo>().also {
            every { it.enqueue(capture(enqueued), any()) } just runs
            coEvery { it.enqueueAndWait(capture(enqueued), any()) } returns true
        }

    fun sessionModelStore(action: ((SessionModel) -> Unit)? = null): SessionModelStore {
        if (action != null) action(mockSessionModelStore.model)
        return mockSessionModelStore
    }

    val sessionService =
        SessionService(
            MockHelper.applicationService(),
            configModelStore,
            mockSessionModelStore,
            time,
            featureManager,
            identityModelStore,
            operationRepo,
        )

    val spyCallback = spyk<ISessionLifecycleHandler>()

    companion object {
        const val ONESIGNAL_ID = "onesignal-id"
        const val SUBSCRIPTION_ID = "subscription-id"
    }
}

class SessionServiceTests : FunSpec({
    // SessionService.onFocus/onUnfocused dispatch their state mutation through the now-always-async
    // runOnSerialIO. IOMockHelper.beforeSpec stubs runOnSerialIO to run inline so the tests below
    // can assert session state synchronously after the call. The SDK-4508 tests further down
    // re-mock/unmock runOnSerialIO themselves to assert the dispatch contract; they run last, so
    // their finally-unmock does not affect the earlier inline-dispatch tests.
    listener(IOMockHelper)

    beforeEach { Logging.logLevel = LogLevel.NONE }

    test("session created on focus when current session invalid") {
        // Given
        val mocks = Mocks()
        val sessionService = mocks.sessionService

        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)

        // When
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.isValid shouldBe true
        sessionModelStore.model.startTime shouldBe mocks.currentTime
        sessionModelStore.model.focusTime shouldBe mocks.currentTime
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }
    }

    test("session created in start when application is in the foreground") {
        // Given
        val mocks = Mocks()
        val sessionService = mocks.sessionService
        val sessionModelStore = mocks.sessionModelStore()

        // When
        sessionService.bootstrap()
        sessionService.start()
        sessionService.onFocus(true)
        sessionService.subscribe(mocks.spyCallback)

        // Then
        sessionModelStore.model.isValid shouldBe true
        sessionModelStore.model.startTime shouldBe mocks.currentTime
        sessionModelStore.model.focusTime shouldBe mocks.currentTime
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }

        // When
        sessionService.onFocus(false) // Should not trigger a second session

        // Then
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }
    }

    test("session focus time updated when current session valid") {
        // Given
        val startTime = 555L
        val mocks = Mocks()
        val sessionService = mocks.sessionService

        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore =
            mocks.sessionModelStore {
                it.startTime = startTime
                it.isValid = true
            }
        sessionService.subscribe(mocks.spyCallback)

        // When
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.isValid shouldBe true
        sessionModelStore.model.startTime shouldBe mocks.currentTime
        sessionModelStore.model.focusTime shouldBe mocks.currentTime
        verify(exactly = 0) { mocks.spyCallback.onSessionActive() }
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }
    }

    test("session active duration updated when unfocused") {
        // Given
        val startTime = 555L
        val focusTime = 666L
        val startingDuration = 1000L

        val mocks = Mocks()
        val sessionService = mocks.sessionService

        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore =
            mocks.sessionModelStore {
                it.isValid = true
                it.startTime = startTime
                it.focusTime = focusTime
                it.activeDuration = startingDuration
            }

        // When
        sessionService.onUnfocused()

        // Then
        sessionModelStore.model.isValid shouldBe true
        sessionModelStore.model.startTime shouldBe startTime
        sessionModelStore.model.activeDuration shouldBe startingDuration + (mocks.currentTime - focusTime)
    }

    test("session ended when background run") {
        // Given
        val activeDuration = 555L
        val mocks = Mocks()
        val sessionService = mocks.sessionService

        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore =
            mocks.sessionModelStore {
                it.isValid = true
                it.activeDuration = activeDuration
            }
        sessionService.subscribe(mocks.spyCallback)

        // When
        sessionService.backgroundRun()

        // Then
        sessionModelStore.model.isValid shouldBe false
        verify(exactly = 1) { mocks.spyCallback.onSessionEnded(activeDuration) }
    }

    test("do not trigger onSessionEnd if session is not active") {
        // Given
        val mocks = Mocks()
        mocks.sessionModelStore { it.isValid = false }
        val sessionService = mocks.sessionService
        sessionService.subscribe(mocks.spyCallback)
        sessionService.bootstrap()
        sessionService.start()

        // When
        sessionService.backgroundRun()

        // Then
        verify(exactly = 0) { mocks.spyCallback.onSessionEnded(any()) }
    }

    test("new session uses the sessions API when the flag is on") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }

        // When
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.usesSessionsApi shouldBe true
    }

    test("new session uses the legacy path when the flag is off") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = false)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }

        // When
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.usesSessionsApi shouldBe false
    }

    test("sessions API choice does not change mid-session") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.sessionsApiEnabled = false
        sessionService.onUnfocused()
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.usesSessionsApi shouldBe true
    }

    test("new session pins onesignal and subscription IDs and clears the server session ID") {
        // Given
        val mocks = Mocks()
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore =
            mocks.sessionModelStore {
                it.isValid = false
                it.serverSessionId = "previous-server-session"
            }

        // When
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.onesignalId shouldBe Mocks.ONESIGNAL_ID
        sessionModelStore.model.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
        sessionModelStore.model.serverSessionId shouldBe null
    }

    test("pinned IDs stay the same after login within a session") {
        // Given
        val mocks = Mocks()
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.identityModelStore.model.onesignalId = "new-user-id"
        mocks.configModelStore.model.pushSubscriptionId = "new-subscription-id"
        sessionService.onUnfocused()
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.onesignalId shouldBe Mocks.ONESIGNAL_ID
        sessionModelStore.model.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
    }

    test("new session enqueues a create with the start time and pinned IDs when the flag is on") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }

        // When
        sessionService.onFocus(false)

        // Then
        val create = mocks.enqueued.single() as CreateSessionOperation
        create.appId shouldBe MockHelper.DEFAULT_APP_ID
        create.localSessionId shouldBe sessionModelStore.model.localSessionId
        create.onesignalId shouldBe Mocks.ONESIGNAL_ID
        create.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
        create.startTime shouldBe mocks.currentTime
        create.directAttributionId shouldBe null
    }

    test("each new session enqueues its own create with a new idempotency key") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        sessionService.onUnfocused()

        // When
        sessionService.backgroundRun()
        sessionService.onFocus(false)

        // Then
        val creates = mocks.enqueued.filterIsInstance<CreateSessionOperation>()
        creates.size shouldBe 2
        creates.map { it.localSessionId }.distinct().size shouldBe 2
        creates.map { it.idempotencyKey }.distinct().size shouldBe 2
    }

    test("resuming within the session timeout does not enqueue another create") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        sessionService.onUnfocused()
        sessionService.onFocus(false)

        // Then
        mocks.enqueued.size shouldBe 1
    }

    test("cold start with a persisted valid session enqueues a create for the new session") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionModelStore =
            mocks.sessionModelStore {
                it.isValid = true
                it.sessionId = "previous-session"
            }
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()

        // When
        sessionService.onFocus(true)

        // Then
        val create = mocks.enqueued.single() as CreateSessionOperation
        create.localSessionId shouldBe sessionModelStore.model.localSessionId
        sessionModelStore.model.sessionId shouldNotBe "previous-session"
    }

    test("new session does not enqueue a create when the flag is off") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = false)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)

        // When
        sessionService.onFocus(false)

        // Then
        mocks.enqueued shouldBe emptyList()
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }
    }

    test("new session does not enqueue a create without a push subscription ID") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.configModelStore.model.pushSubscriptionId = null
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)

        // When
        sessionService.onFocus(false)

        // Then
        mocks.enqueued shouldBe emptyList()
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }
    }

    test("background run sends the end with the unfocus time and final duration when the flag is on") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.elapsedRealtime += 750L
        mocks.currentTime = 2222L
        sessionService.onUnfocused()
        mocks.currentTime = 32222L

        // When
        sessionService.backgroundRun()

        // Then
        val end = mocks.enqueued.filterIsInstance<UpdateSessionOperation>().single()
        end.appId shouldBe MockHelper.DEFAULT_APP_ID
        end.sessionId shouldBe sessionModelStore.model.localSessionId
        end.onesignalId shouldBe Mocks.ONESIGNAL_ID
        end.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
        end.activeDuration shouldBe 750L
        end.endTime shouldBe 2222L
        coVerify(exactly = 1) { mocks.operationRepo.enqueueAndWait(end, true) }
        sessionModelStore.model.isValid shouldBe false
    }

    test("background run ends the session by its server session ID once created") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        sessionModelStore.model.serverSessionId = "server-session-id"
        sessionService.onUnfocused()

        // When
        sessionService.backgroundRun()

        // Then
        val end = mocks.enqueued.filterIsInstance<UpdateSessionOperation>().single()
        end.sessionId shouldBe "server-session-id"
    }

    test("background run returns when the end is not sent in time and leaves it queued") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        coEvery { mocks.operationRepo.enqueueAndWait(any(), any()) } coAnswers { awaitCancellation() }
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        sessionService.onUnfocused()

        // When
        runTest { sessionService.backgroundRun() }

        // Then
        coVerify(exactly = 1) { mocks.operationRepo.enqueueAndWait(any<UpdateSessionOperation>(), true) }
        sessionModelStore.model.isValid shouldBe false
    }

    test("cold start ends the previous sessions API session before creating a new one") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.sessionModelStore {
            it.isValid = true
            it.sessionId = "previous-session"
            it.usesSessionsApi = true
            it.onesignalId = Mocks.ONESIGNAL_ID
            it.subscriptionId = Mocks.SUBSCRIPTION_ID
            it.serverSessionId = "previous-server-session-id"
            it.focusTime = 500L
            it.unfocusTime = 900L
            it.activeDuration = 400L
        }
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()

        // When
        sessionService.onFocus(false)

        // Then
        val end = mocks.enqueued[0] as UpdateSessionOperation
        end.sessionId shouldBe "previous-server-session-id"
        end.activeDuration shouldBe 400L
        end.endTime shouldBe 900L
        (mocks.enqueued[1] is CreateSessionOperation) shouldBe true
        coVerify(exactly = 0) { mocks.operationRepo.enqueueAndWait(any(), any()) }
    }

    test("cold start after a kill while focused ends the previous session at its last focus") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.sessionModelStore {
            it.isValid = true
            it.sessionId = "previous-session"
            it.usesSessionsApi = true
            it.onesignalId = Mocks.ONESIGNAL_ID
            it.subscriptionId = Mocks.SUBSCRIPTION_ID
            it.unfocusTime = 300L
            it.focusTime = 500L
        }
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()

        // When
        sessionService.onFocus(false)

        // Then
        val end = mocks.enqueued.filterIsInstance<UpdateSessionOperation>().single()
        end.endTime shouldBe 500L
    }

    test("session end does not send an end when the session uses the legacy path") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = false)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)
        sessionService.onFocus(false)
        sessionService.onUnfocused()
        mocks.sessionsApiEnabled = true

        // When
        sessionService.backgroundRun()

        // Then
        mocks.enqueued shouldBe emptyList()
        coVerify(exactly = 0) { mocks.operationRepo.enqueueAndWait(any(), any()) }
        verify(exactly = 1) { mocks.spyCallback.onSessionEnded(any()) }
    }

    test("session end does not send an end without a push subscription ID") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.configModelStore.model.pushSubscriptionId = null
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)
        sessionService.onFocus(false)
        sessionService.onUnfocused()

        // When
        sessionService.backgroundRun()

        // Then
        mocks.enqueued shouldBe emptyList()
        verify(exactly = 1) { mocks.spyCallback.onSessionEnded(any()) }
    }

    test("sessions API active duration uses the monotonic clock") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.elapsedRealtime += 750L
        sessionService.onUnfocused()

        // Then
        sessionModelStore.model.activeDuration shouldBe 750L
    }

    test("sessions API active duration excludes background time between focuses") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.elapsedRealtime += 300L
        sessionService.onUnfocused()

        // When
        mocks.elapsedRealtime += 10_000L
        sessionService.onFocus(false)
        mocks.elapsedRealtime += 200L
        sessionService.onUnfocused()

        // Then
        sessionModelStore.model.activeDuration shouldBe 500L
    }

    test("sessions API active duration ignores an interval spanning a reboot") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore =
            mocks.sessionModelStore {
                it.isValid = true
                it.usesSessionsApi = true
                it.focusElapsedRealtime = 10_000L
                it.activeDuration = 200L
            }

        // When
        mocks.elapsedRealtime = 100L
        sessionService.onUnfocused()

        // Then
        sessionModelStore.model.activeDuration shouldBe 200L
    }

    test("session model fields survive a reload from preferences") {
        // Given
        val prefs = MockPreferencesService()
        val store = SessionModelStore(prefs)
        store.model.sessionId = "session-id"
        store.model.startTime = 123L
        store.model.activeDuration = 456L
        store.model.unfocusTime = 321L
        store.model.focusElapsedRealtime = 789L
        store.model.usesSessionsApi = true
        store.model.onesignalId = Mocks.ONESIGNAL_ID
        store.model.subscriptionId = Mocks.SUBSCRIPTION_ID
        store.model.serverSessionId = "server-session-id"

        // When
        val reloaded = SessionModelStore(prefs).model

        // Then
        reloaded.sessionId shouldBe "session-id"
        reloaded.startTime shouldBe 123L
        reloaded.activeDuration shouldBe 456L
        reloaded.unfocusTime shouldBe 321L
        reloaded.focusElapsedRealtime shouldBe 789L
        reloaded.usesSessionsApi shouldBe true
        reloaded.onesignalId shouldBe Mocks.ONESIGNAL_ID
        reloaded.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
        reloaded.serverSessionId shouldBe "server-session-id"
    }

    test("onFocus dispatches the session-mutation body through runOnSerialIO (SDK-4508)") {
        // SDK-4508: SessionService.onFocus runs on the main thread via
        // ApplicationService.handleFocus -> applicationLifecycleNotifier.fire. Its body fires
        // session lifecycle handlers (operation repo, IAM trigger eval, etc.) which can in turn
        // touch OneSignalDispatchers' cold-init chain. The fix wraps the body in
        // runOnSerialIO; this test pins down the dispatch contract.
        //
        // Stub the helper as a pass-through so the underlying state mutations still happen
        // (`startTime`, `focusTime`, lifecycle-handler fires) and the existing assertions
        // about session state remain meaningful.
        val threadUtilsPath = "com.onesignal.common.threading.ThreadUtilsKt"
        mockkStatic(threadUtilsPath)
        mockkObject(OneSignalDispatchers)
        every { runOnSerialIO(any<() -> Unit>()) } answers {
            firstArg<() -> Unit>().invoke()
        }
        every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        try {
            val mocks = Mocks()
            val sessionService = mocks.sessionService
            sessionService.bootstrap()
            sessionService.start()
            mocks.sessionModelStore { it.isValid = false }

            sessionService.onFocus(firedOnSubscribe = false)

            verify(exactly = 1) { runOnSerialIO(any<() -> Unit>()) }
        } finally {
            unmockkObject(OneSignalDispatchers)
            unmockkStatic(threadUtilsPath)
        }
    }

    test("onUnfocused dispatches the activeDuration update through runOnSerialIO (SDK-4508)") {
        val threadUtilsPath = "com.onesignal.common.threading.ThreadUtilsKt"
        mockkStatic(threadUtilsPath)
        mockkObject(OneSignalDispatchers)
        every { runOnSerialIO(any<() -> Unit>()) } answers {
            firstArg<() -> Unit>().invoke()
        }
        every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        try {
            val mocks = Mocks()
            val sessionService = mocks.sessionService
            sessionService.bootstrap()
            sessionService.start()
            mocks.sessionModelStore {
                it.isValid = true
                it.focusTime = 0L
            }

            sessionService.onUnfocused()

            verify(exactly = 1) { runOnSerialIO(any<() -> Unit>()) }
        } finally {
            unmockkObject(OneSignalDispatchers)
            unmockkStatic(threadUtilsPath)
        }
    }

    test("rapid onUnfocused -> onFocus burst dispatches each event through the serial IO helper in submission order (SDK-4508)") {
        // Mirrors the SDK-4505 BackgroundManager burst test. Real-world scenario: the user
        // backgrounds then immediately re-foregrounds the app on the main thread. Both lifecycle
        // events must route through the same serial IO helper in submission order so the serial IO
        // worker sees focusTime / activeDuration mutations in main-thread arrival order. If they
        // ever raced across the IO pool, activeDuration accounting could drift.
        val threadUtilsPath = "com.onesignal.common.threading.ThreadUtilsKt"
        mockkStatic(threadUtilsPath)
        mockkObject(OneSignalDispatchers)
        every { runOnSerialIO(any<() -> Unit>()) } just runs
        every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        try {
            val mocks = Mocks()
            val sessionService = mocks.sessionService
            sessionService.bootstrap()
            sessionService.start()
            mocks.sessionModelStore { it.isValid = true }

            sessionService.onUnfocused()
            sessionService.onFocus(firedOnSubscribe = false)

            verify(exactly = 2) { runOnSerialIO(any<() -> Unit>()) }
            verifyOrder {
                runOnSerialIO(any<() -> Unit>())
                runOnSerialIO(any<() -> Unit>())
            }
        } finally {
            unmockkObject(OneSignalDispatchers)
            unmockkStatic(threadUtilsPath)
        }
    }
})
