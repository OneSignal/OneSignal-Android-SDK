package com.onesignal.core.internal.session

import com.onesignal.core.debug.LogLevel
import com.onesignal.core.internal.common.suspendifyOnThread
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.outcomes.IOutcomeEventsController
import com.onesignal.core.session.ISessionManager

internal open class SessionManager(
    private val _outcomeController: IOutcomeEventsController
) : ISessionManager {

    override fun sendOutcome(name: String): ISessionManager {
        Logging.log(LogLevel.DEBUG, "sendOutcome(name: $name)")

        suspendifyOnThread {
            _outcomeController.sendOutcomeEvent(name)
        }

        return this
    }

    override fun sendUniqueOutcome(name: String): ISessionManager {
        Logging.log(LogLevel.DEBUG, "sendUniqueOutcome(name: $name)")

        suspendifyOnThread {
            _outcomeController.sendUniqueOutcomeEvent(name)
        }

        return this
    }

    override fun sendOutcomeWithValue(name: String, value: Float): ISessionManager {
        Logging.log(LogLevel.DEBUG, "sendOutcomeWithValue(name: $name, value: $value)")

        suspendifyOnThread {
            _outcomeController.sendOutcomeEventWithValue(name, value)
        }

        return this
    }
}
