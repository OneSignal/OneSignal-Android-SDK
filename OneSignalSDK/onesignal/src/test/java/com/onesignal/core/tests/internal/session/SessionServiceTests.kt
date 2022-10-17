package com.onesignal.core.tests.internal.session

import com.onesignal.core.internal.session.ISessionLifecycleHandler
import com.onesignal.core.internal.session.impl.SessionService
import com.onesignal.core.tests.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.spyk
import io.mockk.verify
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class SessionServiceTests : FunSpec({

    test("session created on focus when current session invalid") {
        /* Given */
        val currentTime = 1111L

        val configModelStoreMock = MockHelper.configModelStore()
        val sessionModelStoreMock = MockHelper.sessionModelStore {
            it.isValid = false
        }
        val spyCallback = spyk<ISessionLifecycleHandler>()

        val sessionService = SessionService(MockHelper.applicationService(), configModelStoreMock, sessionModelStoreMock, MockHelper.time(currentTime))
        sessionService.start()
        sessionService.subscribe(spyCallback)

        /* When */
        sessionService.onFocus()

        /* Then */
        sessionModelStoreMock.get().isValid shouldBe true
        sessionModelStoreMock.get().startTime shouldBe currentTime
        sessionModelStoreMock.get().focusTime shouldBe currentTime
        verify(exactly = 1) { spyCallback.onSessionStarted() }
    }

    test("session focus time updated when current session valid") {
        /* Given */
        val currentTime = 1111L
        val startTime = 555L

        val configModelStoreMock = MockHelper.configModelStore()
        val sessionModelStoreMock = MockHelper.sessionModelStore {
            it.isValid = true
            it.startTime = startTime
        }
        val spyCallback = spyk<ISessionLifecycleHandler>()

        val sessionService = SessionService(MockHelper.applicationService(), configModelStoreMock, sessionModelStoreMock, MockHelper.time(currentTime))
        sessionService.start()
        sessionService.subscribe(spyCallback)

        /* When */
        sessionService.onFocus()

        /* Then */
        sessionModelStoreMock.get().isValid shouldBe true
        sessionModelStoreMock.get().startTime shouldBe startTime
        sessionModelStoreMock.get().focusTime shouldBe currentTime
        verify(exactly = 1) { spyCallback.onSessionActive() }
    }

    test("session active duration updated when unfocused") {
        /* Given */
        val startTime = 555L
        val focusTime = 666L
        val startingDuration = 1000L
        val currentTime = 1111L

        val configModelStoreMock = MockHelper.configModelStore()
        val sessionModelStoreMock = MockHelper.sessionModelStore {
            it.isValid = true
            it.startTime = startTime
            it.focusTime = focusTime
            it.activeDuration = startingDuration
        }

        val sessionService = SessionService(MockHelper.applicationService(), configModelStoreMock, sessionModelStoreMock, MockHelper.time(currentTime))
        sessionService.start()

        /* When */
        sessionService.onUnfocused()

        /* Then */
        sessionModelStoreMock.get().isValid shouldBe true
        sessionModelStoreMock.get().startTime shouldBe startTime
        sessionModelStoreMock.get().activeDuration shouldBe startingDuration + (currentTime - focusTime)
    }

    test("session ended when background run") {
        /* Given */
        val activeDuration = 555L
        val currentTime = 1111L

        val configModelStoreMock = MockHelper.configModelStore()
        val sessionModelStoreMock = MockHelper.sessionModelStore {
            it.isValid = true
            it.activeDuration = activeDuration
        }
        val spyCallback = spyk<ISessionLifecycleHandler>()

        val sessionService = SessionService(MockHelper.applicationService(), configModelStoreMock, sessionModelStoreMock, MockHelper.time(currentTime))
        sessionService.start()
        sessionService.subscribe(spyCallback)

        /* When */
        sessionService.backgroundRun()

        /* Then */
        sessionModelStoreMock.get().isValid shouldBe false
        verify(exactly = 1) { spyCallback.onSessionEnded(activeDuration) }
    }
})
