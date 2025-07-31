package com.onesignal.user.internal.customEvents

import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.user.internal.customEvents.impl.CustomEventMetadata

/**
 * The backend service for custom events.
 */
interface ICustomEventBackendService {
    /**
     * Send an custom event to the backend and return the response.
     *
     * @param customEvent The custom event to send up.
     */
    suspend fun sendCustomEvent(
        appId: String,
        onesignalId: String,
        externalId: String?,
        timestamp: Long,
        eventName: String,
        eventProperties: String?,
        metadata: CustomEventMetadata,
    ): ExecutionResponse
}
