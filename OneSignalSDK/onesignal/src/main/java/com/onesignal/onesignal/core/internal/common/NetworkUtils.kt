package com.onesignal.onesignal.core.internal.common

object NetworkUtils {
    var MAX_NETWORK_REQUEST_ATTEMPT_COUNT = 3
    val NO_RETRY_NETWROK_REQUEST_STATUS_CODES = intArrayOf(401, 402, 403, 404, 410)

    fun shouldRetryNetworkRequest(statusCode: Int): Boolean {
        for (code in NO_RETRY_NETWROK_REQUEST_STATUS_CODES)
            if (statusCode == code) return false
        return true
    }
}
