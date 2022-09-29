package com.onesignal.core.internal.outcomes.impl

import com.onesignal.core.internal.http.HttpResponse

/**
 * The backend service for outcomes.
 */
internal interface IOutcomeEventsBackend {

    /**
     * Send an outcome event to the backend.
     *
     * @param appId The ID of the application this outcome event occurred under.
     * @param deviceType The device type.
     * @param direct Whether this outcome event is direct. `true` if it is, `false` if it isn't, `null` if should not be specified.
     * @param event The outcome event to send up.
     */
    suspend fun sendOutcomeEvent(appId: String, deviceType: Int, direct: Boolean?, event: OutcomeEvent): HttpResponse
}
