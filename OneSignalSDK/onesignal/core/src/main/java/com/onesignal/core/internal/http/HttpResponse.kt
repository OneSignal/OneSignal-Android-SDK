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
) {
    /**
     * Whether the response is a successful one.
     */
    val isSuccess: Boolean
        get() = statusCode == HttpURLConnection.HTTP_OK || statusCode == HttpURLConnection.HTTP_ACCEPTED || statusCode == HttpURLConnection.HTTP_NOT_MODIFIED || statusCode == HttpURLConnection.HTTP_CREATED
}
