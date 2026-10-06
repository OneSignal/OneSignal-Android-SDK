package com.onesignal.session.internal.session.backend

import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.backend.impl.SessionsBackendService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

private val createRequest =
    CreateSessionRequest(
        onesignalId = "onesignalId",
        subscriptionId = "subscriptionId",
        deviceType = 1,
        startTime = 1_700_000_000L,
        idempotencyKey = "create-key",
    )

private val updateRequest =
    UpdateSessionRequest(
        onesignalId = "onesignalId",
        subscriptionId = "subscriptionId",
        durationSeconds = 42L,
        idempotencyKey = "update-key",
    )

class SessionsBackendServiceTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("create session posts the request body") {
        // Given
        val http = mockk<IHttpClient>()
        coEvery { http.post(any(), any()) } returns HttpResponse(202, """{"data":{"session_id":"server-id"}}""")
        val service = SessionsBackendService(http)

        // When
        service.createSession("appId", createRequest)

        // Then
        coVerify {
            http.post(
                "apps/appId/sessions",
                withArg {
                    it.getString("onesignal_id") shouldBe "onesignalId"
                    it.getString("subscription_id") shouldBe "subscriptionId"
                    it.getInt("device_type") shouldBe 1
                    it.getLong("start_time") shouldBe 1_700_000_000L
                    it.getString("idempotency_key") shouldBe "create-key"
                    it.has("direct_attribution_id") shouldBe false
                },
            )
        }
    }

    test("create session includes direct attribution when set") {
        // Given
        val http = mockk<IHttpClient>()
        coEvery { http.post(any(), any()) } returns HttpResponse(202, """{"data":{"session_id":"server-id"}}""")
        val service = SessionsBackendService(http)

        // When
        service.createSession("appId", createRequest.copy(directAttributionId = "notificationId"))

        // Then
        coVerify {
            http.post(
                "apps/appId/sessions",
                withArg { it.getString("direct_attribution_id") shouldBe "notificationId" },
            )
        }
    }

    test("create session returns the session ID from a 202") {
        // Given
        val http = mockk<IHttpClient>()
        coEvery { http.post(any(), any()) } returns HttpResponse(202, """{"data":{"session_id":"server-id"}}""")
        val service = SessionsBackendService(http)

        // When
        val result = service.createSession("appId", createRequest)

        // Then
        result shouldBe SessionsApiResult.Success("server-id")
    }

    test("create session drops a success response without a session ID") {
        listOf(
            null,
            "",
            "not json",
            "{}",
            """{"data":{}}""",
            """{"data":{"session_id":""}}""",
        ).forEach { payload ->
            // Given
            val http = mockk<IHttpClient>()
            coEvery { http.post(any(), any()) } returns HttpResponse(202, payload)
            val service = SessionsBackendService(http)

            // When
            val result = service.createSession("appId", createRequest)

            // Then
            result shouldBe SessionsApiResult.Drop(202)
        }
    }

    test("update session patches the request body") {
        // Given
        val http = mockk<IHttpClient>()
        coEvery { http.patch(any(), any()) } returns HttpResponse(202, null)
        val service = SessionsBackendService(http)

        // When
        val result = service.updateSession("appId", "server-id", updateRequest)

        // Then
        result shouldBe SessionsApiResult.Success(Unit)
        coVerify {
            http.patch(
                "apps/appId/sessions/server-id",
                withArg {
                    it.getString("onesignal_id") shouldBe "onesignalId"
                    it.getString("subscription_id") shouldBe "subscriptionId"
                    it.getLong("duration_seconds") shouldBe 42L
                    it.getString("idempotency_key") shouldBe "update-key"
                    it.has("end_time") shouldBe false
                },
            )
        }
    }

    test("update session includes end time when set") {
        // Given
        val http = mockk<IHttpClient>()
        coEvery { http.patch(any(), any()) } returns HttpResponse(202, null)
        val service = SessionsBackendService(http)

        // When
        service.updateSession("appId", "server-id", updateRequest.copy(endTime = 1_700_000_042L))

        // Then
        coVerify {
            http.patch(
                "apps/appId/sessions/server-id",
                withArg { it.getLong("end_time") shouldBe 1_700_000_042L },
            )
        }
    }

    test("failures are retried on network error, 5xx, 408, and 429") {
        listOf(0, 408, 429, 500, 502, 503).forEach { statusCode ->
            // Given
            val http = mockk<IHttpClient>()
            coEvery { http.post(any(), any()) } returns HttpResponse(statusCode, null)
            coEvery { http.patch(any(), any()) } returns HttpResponse(statusCode, null)
            val service = SessionsBackendService(http)

            // When / Then
            service.createSession("appId", createRequest) shouldBe SessionsApiResult.Retry(statusCode, null)
            service.updateSession("appId", "server-id", updateRequest) shouldBe SessionsApiResult.Retry(statusCode, null)
        }
    }

    test("other 4xx failures are dropped") {
        listOf(400, 401, 403, 404, 409, 410, 422).forEach { statusCode ->
            // Given
            val http = mockk<IHttpClient>()
            coEvery { http.post(any(), any()) } returns HttpResponse(statusCode, null, retryAfterSeconds = 10)
            coEvery { http.patch(any(), any()) } returns HttpResponse(statusCode, null, retryAfterSeconds = 10)
            val service = SessionsBackendService(http)

            // When / Then
            service.createSession("appId", createRequest) shouldBe SessionsApiResult.Drop(statusCode)
            service.updateSession("appId", "server-id", updateRequest) shouldBe SessionsApiResult.Drop(statusCode)
        }
    }

    test("retry exposes Retry-After from the response") {
        // Given
        val http = mockk<IHttpClient>()
        coEvery { http.post(any(), any()) } returns HttpResponse(429, null, retryAfterSeconds = 30)
        coEvery { http.patch(any(), any()) } returns HttpResponse(503, null, retryAfterSeconds = 15)
        val service = SessionsBackendService(http)

        // When / Then
        service.createSession("appId", createRequest) shouldBe SessionsApiResult.Retry(429, 30)
        service.updateSession("appId", "server-id", updateRequest) shouldBe SessionsApiResult.Retry(503, 15)
    }
})
