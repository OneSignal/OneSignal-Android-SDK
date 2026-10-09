package com.onesignal.session.internal.session.backend

import com.onesignal.common.exceptions.BackendException

/**
 * The backend service for the public sessions API.
 *
 * If there is a non-successful response from the backend, a [BackendException] will be thrown with response data.
 */
internal interface ISessionsBackendService {
    /**
     * Create a session.
     *
     * @return The backend session ID, or null if the success response did not include one.
     */
    suspend fun createSession(
        appId: String,
        request: CreateSessionRequest,
    ): String?

    /**
     * Update an existing session with its cumulative duration, and end it when [UpdateSessionRequest.endTime] is set.
     */
    suspend fun updateSession(
        appId: String,
        sessionId: String,
        request: UpdateSessionRequest,
    )
}

internal data class CreateSessionRequest(
    val onesignalId: String,
    val subscriptionId: String,
    val deviceType: Int,
    /** Unix time in seconds, sent as ISO 8601 UTC. */
    val startTime: Long,
    val idempotencyKey: String,
    val directAttributionId: String? = null,
)

internal data class UpdateSessionRequest(
    val onesignalId: String,
    val subscriptionId: String,
    val durationSeconds: Long,
    val idempotencyKey: String,
    /** Unix time in seconds, sent as ISO 8601 UTC. */
    val endTime: Long? = null,
)
