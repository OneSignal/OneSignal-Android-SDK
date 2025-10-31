package com.onesignal.session.internal.session.impl

import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.operations.TrackSessionStartOperation
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import io.mockk.verify

class SessionListenerTests : FunSpec({

    test("onSessionStarted enqueues TrackSessionStartOperation") {
        // Given
        val mockOperationRepo = mockk<com.onesignal.core.internal.operations.IOperationRepo>(relaxed = true)

        val sessionListener =
            SessionListener(
                mockOperationRepo,
                mockk<com.onesignal.session.internal.session.ISessionService>(relaxed = true),
                MockHelper.configModelStore(),
                MockHelper.identityModelStore(),
                mockk<com.onesignal.session.internal.outcomes.IOutcomeEventsController>(relaxed = true),
            )

        // When
        sessionListener.onSessionStarted()

        // Then
        verify(exactly = 1) {
            mockOperationRepo.enqueue(any<TrackSessionStartOperation>(), true)
        }
    }
})
