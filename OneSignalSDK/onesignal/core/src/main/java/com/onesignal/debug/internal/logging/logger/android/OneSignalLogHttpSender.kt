package com.onesignal.debug.internal.logging.logger.android

import com.onesignal.logger.ILogHttpSender
import com.onesignal.logger.ILogger
import com.onesignal.logger.LogHttpRequest
import com.onesignal.logger.LogHttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android [ILogHttpSender] backed by [HttpURLConnection]. Request and response diagnostics go
 * through [logger] only when [isDiagnosticsEnabled], so production stays quiet.
 */
internal class OneSignalLogHttpSender(
    private val logger: ILogger,
    private val isDiagnosticsEnabled: () -> Boolean = { false },
) : ILogHttpSender {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val HTTP_OK_MIN = 200
        private const val HTTP_OK_MAX = 299
        private const val MAX_LOGGED_BODY_CHARS = 500
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
                    // Always drain the error stream so the connection can be reused/closed cleanly;
                    // only surface it when diagnostics are enabled.
                    val errorBody = connection.errorStream?.use { String(it.readBytes()) }
                    if (isDiagnosticsEnabled()) {
                        logger.warn(
                            "OneSignalLogHttpSender: POST ${request.url} -> $code " +
                                "(ct=${request.contentType}, ${request.body.size}B) " +
                                "body=${errorBody?.take(MAX_LOGGED_BODY_CHARS)}",
                        )
                    }
                } else if (isDiagnosticsEnabled()) {
                    logger.debug("OneSignalLogHttpSender: POST ${request.url} -> $code OK (${request.body.size}B)")
                }
                LogHttpResponse(success = success, statusCode = code)
            } catch (t: Throwable) {
                if (isDiagnosticsEnabled()) {
                    logger.warn("OneSignalLogHttpSender: POST ${request.url} failed: ${t::class.simpleName}: ${t.message}")
                }
                LogHttpResponse(success = false, statusCode = -1, message = t.message)
            } finally {
                connection?.disconnect()
            }
        }
}
