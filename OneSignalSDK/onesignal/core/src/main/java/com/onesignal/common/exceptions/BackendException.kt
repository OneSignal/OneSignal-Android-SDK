package com.onesignal.common.exceptions

/**
 * Raised when a backend service request has failed.
 */
class BackendException(
    /**
     * The status code of the response.
     */
    val statusCode: Int,
    /**
     * The response, if one exists.
     */
    val response: String? = null,
    /**
     * Optional Integer value maybe returned from the backend.
     * Any future requests by this time.
     */
    val retryAfterSeconds: Int? = null,
) : Exception()
