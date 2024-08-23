package com.onesignal.core.internal.http.impl

data class OptionalHeaderValues(
    var offset: Long? = null,
    var retryCount: Int? = null,
    var secondsSinceAppOpen: Long? = null,
    var cacheKey: String? = null,
)
