package com.onesignal.core.internal.http.impl

import android.net.TrafficStats
import android.os.Build
import com.onesignal.common.JSONUtils
import com.onesignal.common.OneSignalUtils
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.UnknownHostException
import java.util.Scanner
import javax.net.ssl.HttpsURLConnection

internal class HttpClient(
    private val _connectionFactory: IHttpConnectionFactory,
    private val _prefs: IPreferencesService,
    private val _configModelStore: ConfigModelStore
) : IHttpClient {
    override suspend fun post(url: String, body: JSONObject): HttpResponse {
        return makeRequest(url, "POST", body, _configModelStore.model.httpTimeout, null)
    }

    override suspend fun get(url: String, cacheKey: String?): HttpResponse {
        return makeRequest(url, null, null, _configModelStore.model.httpGetTimeout, cacheKey)
    }

    override suspend fun put(url: String, body: JSONObject): HttpResponse {
        return makeRequest(url, "PUT", body, _configModelStore.model.httpTimeout, null)
    }

    override suspend fun patch(url: String, body: JSONObject): HttpResponse {
        return makeRequest(url, "PATCH", body, _configModelStore.model.httpTimeout, null)
    }

    override suspend fun delete(url: String): HttpResponse {
        return makeRequest(url, "DELETE", null, _configModelStore.model.httpTimeout, null)
    }

    private suspend fun makeRequest(
        url: String,
        method: String?,
        jsonBody: JSONObject?,
        timeout: Int,
        cacheKey: String?
    ): HttpResponse {
        // If privacy consent is required but not yet given, any non-GET request should be blocked.
        if (method != null && _configModelStore.model.requiresPrivacyConsent == true && _configModelStore.model.givenPrivacyConsent != true) {
            Logging.warn("$method `$url` was called before the user provided privacy consent. Your application is set to require the user's privacy consent before the OneSignal SDK can be initialized. Please ensure the user has provided consent before calling this method. You can check the latest OneSignal consent status by calling OneSignal.privacyConsent")
            return HttpResponse(0, null, null)
        }

        try {
            return withTimeout(getThreadTimeout(timeout).toLong()) {
                return@withTimeout makeRequestIODispatcher(url, method, jsonBody, timeout, cacheKey)
            }
        } catch (e: TimeoutCancellationException) {
            Logging.error("HttpClient: Request timed out: $url", e)
            return HttpResponse(0, null, e)
        } catch (e: Throwable) {
            return HttpResponse(0, null, e)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun makeRequestIODispatcher(
        url: String,
        method: String?,
        jsonBody: JSONObject?,
        timeout: Int,
        cacheKey: String?
    ): HttpResponse {
        var retVal: HttpResponse? = null

        val job = GlobalScope.launch(Dispatchers.IO) {
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
                            conHttps.sslSocketFactory
                        )
                }

                con.useCaches = false
                con.connectTimeout = timeout
                con.readTimeout = timeout
                con.setRequestProperty("SDK-Version", "onesignal/android/" + OneSignalUtils.sdkVersion)
                con.setRequestProperty("Accept", OS_ACCEPT_HEADER)

                val subscriptionId = _configModelStore.model.pushSubscriptionId
                if (subscriptionId != null && subscriptionId.isNotEmpty()) {
                    con.setRequestProperty("OneSignal-Subscription-Id", subscriptionId)
                }

                if (jsonBody != null) {
                    con.doInput = true
                }

                if (method != null) {
                    con.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    con.requestMethod = method
                    con.doOutput = true
                }

                if (jsonBody != null) {
                    val strJsonBody = JSONUtils.toUnescapedEUIDString(jsonBody)
                    Logging.debug("HttpClient: ${method ?: "GET"} $url - $strJsonBody")

                    val sendBytes = strJsonBody.toByteArray(charset("UTF-8"))
                    con.setFixedLengthStreamingMode(sendBytes.size)
                    val outputStream = con.outputStream
                    outputStream.write(sendBytes)
                } else {
                    Logging.debug("HttpClient: ${method ?: "GET"} $url")
                }

                if (cacheKey != null) {
                    val eTag = _prefs.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_ETAG_PREFIX + cacheKey)

                    if (eTag != null) {
                        con.setRequestProperty("if-none-match", eTag)
                        Logging.debug("HttpClient: Adding header if-none-match: $eTag")
                    }
                }

                // Network request is made from getResponseCode()
                httpResponse = con.responseCode

                when (httpResponse) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> {
                        val cachedResponse = _prefs.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_HTTP_CACHE_PREFIX + cacheKey)
                        Logging.debug("HttpClient: ${method ?: "GET"} $url - Using Cached response due to 304: " + cachedResponse)

                        // TODO: SHOULD RETURN OK INSTEAD OF NOT_MODIFIED TO MAKE TRANSPARENT?
                        retVal = HttpResponse(httpResponse, cachedResponse)
                    }
                    HttpURLConnection.HTTP_ACCEPTED, HttpURLConnection.HTTP_CREATED, HttpURLConnection.HTTP_OK -> {
                        val inputStream = con.inputStream
                        val scanner = Scanner(inputStream, "UTF-8")
                        val json = if (scanner.useDelimiter("\\A").hasNext()) scanner.next() else ""
                        scanner.close()
                        Logging.debug("HttpClient: ${method ?: "GET"} $url - STATUS: $httpResponse JSON: " + json)

                        if (cacheKey != null) {
                            val eTag = con.getHeaderField("etag")
                            if (eTag != null) {
                                Logging.debug("HttpClient: Response has etag of $eTag so caching the response.")

                                _prefs.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_ETAG_PREFIX + cacheKey, eTag)
                                _prefs.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_HTTP_CACHE_PREFIX + cacheKey, json)
                            }
                        }

                        retVal = HttpResponse(httpResponse, json)
                    }
                    else -> {
                        Logging.debug("HttpClient: ${method ?: "GET"} $url - FAILED STATUS: $httpResponse")

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
                            Logging.warn("HttpClient: $method RECEIVED JSON: $jsonResponse")
                        } else {
                            Logging.warn("HttpClient: $method HTTP Code: $httpResponse No response body!")
                        }

                        retVal = HttpResponse(httpResponse, jsonResponse)
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

    companion object {
        private const val OS_API_VERSION = "1"
        private const val OS_ACCEPT_HEADER = "application/vnd.onesignal.v$OS_API_VERSION+json"
        private const val THREAD_ID = 10000
    }
}
