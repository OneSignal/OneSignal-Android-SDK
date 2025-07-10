package com.onesignal.user.internal.customEvents.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.session.internal.outcomes.impl.OutcomeEvent
import org.json.JSONObject

/**
 * The backend service for custom events.
 */
interface ICustomEventBackendService {
    /**
     * Send an custom event to the backend.
     *
     * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
     *
     * @param customEvent The custom event to send up.
     */
    suspend fun sendCustomEvent(
        customEvent: CustomEvent,
    )
}