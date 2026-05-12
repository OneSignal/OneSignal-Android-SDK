package com.onesignal.session.internal.session

import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.runOnSerialIOIfBackgroundThreading
import com.onesignal.mocks.MockHelper
import com.onesignal.session.internal.session.impl.SessionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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
private class Mocks {
    val currentTime = 1111L

    private val mockSessionModelStore = MockHelper.sessionModelStore()

    fun sessionModelStore(action: ((SessionModel) -> Unit)? = null): SessionModelStore {
        if (action != null) action(mockSessionModelStore.model)
        return mockSessionModelStore
    }

    val sessionService =
        SessionService(MockHelper.applicationService(), MockHelper.configModelStore(), mockSessionModelStore, MockHelper.time(currentTime))

    val spyCallback = spyk<ISessionLifecycleHandler>()
}

class SessionServiceTests : FunSpec({
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

    test("onFocus dispatches the session-mutation body through runOnSerialIOIfBackgroundThreading (SDK-4508)") {
        // SDK-4508: SessionService.onFocus runs on the main thread via
        // ApplicationService.handleFocus -> applicationLifecycleNotifier.fire. Its body fires
        // session lifecycle handlers (operation repo, IAM trigger eval, etc.) which can in turn
        // touch OneSignalDispatchers' cold-init chain. The fix wraps the body in
        // runOnSerialIOIfBackgroundThreading; this test pins down the dispatch contract.
        //
        // Stub the helper as a pass-through so the underlying state mutations still happen
        // (`startTime`, `focusTime`, lifecycle-handler fires) and the existing assertions
        // about session state remain meaningful.
        val threadUtilsPath = "com.onesignal.common.threading.ThreadUtilsKt"
        mockkStatic(threadUtilsPath)
        mockkObject(OneSignalDispatchers)
        every { runOnSerialIOIfBackgroundThreading(any<() -> Unit>()) } answers {
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

            verify(exactly = 1) { runOnSerialIOIfBackgroundThreading(any<() -> Unit>()) }
        } finally {
            unmockkObject(OneSignalDispatchers)
            unmockkStatic(threadUtilsPath)
        }
    }

    test("onUnfocused dispatches the activeDuration update through runOnSerialIOIfBackgroundThreading (SDK-4508)") {
        val threadUtilsPath = "com.onesignal.common.threading.ThreadUtilsKt"
        mockkStatic(threadUtilsPath)
        mockkObject(OneSignalDispatchers)
        every { runOnSerialIOIfBackgroundThreading(any<() -> Unit>()) } answers {
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

            verify(exactly = 1) { runOnSerialIOIfBackgroundThreading(any<() -> Unit>()) }
        } finally {
            unmockkObject(OneSignalDispatchers)
            unmockkStatic(threadUtilsPath)
        }
    }

    test("rapid onUnfocused -> onFocus burst dispatches each event through the gated helper in submission order (SDK-4508)") {
        // Mirrors the SDK-4505 BackgroundManager burst test. Real-world scenario: the user
        // backgrounds then immediately re-foregrounds the app on the main thread. Both lifecycle
        // events must route through the same gated helper in submission order so the serial IO
        // worker sees focusTime / activeDuration mutations in main-thread arrival order. If they
        // ever raced across the IO pool, activeDuration accounting could drift.
        val threadUtilsPath = "com.onesignal.common.threading.ThreadUtilsKt"
        mockkStatic(threadUtilsPath)
        mockkObject(OneSignalDispatchers)
        every { runOnSerialIOIfBackgroundThreading(any<() -> Unit>()) } just runs
        every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        try {
            val mocks = Mocks()
            val sessionService = mocks.sessionService
            sessionService.bootstrap()
            sessionService.start()
            mocks.sessionModelStore { it.isValid = true }

            sessionService.onUnfocused()
            sessionService.onFocus(firedOnSubscribe = false)

            verify(exactly = 2) { runOnSerialIOIfBackgroundThreading(any<() -> Unit>()) }
            verifyOrder {
                runOnSerialIOIfBackgroundThreading(any<() -> Unit>())
                runOnSerialIOIfBackgroundThreading(any<() -> Unit>())
            }
        } finally {
            unmockkObject(OneSignalDispatchers)
            unmockkStatic(threadUtilsPath)
        }
    }
})
