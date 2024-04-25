package com.onesignal.core.internal.http

import com.onesignal.common.OneSignalUtils
import com.onesignal.core.internal.http.impl.HttpClient
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beInstanceOf
import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONObject

class Mocks {
    internal val mockConfigModel = MockHelper.configModelStore()
    internal val response = MockHttpConnectionFactory.MockResponse()
    internal val factory = MockHttpConnectionFactory(response)
    internal val httpClient by lazy {
        HttpClient(factory, MockPreferencesService(), mockConfigModel)
    }
}

class HttpClientTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("timeout request will give a bad response") {
        // Given
        val mocks = Mocks()
        // HttpClient will add 5 seconds to the httpTimeout to "give up" so we need to fail the request more than 5 seconds beyond our timeout.
        mocks.response.mockRequestTime = 10_000
        mocks.mockConfigModel.model.let {
            it.httpGetTimeout = 200
            it.httpGetTimeout = 200
        }

        // When
        val response = mocks.httpClient.get("URL")

        // Then
        response.statusCode shouldBe 0
        response.throwable shouldNotBe null
        response.throwable should beInstanceOf<TimeoutCancellationException>()
    }

    test("SDKHeader is included in all requests") {
        // Given
        val mocks = Mocks()
        val httpClient = mocks.httpClient

        // When
        httpClient.get("URL")
        httpClient.delete("URL")
        httpClient.patch("URL", JSONObject())
        httpClient.post("URL", JSONObject())
        httpClient.put("URL", JSONObject())

        // Then
        for (connection in mocks.factory.connections) {
            connection.getRequestProperty("SDK-Version") shouldBe "onesignal/android/${OneSignalUtils.SDK_VERSION}"
        }
    }

    test("GET with cache key uses cache when unchanged") {
        // Given
        val mocks = Mocks()
        val payload = "RESPONSE IS THIS"
        val mockResponse1 = MockHttpConnectionFactory.MockResponse()
        mockResponse1.status = 200
        mockResponse1.responseBody = payload
        mockResponse1.mockProps.put("etag", "MOCK_ETAG")
        mocks.factory.mockResponse = mockResponse1

        val mockResponse2 = MockHttpConnectionFactory.MockResponse()
        mockResponse2.status = 304

        val factory = mocks.factory
        val httpClient = mocks.httpClient

        // When
        val response1 = httpClient.get("URL", "CACHE_KEY")
        factory.mockResponse = mockResponse2
        val response2 = httpClient.get("URL", "CACHE_KEY")

        // Then
        response1.statusCode shouldBe 200
        response1.payload shouldBe payload
        response2.statusCode shouldBe 304
        response2.payload shouldBe payload
        factory.lastConnection!!.getRequestProperty("if-none-match") shouldBe "MOCK_ETAG"
    }

    test("GET with cache key replaces cache when changed") {
        // Given
        val mocks = Mocks()
        val payload1 = "RESPONSE IS THIS"
        val payload2 = "A DIFFERENT RESPONSE"
        val mockResponse1 = MockHttpConnectionFactory.MockResponse()
        mockResponse1.status = 200
        mockResponse1.responseBody = payload1
        mockResponse1.mockProps.put("etag", "MOCK_ETAG1")
        mocks.factory.mockResponse = mockResponse1

        val mockResponse2 = MockHttpConnectionFactory.MockResponse()
        mockResponse2.status = 200
        mockResponse2.responseBody = payload2
        mockResponse2.mockProps.put("etag", "MOCK_ETAG2")

        val mockResponse3 = MockHttpConnectionFactory.MockResponse()
        mockResponse3.status = 304

        val factory = mocks.factory
        val httpClient = mocks.httpClient

        // When
        val response1 = httpClient.get("URL", "CACHE_KEY")

        factory.mockResponse = mockResponse2
        val response2 = httpClient.get("URL", "CACHE_KEY")

        factory.mockResponse = mockResponse3
        val response3 = httpClient.get("URL", "CACHE_KEY")

        // Then
        response1.statusCode shouldBe 200
        response1.payload shouldBe payload1
        response2.statusCode shouldBe 200
        response2.payload shouldBe payload2
        response3.statusCode shouldBe 304
        response3.payload shouldBe payload2

        factory.lastConnection!!.getRequestProperty("if-none-match") shouldBe "MOCK_ETAG2"
    }

    test("Error response") {
        // Given
        val mocks = Mocks()
        val payload = "ERROR RESPONSE"
        mocks.response.status = 400
        mocks.response.errorResponseBody = payload

        // When
        val response = mocks.httpClient.post("URL", JSONObject())

        // Then
        response.statusCode shouldBe 400
        response.payload shouldBe payload
    }

    test("should parse valid Retry-After, on 429") {
        // Given
        val mocks = Mocks()
        mocks.response.status = 429
        mocks.response.mockProps["Retry-After"] = "1234"
        mocks.response.errorResponseBody = "{}"

        // When
        val response = mocks.httpClient.post("URL", JSONObject())

        // Then
        response.retryAfterSeconds shouldBe 1234
    }

    test("should parse valid Retry-After, on 500") {
        // Given
        val mocks = Mocks()
        mocks.response.status = 500
        mocks.response.mockProps["Retry-After"] = "1234"
        mocks.response.errorResponseBody = "{}"

        // When
        val response = mocks.httpClient.post("URL", JSONObject())

        // Then
        response.retryAfterSeconds shouldBe 1234
    }

    test("should use set fallback retryAfterSeconds if can't parse Retry-After") {
        // Given
        val mocks = Mocks()
        mocks.response.status = 429
        mocks.response.mockProps["Retry-After"] = "INVALID FORMAT"
        mocks.response.errorResponseBody = "{}"

        // When
        val response = mocks.httpClient.post("URL", JSONObject())

        // Then
        response.retryAfterSeconds shouldBe 60
    }

    // Since 429 means "Too Many Requests", for some reason the server doesn't give us a
    // Retry-After we should assume a our safe fallback.
    test("should use set fallback retryAfterSeconds if 429 and Retry-After is missing") {
        // Given
        val mocks = Mocks()
        mocks.response.status = 429
        mocks.response.errorResponseBody = "{}"

        // When
        val response = mocks.httpClient.post("URL", JSONObject())

        // Then
        response.retryAfterSeconds shouldBe 60
    }
})
