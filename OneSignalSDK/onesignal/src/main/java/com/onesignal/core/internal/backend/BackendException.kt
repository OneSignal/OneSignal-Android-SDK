package com.onesignal.core.internal.backend

internal class BackendException(
    val statusCode: Int,
    val response: String?
) : Exception()
