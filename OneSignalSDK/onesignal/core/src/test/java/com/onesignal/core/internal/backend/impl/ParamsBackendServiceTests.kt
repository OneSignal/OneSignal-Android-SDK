package com.onesignal.core.internal.backend.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk

/**
 * Regression coverage for SDK-4478: a 2xx response with a non-JSON body (e.g. an
 * intercepting proxy or captive portal returning HTML) used to surface as an uncaught
 * [org.json.JSONException] on the IO dispatcher and silently kill the params refresh
 * loop. [ParamsBackendService.fetchParams] now converts those into [BackendException] so
 * the caller's existing retry/backoff path handles them like any other transient failure.
 */
class ParamsBackendServiceTests : FunSpec({
    var originalLogLevel: LogLevel = LogLevel.WARN

    beforeEach {
        // Logging.warn() ultimately routes through android.util.Log, which is not mocked
        // in plain JVM unit tests. Silence it for the duration of these tests.
        originalLogLevel = Logging.logLevel
        Logging.logLevel = LogLevel.NONE
    }

    afterEach {
        Logging.logLevel = originalLogLevel
    }

    test("malformed (HTML) 200 payload throws BackendException instead of JSONException") {
        val http = mockk<IHttpClient>()
        val htmlBody = "<html><head><title>Burp</title></head><body>intercepted</body></html>"
        coEvery { http.get(any(), any()) } returns HttpResponse(200, htmlBody)

        val ex =
            shouldThrow<BackendException> {
                ParamsBackendService(http).fetchParams("appId", null)
            }

        ex.statusCode shouldBe 200
        ex.response shouldBe htmlBody
    }

    test("empty 200 payload throws BackendException instead of NullPointerException") {
        val http = mockk<IHttpClient>()
        coEvery { http.get(any(), any()) } returns HttpResponse(200, null)

        val ex =
            shouldThrow<BackendException> {
                ParamsBackendService(http).fetchParams("appId", null)
            }

        ex.statusCode shouldBe 200
    }

    test("non-2xx response still throws BackendException with the original status code") {
        val http = mockk<IHttpClient>()
        coEvery { http.get(any(), any()) } returns HttpResponse(403, """{"errors":["Forbidden"]}""")

        val ex =
            shouldThrow<BackendException> {
                ParamsBackendService(http).fetchParams("appId", null)
            }

        ex.statusCode shouldBe 403
        ex.response shouldBe """{"errors":["Forbidden"]}"""
    }

    test("valid JSON payload is parsed and returns a populated ParamsObject") {
        val http = mockk<IHttpClient>()
        coEvery { http.get(any(), any()) } returns
            HttpResponse(
                200,
                """{"android_sender_id":"sender-123","enterp":true,"fcm":{"api_key":"k","app_id":"a","project_id":"p"}}""",
            )

        val params = ParamsBackendService(http).fetchParams("appId", null)

        params shouldNotBe null
        params.googleProjectNumber shouldBe "sender-123"
        params.enterprise shouldBe true
        params.fcmParams.apiKey shouldBe "k"
        params.fcmParams.appId shouldBe "a"
        params.fcmParams.projectId shouldBe "p"
    }
})
