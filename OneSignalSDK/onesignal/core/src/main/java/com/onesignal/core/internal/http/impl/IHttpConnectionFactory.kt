package com.onesignal.core.internal.http.impl

import java.io.IOException
import java.net.HttpURLConnection

internal interface IHttpConnectionFactory {
    @Throws(IOException::class)
    fun newHttpURLConnection(url: String): HttpURLConnection
}
