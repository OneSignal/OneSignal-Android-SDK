package com.onesignal.core.internal.http.impl

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class HttpConnectionFactory : IHttpConnectionFactory {
    @Throws(IOException::class)
    override fun newHttpURLConnection(url: String): HttpURLConnection {
        return URL(BASE_URL + url).openConnection() as HttpURLConnection
    }

    companion object {
        private const val BASE_URL = "https://api.onesignal.com/"
    }
}
