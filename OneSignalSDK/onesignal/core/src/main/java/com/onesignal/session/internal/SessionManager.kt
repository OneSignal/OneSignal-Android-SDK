package com.onesignal.session.internal

import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.ISessionManager
import com.onesignal.session.internal.outcomes.IOutcomeEventsController

internal open class SessionManager(
    private val _outcomeController: IOutcomeEventsController,
) : ISessionManager {
    override fun addOutcome(name: String) {
        Logging.log(LogLevel.DEBUG, "sendOutcome(name: $name)")

        suspendifyOnThread {
            _outcomeController.sendOutcomeEvent(name)
        }
    }

    override fun addUniqueOutcome(name: String) {
        Logging.log(LogLevel.DEBUG, "sendUniqueOutcome(name: $name)")

        suspendifyOnThread {
            _outcomeController.sendUniqueOutcomeEvent(name)
        }
    }

    override fun addOutcomeWithValue(
        name: String,
        value: Float,
    ) {
        Logging.log(LogLevel.DEBUG, "sendOutcomeWithValue(name: $name, value: $value)")

        suspendifyOnThread {
            _outcomeController.sendOutcomeEventWithValue(name, value)
        }
    }
}
