package com.onesignal.core.internal.backend.http

import org.json.JSONObject

internal interface IHttpClient {
    suspend fun post(url: String, body: JSONObject): HttpResponse
    suspend fun get(url: String, cacheKey: String? = null): HttpResponse
    suspend fun put(url: String, body: JSONObject): HttpResponse
    suspend fun patch(url: String, body: JSONObject): HttpResponse
    suspend fun delete(url: String): HttpResponse
}

/**
 * The cache keys that can be provided on [IHttpClient.get]
 */
internal object CacheKeys {
    /**
     * Cache key for retrieving tags
     */
    const val GET_TAGS = "CACHE_KEY_GET_TAGS"

    /**
     * Cache key for retrieving remote params.
     */
    const val REMOTE_PARAMS = "CACHE_KEY_REMOTE_PARAMS"
}
