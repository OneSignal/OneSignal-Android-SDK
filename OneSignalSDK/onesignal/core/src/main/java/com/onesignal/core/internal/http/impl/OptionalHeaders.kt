package com.onesignal.core.internal.http.impl

data class OptionalHeaders(
    /**
     * Used as an E-Tag
     */
    val cacheKey: String? = null,
    /**
     * Used for read your write consistency
     */
    val rywToken: String? = null,
    /**
     * Current retry count
     */
    val retryCount: Int? = null,
    /**
     * Used to track delay between session start and request
     */
    val sessionDuration: Long? = null,
    /**
     * JWT bearer token for identity verification. When non-null, sent as
     * `Authorization: Bearer <jwt>` on the request.
     */
    val jwt: String? = null,
)
