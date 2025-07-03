package com.onesignal.user.internal.customEvents

import com.onesignal.session.internal.outcomes.IOutcomeEvent
import com.onesignal.user.internal.customEvents.impl.CustomEventProperty

/**
 * The gateway to user custom event logic.
 */
interface ICustomEventController {
    /**
     * Send an custom event to the backend with optional properties.
     */
    suspend fun sendCustomEvent(
        name: String,
        properties: Map<String, CustomEventProperty>?,
    )
}