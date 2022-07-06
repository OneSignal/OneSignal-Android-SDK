package com.onesignal.onesignal.internal.backend.api

class ApiException(msg: String,
           val statusCode: Int,
           val response: String?,
                   ) : Exception(msg) {
}
