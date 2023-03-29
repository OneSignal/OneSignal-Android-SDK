package com.onesignal.common

object NetworkUtils {
    enum class ResponseStatusType {
        INVALID,
        RETRYABLE,
        UNAUTHORIZED,
        MISSING,
        CONFLICT,
    }

    var MAX_NETWORK_REQUEST_ATTEMPT_COUNT = 3

    fun getResponseStatusType(statusCode: Int): ResponseStatusType {
        return when (statusCode) {
            400, 402 -> ResponseStatusType.INVALID
            401, 403 -> ResponseStatusType.UNAUTHORIZED
            404, 410 -> ResponseStatusType.MISSING
            409 -> ResponseStatusType.CONFLICT
            else -> ResponseStatusType.RETRYABLE
        }
    }
}
