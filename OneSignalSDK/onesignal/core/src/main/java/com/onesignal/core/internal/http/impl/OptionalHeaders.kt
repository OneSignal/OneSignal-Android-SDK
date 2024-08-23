package com.onesignal.core.internal.http.impl

data class OptionalHeaders(
    /**
     * Used as an E-Tag
     */
    val cacheKey: String? = null,
    /**
     * Used for read your write consistency
     */
    var offset: Long? = null,
    /**
     * Current retry count
     */
    var retryCount: Int? = null,
    /**
     * Used to track delay between session start and request
     */
    var secondsSinceAppOpen: Long? = null,
)
