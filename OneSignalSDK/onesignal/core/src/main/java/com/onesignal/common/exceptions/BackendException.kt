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
) : Exception()
