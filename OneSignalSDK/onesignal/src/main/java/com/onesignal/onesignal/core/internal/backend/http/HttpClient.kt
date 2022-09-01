package com.onesignal.onesignal.core.internal.backend.http

import android.net.TrafficStats
import android.os.Build
import com.onesignal.onesignal.core.internal.common.JSONUtils
import com.onesignal.onesignal.core.internal.common.OneSignalUtils
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.onesignal.core.internal.preferences.PreferenceStores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.util.Scanner
import javax.net.ssl.HttpsURLConnection

class HttpClient(
    private val _prefs: IPreferencesService
) : IHttpClient {
    override suspend fun post(url: String, body: JSONObject): HttpResponse {
        return makeRequest(url, "POST", body, TIMEOUT, null)
    }

    override suspend fun get(url: String, cacheKey: String?): HttpResponse {
        return makeRequest(url, null, null, GET_TIMEOUT, cacheKey)
    }

    override suspend fun put(url: String, body: JSONObject): HttpResponse {
        return makeRequest(url, "PUT", body, TIMEOUT, null)
    }

    override suspend fun patch(url: String, body: JSONObject): HttpResponse {
        return makeRequest(url, "PATCH", body, TIMEOUT, null)
    }

    override suspend fun delete(url: String): HttpResponse {
        return makeRequest(url, "DELETE", null, TIMEOUT, null)
    }

    private suspend fun makeRequest(
        url: String,
        method: String?,
        jsonBody: JSONObject?,
        timeout: Int,
        cacheKey: String?
    ): HttpResponse {
        // TODO: Implement If not a GET request, check if the user provided privacy consent if the application is set to require user privacy consent
//        if (method != null && OneSignal.shouldLogUserPrivacyConsentErrorMessageForMethodName(null))
//            return

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

    private suspend fun makeRequestIODispatcher(
        url: String,
        method: String?,
        jsonBody: JSONObject?,
        timeout: Int,
        cacheKey: String?
    ): HttpResponse {
        val result = withContext(Dispatchers.IO) {
            var httpResponse = -1
            var con: HttpURLConnection? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                TrafficStats.setThreadStatsTag(THREAD_ID)
            }

            try {
                Logging.debug("HttpClient: Making request to: $BASE_URL$url")
                con = newHttpURLConnection(url)

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

                if (jsonBody != null)
                    con.doInput = true

                if (method != null) {
                    con.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    con.requestMethod = method
                    con.doOutput = true
                }

                if (jsonBody != null) {
                    val strJsonBody = JSONUtils.toUnescapedEUIDString(jsonBody)
                    Logging.debug("HttpClient: $method SEND JSON: $strJsonBody")

                    val sendBytes = strJsonBody.toByteArray(charset("UTF-8"))
                    con.setFixedLengthStreamingMode(sendBytes.size)
                    val outputStream = con.outputStream
                    outputStream.write(sendBytes)
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
                Logging.verbose("HttpClient: After con.getResponseCode to: " + BASE_URL + url)

                when (httpResponse) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> {
                        val cachedResponse = _prefs.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_HTTP_CACHE_PREFIX + cacheKey,)
                        Logging.debug("HttpClient: " + (method ?: "GET") + " - Using Cached response due to 304: " + cachedResponse)

                        // TODO: SHOULD RETURN OK INSTEAD OF NOT_MODIFIED TO MAKE TRANSPARENT?
                        return@withContext HttpResponse(httpResponse, cachedResponse)
                    }
                    HttpURLConnection.HTTP_ACCEPTED, HttpURLConnection.HTTP_OK -> {
                        Logging.debug("HttpClient: Successfully finished request to: $BASE_URL$url")

                        val inputStream = con.inputStream
                        val scanner = Scanner(inputStream, "UTF-8")
                        val json = if (scanner.useDelimiter("\\A").hasNext()) scanner.next() else ""
                        scanner.close()
                        Logging.debug("HttpClient: " + (method ?: "GET") + " RECEIVED JSON: " + json)

                        if (cacheKey != null) {
                            val eTag = con.getHeaderField("etag")
                            if (eTag != null) {
                                Logging.debug("HttpClient: Response has etag of $eTag so caching the response.")

                                _prefs.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_ETAG_PREFIX + cacheKey, eTag)
                                _prefs.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_OS_HTTP_CACHE_PREFIX + cacheKey, json)
                            }
                        }

                        return@withContext HttpResponse(httpResponse, json)
                    }
                    else -> {
                        Logging.debug("HttpClient: Failed request to: $BASE_URL$url")

                        var inputStream = con.errorStream
                        if (inputStream == null)
                            inputStream = con.inputStream

                        var jsonResponse: String? = null
                        if (inputStream != null) {
                            val scanner = Scanner(inputStream, "UTF-8")
                            jsonResponse =
                                if (scanner.useDelimiter("\\A").hasNext()) scanner.next() else ""
                            scanner.close()
                            Logging.warn("HttpClient: $method RECEIVED JSON: $jsonResponse")
                        } else
                            Logging.warn("HttpClient: $method HTTP Code: $httpResponse No response body!")

                        return@withContext HttpResponse(httpResponse, jsonResponse)
                    }
                }
            } catch (t: Throwable) {
                if (t is ConnectException || t is UnknownHostException)
                    Logging.info("HttpClient: Could not send last request, device is offline. Throwable: " + t.javaClass.name)
                else
                    Logging.warn("HttpClient: $method Error thrown from network stack. ", t)

                return@withContext HttpResponse(httpResponse, null, t)
            } finally {
                con?.disconnect()
            }
        }

        return result as HttpResponse
    }

    @Throws(IOException::class)
    private fun newHttpURLConnection(url: String): HttpURLConnection {
        return URL(BASE_URL + url).openConnection() as HttpURLConnection
    }

    private fun getThreadTimeout(timeout: Int): Int {
        return timeout + 5000
    }

    companion object {
        private const val OS_API_VERSION = "1"
        private const val OS_ACCEPT_HEADER = "application/vnd.onesignal.v$OS_API_VERSION+json"
        private const val BASE_URL = "https://api.onesignal.com/"
        private const val GET_TIMEOUT = 60000
        private const val TIMEOUT = 120000
        private const val THREAD_ID = 10000
    }
}
