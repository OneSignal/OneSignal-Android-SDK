package com.onesignal.session.internal.session

import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.runOnSerialIO
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.features.FeatureFlag
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.session.internal.session.impl.SessionService
import com.onesignal.session.internal.session.queue.ISessionsApiQueue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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

// Mocks used by every test in this file
private class Mocks(
    var sessionsApiEnabled: Boolean = false,
) {
    val currentTime = 1111L
    var elapsedRealtime = 5000L

    private val mockSessionModelStore = MockHelper.sessionModelStore()

    val time: ITime =
        mockk<ITime>().also {
            every { it.currentTimeMillis } returns currentTime
            every { it.elapsedRealtimeMillis } answers { elapsedRealtime }
        }

    val featureManager: IFeatureManager =
        mockk<IFeatureManager>().also {
            every { it.isEnabled(FeatureFlag.SDK_SESSIONS_V2_API_CUTOVER) } answers { sessionsApiEnabled }
        }

    val identityModelStore = MockHelper.identityModelStore { it.onesignalId = ONESIGNAL_ID }
    val configModelStore =
        MockHelper.configModelStore {
            it.appId = APP_ID
            it.pushSubscriptionId = SUBSCRIPTION_ID
        }

    val sessionsApiQueue = mockk<ISessionsApiQueue>(relaxed = true)

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
            sessionsApiQueue,
        )

    val spyCallback = spyk<ISessionLifecycleHandler>()

    companion object {
        const val APP_ID = "app-id"
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

    test("new session enqueues a sessions API create when the flag is on") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }

        // When
        sessionService.onFocus(false)

        // Then
        val sessionId = sessionModelStore.model.sessionId
        verify(exactly = 1) {
            mocks.sessionsApiQueue.enqueueCreate(
                sessionId,
                Mocks.APP_ID,
                Mocks.ONESIGNAL_ID,
                Mocks.SUBSCRIPTION_ID,
                mocks.currentTime,
            )
        }
    }

    test("new session does not enqueue a create when the flag is off") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = false)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }

        // When
        sessionService.onFocus(false)

        // Then
        verify(exactly = 0) { mocks.sessionsApiQueue.enqueueCreate(any(), any(), any(), any(), any()) }
    }

    test("resuming a session does not enqueue another create") {
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
        verify(exactly = 1) { mocks.sessionsApiQueue.enqueueCreate(any(), any(), any(), any(), any()) }
    }

    test("new session does not enqueue a create without a push subscription ID") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.configModelStore.model.pushSubscriptionId = null
        Logging.logLevel = LogLevel.NONE
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }

        // When
        sessionService.onFocus(false)

        // Then
        verify(exactly = 0) { mocks.sessionsApiQueue.enqueueCreate(any(), any(), any(), any(), any()) }
    }

    test("background run enqueues a sessions API end and flushes it") {
        // Given
        val mocks = Mocks()
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore {
            it.sessionId = "session-id"
            it.isValid = true
            it.usesSessionsApi = true
            it.focusTime = 1000L
            it.unfocusTime = 2000L
            it.activeDuration = 555L
        }

        // When
        sessionService.backgroundRun()

        // Then
        coVerifyOrder {
            mocks.sessionsApiQueue.enqueueUpdate("session-id", 555L, 2000L)
            mocks.sessionsApiQueue.flush()
        }
    }

    test("session end time is the last focus when the app was killed while focused") {
        // Given
        val mocks = Mocks()
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore {
            it.sessionId = "session-id"
            it.isValid = true
            it.usesSessionsApi = true
            it.focusTime = 3000L
            it.unfocusTime = 2000L
            it.activeDuration = 555L
        }

        // When
        sessionService.backgroundRun()

        // Then
        verify { mocks.sessionsApiQueue.enqueueUpdate("session-id", 555L, 3000L) }
    }

    test("legacy session end does not use the sessions API") {
        // Given
        val mocks = Mocks()
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore {
            it.isValid = true
            it.usesSessionsApi = false
        }

        // When
        sessionService.backgroundRun()

        // Then
        verify(exactly = 0) { mocks.sessionsApiQueue.enqueueUpdate(any(), any(), any()) }
        coVerify(exactly = 0) { mocks.sessionsApiQueue.flush() }
    }

    test("cold start ends the previous session before creating the next one") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore =
            mocks.sessionModelStore {
                it.sessionId = "previous-session"
                it.isValid = true
                it.usesSessionsApi = true
                it.focusTime = 1000L
                it.unfocusTime = 900L
                it.activeDuration = 555L
            }

        // When
        sessionService.onFocus(false)

        // Then
        val sessionId = sessionModelStore.model.sessionId
        verifyOrder {
            mocks.sessionsApiQueue.enqueueUpdate("previous-session", 555L, 1000L)
            mocks.sessionsApiQueue.enqueueCreate(sessionId, any(), any(), any(), any())
        }
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
        store.model.focusElapsedRealtime = 789L
        store.model.usesSessionsApi = true
        store.model.unfocusTime = 321L

        // When
        val reloaded = SessionModelStore(prefs).model

        // Then
        reloaded.sessionId shouldBe "session-id"
        reloaded.startTime shouldBe 123L
        reloaded.activeDuration shouldBe 456L
        reloaded.focusElapsedRealtime shouldBe 789L
        reloaded.usesSessionsApi shouldBe true
        reloaded.unfocusTime shouldBe 321L
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
