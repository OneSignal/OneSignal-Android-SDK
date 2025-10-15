package com.onesignal.session.internal.session.impl

import com.onesignal.common.TimeUtils
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.operations.TrackSessionStartOperation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify

class SessionListenerTests : FunSpec({

    test("onSessionStarted updates timezone and enqueues TrackSessionStartOperation") {
        // Given
        val mockTimeZone = "Europe/London"
        mockkObject(TimeUtils)
        every { TimeUtils.getTimeZoneId() } returns mockTimeZone

        val mockOperationRepo = mockk<com.onesignal.core.internal.operations.IOperationRepo>(relaxed = true)
        val mockSessionService = mockk<com.onesignal.session.internal.session.ISessionService>(relaxed = true)
        val mockConfigModelStore = MockHelper.configModelStore()
        val mockIdentityModelStore = MockHelper.identityModelStore()
        val mockOutcomeEventsController = mockk<com.onesignal.session.internal.outcomes.IOutcomeEventsController>(relaxed = true)

        val mockPropertiesModelStore = MockHelper.propertiesModelStore()

        val sessionListener =
            SessionListener(
                mockOperationRepo,
                mockSessionService,
                mockConfigModelStore,
                mockIdentityModelStore,
                mockPropertiesModelStore,
                mockOutcomeEventsController,
            )

        try {
            // When
            sessionListener.onSessionStarted()

            // Then - Verify that update() was called (timezone should be set to our mocked value)
            val propertiesModel = mockPropertiesModelStore.model
            propertiesModel.timezone shouldBe mockTimeZone

            // Also verify the operation was enqueued
            verify(exactly = 1) {
                mockOperationRepo.enqueue(any<TrackSessionStartOperation>(), true)
            }
        } finally {
            // Clean up the mock
            unmockkObject(TimeUtils)
        }
    }
})
