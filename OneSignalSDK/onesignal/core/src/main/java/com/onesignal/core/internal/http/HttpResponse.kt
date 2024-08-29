package com.onesignal.core.internal.http

import java.net.HttpURLConnection

/**
 * The response returned by a method in [IHttpClient].
 */
class HttpResponse(
    /**
     * The status code of the response ([HttpURLConnection.HTTP_OK] as an example).
     */
    val statusCode: Int,
    /**
     * The optional response payload.
     */
    val payload: String?,
    /**
     * When non-null, the throwable that was thrown during processing.
     */
    val throwable: Throwable? = null,
    /**
     * Optional Integer value maybe returned from the backend.
     * The module handing this should delay any future requests by this time.
     */
    val retryAfterSeconds: Int? = null,
    /**
     * Optional Integer value may be returned from the backend.
     * The module handling this should not retry more than this number.
     */
    val retryLimit: Int? = null,
) {
    /**
     * Whether the response is a successful one.
     */
    val isSuccess: Boolean
        get() = statusCode == HttpURLConnection.HTTP_OK || statusCode == HttpURLConnection.HTTP_ACCEPTED || statusCode == HttpURLConnection.HTTP_NOT_MODIFIED || statusCode == HttpURLConnection.HTTP_CREATED
}
