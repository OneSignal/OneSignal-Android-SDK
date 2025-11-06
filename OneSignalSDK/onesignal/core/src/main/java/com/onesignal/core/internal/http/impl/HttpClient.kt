package com.onesignal.core.internal.http.impl

import android.net.TrafficStats
import android.os.Build
import com.onesignal.common.JSONUtils
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.OneSignalWrapper
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IInstallIdService
import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.util.Scanner
import javax.net.ssl.HttpsURLConnection

internal const val HTTP_SDK_VERSION_HEADER_KEY = "SDK-Version"
internal val HTTP_SDK_VERSION_HEADER_VALUE = "onesignal/android/${OneSignalUtils.sdkVersion}"

internal class HttpClient(
    private val _connectionFactory: IHttpConnectionFactory,
    private val _prefs: IPreferencesService,
    private val _configModelStore: ConfigModelStore,
    private val _time: ITime,
    private val _installIdService: IInstallIdService,
) : IHttpClient {
    /**
     * Delay making network requests until we reach this time.
     * Used when the OneSignal backend returns a Retry-After value.
     */
    private var delayNewRequestsUntil = 0L

    override suspend fun post(
        url: String,
        body: JSONObject,
        headers: OptionalHeaders?,
    ): HttpResponse = makeRequest(url, "POST", body, _configModelStore.model.httpTimeout, headers)

    override suspend fun get(
        url: String,
        headers: OptionalHeaders?,
    ): HttpResponse = makeRequest(url, null, null, _configModelStore.model.httpGetTimeout, headers)

    override suspend fun put(
        url: String,
        body: JSONObject,
        headers: OptionalHeaders?,
    ): HttpResponse = makeRequest(url, "PUT", body, _configModelStore.model.httpTimeout, headers)

    override suspend fun patch(
        url: String,
        body: JSONObject,
        headers: OptionalHeaders?,
    ): HttpResponse = makeRequest(url, "PATCH", body, _configModelStore.model.httpTimeout, headers)

    override suspend fun delete(
        url: String,
        headers: OptionalHeaders?,
    ): HttpResponse = makeRequest(url, "DELETE", null, _configModelStore.model.httpTimeout, headers)

    private suspend fun makeRequest(
        url: String,
        method: String?,
        jsonBody: JSONObject?,
        timeout: Int,
        headers: OptionalHeaders?,
    ): HttpResponse {
        // If privacy consent is required but not yet given, any non-GET request should be blocked.
        if (method != null && _configModelStore.model.consentRequired == true && _configModelStore.model.consentGiven != true) {
            Logging.warn(
                "$method `$url` was called before the user provided privacy consent. Your application is set to require the user's privacy consent before the OneSignal SDK can be initialized. Please ensure the user has provided consent before calling this method. You can check the latest OneSignal consent status by calling OneSignal.privacyConsent",
            )
            return HttpResponse(0, null, null)
        }

        val delayUntil = delayNewRequestsUntil - _time.currentTimeMillis
        if (delayUntil > 0) delay(delayUntil)

        try {
            return withTimeout(getThreadTimeout(timeout).toLong()) {
                return@withTimeout makeRequestIODispatcher(url, method, jsonBody, timeout, headers)
            }
        } catch (e: TimeoutCancellationException) {
            Logging.error("HttpClient: Request timed out: $url", e)
            return HttpResponse(0, null, e)
        } catch (e: Throwable) {
            return HttpResponse(0, null, e)
        }
    }

    private suspend fun makeRequestIODispatcher(
        url: String,
        method: String?,
        jsonBody: JSONObject?,
        timeout: Int,
        headers: OptionalHeaders?,
    ): HttpResponse {
        var retVal: HttpResponse? = null

        val job =
            OneSignalDispatchers.launchOnIO {
                var httpResponse = -1
                var con: HttpURLConnection? = null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    TrafficStats.setThreadStatsTag(THREAD_ID)
                }

                try {
                    con = _connectionFactory.newHttpURLConnection(url)

                    // https://github.com/OneSignal/OneSignal-Android-SDK/issues/1465
                    // Android 4.4 and older devices fail to register to onesignal.com to due it's TLS1.2+ requirement
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1 && con is HttpsURLConnection) {
                        val conHttps = con
                        conHttps.sslSocketFactory =
                            TLS12SocketFactory(
                                conHttps.sslSocketFactory,
                            )
                    }

                    con.useCaches = false
                    con.connectTimeout = timeout
                    con.readTimeout = timeout
                    con.setRequestProperty(HTTP_SDK_VERSION_HEADER_KEY, HTTP_SDK_VERSION_HEADER_VALUE)

                    if (OneSignalWrapper.sdkType != null && OneSignalWrapper.sdkVersion != null) {
                        con.setRequestProperty("SDK-Wrapper", "onesignal/${OneSignalWrapper.sdkType}/${OneSignalWrapper.sdkVersion}")
                    }

                    con.setRequestProperty("Accept", OS_ACCEPT_HEADER)

                    val subscriptionId = _configModelStore.model.pushSubscriptionId
                    if (subscriptionId != null && subscriptionId.isNotEmpty()) {
                        con.setRequestProperty("OneSignal-Subscription-Id", subscriptionId)
                    }

                    con.setRequestProperty("OneSignal-Install-Id", _installIdService.getId().toString())

                    if (jsonBody != null) {
                        con.doInput = true
                    }

                    if (method != null) {
                        con.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        con.requestMethod = method
                        con.doOutput = true
                    }

                    logHTTPSent(con.requestMethod, con.url, jsonBody, con.requestProperties)

                    if (jsonBody != null) {
                        val strJsonBody = JSONUtils.toUnescapedEUIDString(jsonBody)
                        val sendBytes = strJsonBody.toByteArray(charset("UTF-8"))
                        con.setFixedLengthStreamingMode(sendBytes.size)
                        val outputStream = con.outputStream
                        outputStream.write(sendBytes)
                    }

                    // H E A D E R S

                    if (headers?.cacheKey != null) {
                        val eTag =
                            _prefs.getString(
                                PreferenceStores.ONESIGNAL,
                                PreferenceOneSignalKeys.PREFS_OS_ETAG_PREFIX + headers.cacheKey,
                            )
                        if (eTag != null) {
                            con.setRequestProperty("If-None-Match", eTag)
                            Logging.debug("HttpClient: Adding header if-none-match: $eTag")
                        }
                    }

                    if (headers?.rywToken != null) {
                        con.setRequestProperty("OneSignal-RYW-Token", headers.rywToken.toString())
                    }

                    if (headers?.retryCount != null) {
                        con.setRequestProperty("Onesignal-Retry-Count", headers.retryCount.toString())
                    }

                    if (headers?.sessionDuration != null) {
                        con.setRequestProperty("OneSignal-Session-Duration", headers.sessionDuration.toString())
                    }

                    // Network request is made from getResponseCode()
                    httpResponse = con.responseCode

                    val retryAfter = retryAfterFromResponse(con)
                    val retryLimit = retryLimitFromResponse(con)
                    val newDelayUntil = _time.currentTimeMillis + (retryAfter ?: 0) * 1_000
                    if (newDelayUntil > delayNewRequestsUntil) delayNewRequestsUntil = newDelayUntil

                    when (httpResponse) {
                        HttpURLConnection.HTTP_NOT_MODIFIED -> {
                            val cachedResponse =
                                _prefs.getString(
                                    PreferenceStores.ONESIGNAL,
                                    PreferenceOneSignalKeys.PREFS_OS_HTTP_CACHE_PREFIX + headers?.cacheKey,
                                )
                            Logging.debug(
                                "HttpClient: Got Response = ${method ?: "GET"} ${con.url} - Using Cached response due to 304: " +
                                    cachedResponse,
                            )

                            // TODO: SHOULD RETURN OK INSTEAD OF NOT_MODIFIED TO MAKE TRANSPARENT?
                            retVal = HttpResponse(httpResponse, cachedResponse, retryAfterSeconds = retryAfter, retryLimit = retryLimit)
                        }
                        HttpURLConnection.HTTP_ACCEPTED, HttpURLConnection.HTTP_CREATED, HttpURLConnection.HTTP_OK -> {
                            val inputStream = con.inputStream
                            val scanner = Scanner(inputStream, "UTF-8")
                            val json = if (scanner.useDelimiter("\\A").hasNext()) scanner.next() else ""
                            scanner.close()
                            Logging.debug(
                                "HttpClient: Got Response = ${method ?: "GET"} ${con.url} - STATUS: $httpResponse - Body: " + json,
                            )

                            if (headers?.cacheKey != null) {
                                val eTag = con.getHeaderField("etag")
                                if (eTag != null) {
                                    Logging.debug("HttpClient: Got Response = Response has etag of $eTag so caching the response.")

                                    _prefs.saveString(
                                        PreferenceStores.ONESIGNAL,
                                        PreferenceOneSignalKeys.PREFS_OS_ETAG_PREFIX + headers.cacheKey,
                                        eTag,
                                    )
                                    _prefs.saveString(
                                        PreferenceStores.ONESIGNAL,
                                        PreferenceOneSignalKeys.PREFS_OS_HTTP_CACHE_PREFIX + headers.cacheKey,
                                        json,
                                    )
                                }
                            }

                            retVal = HttpResponse(httpResponse, json, retryAfterSeconds = retryAfter, retryLimit = retryLimit)
                        }
                        else -> {
                            Logging.debug("HttpClient: Got Response = ${method ?: "GET"} ${con.url} - FAILED STATUS: $httpResponse")

                            var inputStream = con.errorStream
                            if (inputStream == null) {
                                inputStream = con.inputStream
                            }

                            var jsonResponse: String? = null
                            if (inputStream != null) {
                                val scanner = Scanner(inputStream, "UTF-8")
                                jsonResponse =
                                    if (scanner.useDelimiter("\\A").hasNext()) scanner.next() else ""
                                scanner.close()
                                Logging.warn("HttpClient: Got Response = $method - STATUS: $httpResponse - Body: $jsonResponse")
                            } else {
                                Logging.warn("HttpClient: Got Response = $method - STATUS: $httpResponse - No response body!")
                            }

                            retVal = HttpResponse(httpResponse, jsonResponse, retryAfterSeconds = retryAfter, retryLimit = retryLimit)
                        }
                    }
                } catch (t: Throwable) {
                    if (t is ConnectException || t is UnknownHostException) {
                        Logging.info("HttpClient: Could not send last request, device is offline. Throwable: " + t.javaClass.name)
                    } else {
                        Logging.warn("HttpClient: $method Error thrown from network stack. ", t)
                    }

                    retVal = HttpResponse(httpResponse, null, t)
                } finally {
                    con?.disconnect()
                }
            }

        job.join()
        return retVal!!
    }

    private fun getThreadTimeout(timeout: Int): Int {
        return timeout + 5000
    }

    /**
     * Reads the HTTP Retry-After from the response.
     * Only supports number format, not the date format.
     */
    private fun retryAfterFromResponse(con: HttpURLConnection): Int? {
        val retryAfterStr = con.getHeaderField("Retry-After")
        return if (retryAfterStr != null) {
            Logging.debug("HttpClient: Response Retry-After: $retryAfterStr")
            retryAfterStr.toIntOrNull() ?: _configModelStore.model.httpRetryAfterParseFailFallback
        } else if (con.responseCode == 429) {
            _configModelStore.model.httpRetryAfterParseFailFallback
        } else {
            null
        }
    }

    /**
     * Reads the HTTP Retry-Limit from the response.
     */
    private fun retryLimitFromResponse(con: HttpURLConnection): Int? {
        val retryLimitStr = con.getHeaderField("OneSignal-Retry-Limit")
        return if (retryLimitStr != null) {
            Logging.debug("HttpClient: Response OneSignal-Retry-Limit: $retryLimitStr")
            retryLimitStr.toIntOrNull()
        } else {
            null
        }
    }

    private fun logHTTPSent(
        method: String?,
        url: URL,
        jsonBody: JSONObject?,
        headers: Map<String, List<String>>,
    ) {
        val headersStr = headers.entries.joinToString()
        val methodStr = method ?: "GET"
        val bodyStr = if (jsonBody != null) JSONUtils.toUnescapedEUIDString(jsonBody) else null
        Logging.debug("HttpClient: Request Sent = $methodStr $url - Body: $bodyStr - Headers: $headersStr")
    }

    companion object {
        private const val OS_API_VERSION = "1"
        private const val OS_ACCEPT_HEADER = "application/vnd.onesignal.v$OS_API_VERSION+json"
        private const val THREAD_ID = 10000
    }
}
