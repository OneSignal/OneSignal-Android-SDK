package com.onesignal.onesignal.core.internal.backend.http

import java.net.HttpURLConnection

class HttpResponse(
    /**
     * The status code of the response ([HttpURLConnection.HTTP_OK] as an example)
     */
    val statusCode: Int,

    /**
     * The optional response payload.
     */
    val payload: String?,

    /**
     * When non-null, the throwable that was thrown during processing.
     */
    val throwable: Throwable? = null
) {

    val isSuccess: Boolean
        get() = statusCode == HttpURLConnection.HTTP_OK || statusCode == HttpURLConnection.HTTP_ACCEPTED || statusCode == HttpURLConnection.HTTP_NOT_MODIFIED
}
