package com.onesignal.core.internal.http

import org.json.JSONObject

/**
 * Provides CRUD operations to a backend service.  All methods are implemented as
 * coroutines and will automatically switch over to a IO thread for its processing.
 */
interface IHttpClient {
    /**
     * Make a POST request to the provided [url] with the provided [body].
     *
     * @param url The URL to request.
     * @param body The payload to send up with the request.
     *
     * @return The response returned.
     */
    suspend fun post(
        url: String,
        body: JSONObject,
        jwt: String? = null,
        deviceAuthPushToken: String? = null,
    ): HttpResponse

    /**
     * Make a GET request to the provided [url].
     *
     * @param url The URL to request.
     * @param cacheKey The optional cache key. If provided, the `etag` response header
     * will be cached locally along with the response payload.  On subsequent requests
     * with the same [cacheKey], the `if-none-match` header will be specified, and the
     * locally cached data used if the response hasn't changed.
     *
     * @return The response returned.
     */
    suspend fun get(
        url: String,
        cacheKey: String? = null,
        jwt: String? = null,
        deviceAuthPushToken: String? = null,
    ): HttpResponse

    /**
     * Make a PUT request to the provided [url] with the provided [body].
     *
     * @param url The URL to request.
     * @param body The payload to send up with the request.
     *
     * @return The response returned.
     */
    suspend fun put(
        url: String,
        body: JSONObject,
        jwt: String? = null,
        deviceAuthPushToken: String? = null,
    ): HttpResponse

    /**
     * Make a PATCH request to the provided [url] with the provided [body].
     *
     * @param url The URL to request.
     * @param body The payload to send up with the request.
     *
     * @return The response returned.
     */
    suspend fun patch(
        url: String,
        body: JSONObject,
        jwt: String? = null,
        deviceAuthPushToken: String? = null,
    ): HttpResponse

    /**
     * Make a DELETE request to the provided [url].
     *
     * @param url The URL to request.
     *
     * @return The response returned.
     */
    suspend fun delete(
        url: String,
        jwt: String? = null,
        deviceAuthPushToken: String? = null,
    ): HttpResponse
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
