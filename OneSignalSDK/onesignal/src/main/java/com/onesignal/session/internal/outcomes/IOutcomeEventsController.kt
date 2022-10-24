package com.onesignal.session.internal.outcomes

/**
 * The gateway to outcomes logic.
 */
interface IOutcomeEventsController {
    /**
     * Send a unique outcome event to the backend.
     */
    suspend fun sendUniqueOutcomeEvent(name: String): IOutcomeEvent?

    /**
     * Send an outcome event to the backend.
     */
    suspend fun sendOutcomeEvent(name: String): IOutcomeEvent?

    /**
     * Send an outcome event with value to the backend.
     */
    suspend fun sendOutcomeEventWithValue(name: String, weight: Float): IOutcomeEvent?
}
