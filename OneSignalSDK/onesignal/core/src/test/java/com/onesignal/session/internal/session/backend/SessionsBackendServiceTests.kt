package com.onesignal.session.internal.session.backend

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.backend.impl.SessionsBackendService
import io.kotest.assertions.throwables.shouldThrow
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
                    it.getString("start_time") shouldBe "2023-11-14T22:13:20.000Z"
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
        result shouldBe "server-id"
    }

    test("create session returns null for a success response without a session ID") {
        listOf(
            null,
            "",
            "not json",
            "{}",
            """{"data":{}}""",
            """{"data":{"session_id":""}}""",
            """{"data":{"session_id":null}}""",
            """{"data":{"session_id":123}}""",
            """{"data":"server-id"}""",
        ).forEach { payload ->
            // Given
            val http = mockk<IHttpClient>()
            coEvery { http.post(any(), any()) } returns HttpResponse(202, payload)
            val service = SessionsBackendService(http)

            // When
            val result = service.createSession("appId", createRequest)

            // Then
            result shouldBe null
        }
    }

    test("update session patches the request body") {
        // Given
        val http = mockk<IHttpClient>()
        coEvery { http.patch(any(), any()) } returns HttpResponse(202, null)
        val service = SessionsBackendService(http)

        // When
        service.updateSession("appId", "server-id", updateRequest)

        // Then
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
                withArg { it.getString("end_time") shouldBe "2023-11-14T22:14:02.000Z" },
            )
        }
    }

    test("failures throw BackendException with the status code and Retry-After") {
        listOf(-1, 0, 400, 404, 422, 429, 503).forEach { statusCode ->
            // Given
            val http = mockk<IHttpClient>()
            coEvery { http.post(any(), any()) } returns HttpResponse(statusCode, "error", retryAfterSeconds = 30)
            coEvery { http.patch(any(), any()) } returns HttpResponse(statusCode, "error", retryAfterSeconds = 30)
            val service = SessionsBackendService(http)

            // When
            val createException = shouldThrow<BackendException> { service.createSession("appId", createRequest) }
            val updateException = shouldThrow<BackendException> { service.updateSession("appId", "server-id", updateRequest) }

            // Then
            listOf(createException, updateException).forEach {
                it.statusCode shouldBe statusCode
                it.response shouldBe "error"
                it.retryAfterSeconds shouldBe 30
            }
        }
    }
})
