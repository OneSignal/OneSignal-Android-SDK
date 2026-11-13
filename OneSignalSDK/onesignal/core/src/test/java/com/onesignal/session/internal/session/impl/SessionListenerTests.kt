package com.onesignal.session.internal.session.impl

import com.onesignal.common.TimeUtils
import com.onesignal.mocks.MockHelper
import com.onesignal.session.internal.outcomes.IOutcomeEventsController
import com.onesignal.user.internal.operations.TrackSessionEndOperation
import com.onesignal.user.internal.operations.TrackSessionStartOperation
import io.kotest.core.spec.style.FunSpec
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify

private class Mocks(
    awaitingConsent: Boolean = false,
) {
    val mockOperationRepo = mockk<com.onesignal.core.internal.operations.IOperationRepo>(relaxed = true)

    val propertiesModelStore = MockHelper.propertiesModelStore()

    val outcomeEventsController = mockk<IOutcomeEventsController>(relaxed = true)

    val sessionModelStore = MockHelper.sessionModelStore()

    val sessionListener =
        SessionListener(
            mockOperationRepo,
            mockk<com.onesignal.session.internal.session.ISessionService>(relaxed = true),
            MockHelper.configModelStore {
                if (awaitingConsent) {
                    it.consentRequired = true
                    it.consentGiven = false
                }
            },
            MockHelper.identityModelStore(),
            propertiesModelStore,
            outcomeEventsController,
            sessionModelStore,
        )
}

class SessionListenerTests : FunSpec({

    test("onSessionStarted enqueues TrackSessionStartOperation") {
        // Given
        val mocks = Mocks()
        val mockOperationRepo = mocks.mockOperationRepo
        val sessionListener = mocks.sessionListener

        // When
        sessionListener.onSessionStarted()

        // Then
        verify(exactly = 1) {
            mockOperationRepo.enqueue(any<TrackSessionStartOperation>(), true)
        }
    }

    test("onSessionStarted updates timezone") {
        // Given
        val mocks = Mocks()
        val sessionListener = mocks.sessionListener
        val propertiesModel = mocks.propertiesModelStore.model
        var timezoneId = "1"
        mockkObject(TimeUtils)
        every { TimeUtils.getTimeZoneId() } returns timezoneId

        // When
        sessionListener.onSessionStarted()

        // Then
        propertiesModel.timezone = timezoneId

        // Once timezone is changed, repetitive onSessionStarted call will update timezone
        timezoneId = "2"
        sessionListener.onSessionStarted()
        propertiesModel.timezone = timezoneId

        unmockkAll()
    }

    test("onSessionActive updates timezone") {
        // Given
        val mocks = Mocks()
        val sessionListener = mocks.sessionListener
        val propertiesModel = mocks.propertiesModelStore.model
        var timezoneId = "1"
        mockkObject(TimeUtils)
        every { TimeUtils.getTimeZoneId() } returns timezoneId

        // When
        sessionListener.onSessionStarted()

        // Once timezone is changed, onSessionActive will update timezone
        timezoneId = "2"
        sessionListener.onSessionActive()
        propertiesModel.timezone = timezoneId

        unmockkAll()
    }

    test("onSessionEnded sends the session duration outcome for a legacy session") {
        // Given
        val mocks = Mocks()

        // When
        mocks.sessionListener.onSessionEnded(5_000, usesSessionsApi = false)

        // Then
        verify(exactly = 1) { mocks.mockOperationRepo.enqueue(any<TrackSessionEndOperation>()) }
        coVerify(exactly = 1, timeout = 1_000) { mocks.outcomeEventsController.sendSessionEndOutcomeEvent(5) }
    }

    test("onSessionStarted counts a legacy session") {
        // Given
        val mocks = Mocks()
        mocks.sessionModelStore.model.usesSessionsApi = false

        // When
        mocks.sessionListener.onSessionStarted()

        // Then
        verify(exactly = 1) {
            mocks.mockOperationRepo.enqueue(match<TrackSessionStartOperation> { it.countsSession }, true)
        }
    }

    test("onSessionStarted does not count a sessions API session") {
        // Given
        val mocks = Mocks()
        mocks.sessionModelStore.model.usesSessionsApi = true

        // When
        mocks.sessionListener.onSessionStarted()

        // Then
        verify(exactly = 1) {
            mocks.mockOperationRepo.enqueue(match<TrackSessionStartOperation> { !it.countsSession }, true)
        }
    }

    test("onSessionEnded skips session time and the duration outcome for a sessions API session") {
        // Given
        val mocks = Mocks()

        // When
        mocks.sessionListener.onSessionEnded(5_000, usesSessionsApi = true)

        // Then
        verify(exactly = 0) { mocks.mockOperationRepo.enqueue(any(), any()) }
        coVerify(exactly = 0) { mocks.outcomeEventsController.sendSessionEndOutcomeEvent(any()) }
    }

    test("onSessionStarted without consent enqueues nothing") {
        // Given
        val mocks = Mocks(awaitingConsent = true)

        // When
        mocks.sessionListener.onSessionStarted()

        // Then
        verify(exactly = 0) { mocks.mockOperationRepo.enqueue(any(), any()) }
    }

    test("onSessionEnded without consent still reports a legacy session's time, which waits for consent") {
        // Given
        val mocks = Mocks(awaitingConsent = true)

        // When
        mocks.sessionListener.onSessionEnded(5_000, usesSessionsApi = false)

        // Then
        verify(exactly = 1) { mocks.mockOperationRepo.enqueue(any<TrackSessionEndOperation>(), any()) }
        coVerify(timeout = 1_000, exactly = 1) { mocks.outcomeEventsController.sendSessionEndOutcomeEvent(5) }
    }
})
