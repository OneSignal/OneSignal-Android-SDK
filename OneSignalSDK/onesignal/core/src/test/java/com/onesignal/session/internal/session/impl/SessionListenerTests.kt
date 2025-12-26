package com.onesignal.session.internal.session.impl

import com.onesignal.common.TimeUtils
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.operations.TrackSessionStartOperation
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify

private class Mocks {
    val mockOperationRepo = mockk<com.onesignal.core.internal.operations.IOperationRepo>(relaxed = true)

    val propertiesModelStore = MockHelper.propertiesModelStore()

    val sessionListener =
        SessionListener(
            mockOperationRepo,
            mockk<com.onesignal.session.internal.session.ISessionService>(relaxed = true),
            MockHelper.configModelStore(),
            MockHelper.identityModelStore(),
            propertiesModelStore,
            mockk<com.onesignal.session.internal.outcomes.IOutcomeEventsController>(relaxed = true),
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
})
