package com.onesignal.debug.internal.logging.logger.android

import com.onesignal.logger.ILogHttpSender
import com.onesignal.logger.LogHttpRequest
import com.onesignal.logger.LogHttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android [ILogHttpSender] backed by [HttpURLConnection].
 *
 * The `logger` module deliberately has no networking dependency, so the platform
 * supplies the transport. This keeps the module pure-Kotlin/multiplatform while
 * routing all SDK log traffic through a standard, well-understood HTTP client.
 */
internal class OneSignalLogHttpSender : ILogHttpSender {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val HTTP_OK_MIN = 200
        private const val HTTP_OK_MAX = 299
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun send(request: LogHttpRequest): LogHttpResponse =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", request.contentType)
                    request.headers.forEach { (key, value) -> setRequestProperty(key, value) }
                }

                connection.outputStream.use { it.write(request.body) }

                val code = connection.responseCode
                val success = code in HTTP_OK_MIN..HTTP_OK_MAX
                if (!success) {
                    // Drain the error stream so the connection can be reused/closed cleanly.
                    connection.errorStream?.use { it.readBytes() }
                }
                LogHttpResponse(success = success, statusCode = code)
            } catch (t: Throwable) {
                LogHttpResponse(success = false, statusCode = -1, message = t.message)
            } finally {
                connection?.disconnect()
            }
        }
}
