package com.onesignal.session.internal

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.outcomes.IOutcomeEventsController
import io.kotest.core.spec.style.FunSpec
import io.mockk.coVerify
import io.mockk.mockk

class SessionManagerTests : FunSpec({
    beforeEach {
        // NONE: otherwise Logging.error calls the unmocked android.util.Log.
        Logging.logLevel = LogLevel.NONE
    }

    test("empty outcome names are not sent") {
        val controller = mockk<IOutcomeEventsController>(relaxed = true)
        val sessionManager = SessionManager(controller)

        sessionManager.addOutcome("")
        sessionManager.addUniqueOutcome("")
        sessionManager.addOutcomeWithValue("", 1f)
        sessionManager.addOutcome("valid")

        coVerify(timeout = 2_000) { controller.sendOutcomeEvent("valid") }
        coVerify(exactly = 0) { controller.sendOutcomeEvent("") }
        coVerify(exactly = 0) { controller.sendUniqueOutcomeEvent(any()) }
        coVerify(exactly = 0) { controller.sendOutcomeEventWithValue(any(), any()) }
    }
})
