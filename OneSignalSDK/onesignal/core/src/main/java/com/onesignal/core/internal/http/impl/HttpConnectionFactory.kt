package com.onesignal.core.internal.http.impl

import com.onesignal.core.internal.config.ConfigModelStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class HttpConnectionFactory(
    private val _configModelStore: ConfigModelStore,
) : IHttpConnectionFactory {
    @Throws(IOException::class)
    override fun newHttpURLConnection(url: String): HttpURLConnection {
        return URL("https://staging.onesignal.com/api/v1/" + url).openConnection() as HttpURLConnection
    }
}
