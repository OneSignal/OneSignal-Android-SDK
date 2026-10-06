package com.onesignal.session.internal.session.backend

/**
 * The backend service for the public sessions API.
 */
internal interface ISessionsBackendService {
    /**
     * Create a session. On success the result holds the backend session ID.
     */
    suspend fun createSession(
        appId: String,
        request: CreateSessionRequest,
    ): SessionsApiResult<String>

    /**
     * Update an existing session with its cumulative duration, and end it when [UpdateSessionRequest.endTime] is set.
     */
    suspend fun updateSession(
        appId: String,
        sessionId: String,
        request: UpdateSessionRequest,
    ): SessionsApiResult<Unit>
}

internal data class CreateSessionRequest(
    val onesignalId: String,
    val subscriptionId: String,
    val deviceType: Int,
    /** Unix time in seconds. */
    val startTime: Long,
    val idempotencyKey: String,
    val directAttributionId: String? = null,
)

internal data class UpdateSessionRequest(
    val onesignalId: String,
    val subscriptionId: String,
    val durationSeconds: Long,
    val idempotencyKey: String,
    /** Unix time in seconds. */
    val endTime: Long? = null,
)

internal sealed class SessionsApiResult<out T> {
    data class Success<T>(val value: T) : SessionsApiResult<T>()

    /**
     * Transient failure: network error, 5xx, 408, 429, or a create success without a session ID.
     * Wait at least [retryAfterSeconds] when set.
     */
    data class Retry(
        val statusCode: Int,
        val retryAfterSeconds: Int?,
    ) : SessionsApiResult<Nothing>()

    /**
     * Permanent failure (any other 4xx); the request should not be retried.
     */
    data class Drop(
        val statusCode: Int,
    ) : SessionsApiResult<Nothing>()
}
