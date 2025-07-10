package com.onesignal.user.internal.customEvents.impl

import com.onesignal.core.internal.operations.ExecutionResponse

/**
 * The backend service for custom events.
 */
interface ICustomEventBackendService {
    /**
     * Send an custom event to the backend and return the response.
     *
     * @param customEvent The custom event to send up.
     */
    suspend fun sendCustomEvent(customEvent: CustomEvent): ExecutionResponse
}
