package com.onesignal.user.internal.backend

import com.onesignal.common.DateUtils
import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.customEvents.impl.CustomEventBackendService
import com.onesignal.user.internal.customEvents.impl.CustomEventMetadata
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.json.JSONObject
import java.util.TimeZone

class CustomEventBackendServiceTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    val metadata =
        CustomEventMetadata(
            "Android",
            "sdk",
            "1.0",
            "type",
            "deviceModel",
            "deviceOS",
        )

    test("track event") {
        // Given
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.post(any(), any()) } returns HttpResponse(202, "")
        val customEventBackendService = CustomEventBackendService(spyHttpClient)

        // When
        val properties = JSONObject().put("proKey1", "proVal1").toString()

        val response =
            customEventBackendService.sendCustomEvent(
                appId = "appId",
                onesignalId = "onesignalId",
                externalId = null,
                timestamp = 1,
                eventName = "event-name",
                eventProperties = properties,
                metadata = metadata,
            )

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify {
            spyHttpClient.post(
                "apps/appId/custom_events",
                withArg {
                    val eventsObject = it.getJSONArray("events").getJSONObject(0)
                    val eventMap = mutableMapOf<String, Any?>()
                    for (key in eventsObject.keys()) {
                        eventMap[key] = eventsObject.get(key)
                    }

                    eventMap["name"] shouldBe "event-name"
                    eventMap["onesignal_id"] shouldBe "onesignalId"
                    eventMap["external_id"] shouldBe null
                    eventMap["timestamp"] shouldBe
                        DateUtils
                            .iso8601Format()
                            .apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }.format(
                                1,
                            )

                    val payload = eventMap["payload"] as JSONObject
                    payload.getJSONObject("os_sdk").toString() shouldBeEqual metadata.toJSONObject().toString()
                    payload.getString("proKey1") shouldBeEqual "proVal1"
                },
            )
        }
    }
})
