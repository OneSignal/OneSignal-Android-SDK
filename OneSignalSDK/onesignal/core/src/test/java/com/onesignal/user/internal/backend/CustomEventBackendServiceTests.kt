package com.onesignal.user.internal.backend

import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.customEvents.impl.CustomEvent
import com.onesignal.user.internal.customEvents.impl.CustomEventBackendService
import com.onesignal.user.internal.customEvents.impl.CustomEventMetadata
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.json.JSONObject

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
        val properties =
            mapOf(
                "proKey1" to "proVal1",
            )
        val customEvent =
            CustomEvent(
                "event-name",
                properties,
            )

        val response =
            customEventBackendService.sendCustomEvent(
                appId = "appId",
                onesignalId = "onesignalId",
                externalId = null,
                timestamp = 1,
                customEvent = customEvent,
                metadata = metadata,
            )

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify {
            spyHttpClient.post(
                "apps/appId/integrations/sdk/custom_events",
                withArg {
                    val eventsObject = it.getJSONArray("events").getJSONObject(0)
                    val eventMap = mutableMapOf<String, Any?>()
                    for (key in eventsObject.keys()) {
                        eventMap[key] = eventsObject.get(key)
                    }

                    eventMap.get("name") shouldBe customEvent.name
                    eventMap.get("app_id") shouldBe "appId"
                    eventMap.get("onesignal_id") shouldBe "onesignalId"
                    eventMap.get("external_id") shouldBe null
                    eventMap.get("timestamp") shouldBe "1969-12-31T19:00:00.001Z"

                    val payload = eventMap.get("payload") as JSONObject
                    payload.getJSONObject("os_sdk").toString() shouldBeEqual metadata.toJSONObject().toString()
                    payload.getString("proKey1") shouldBeEqual "proVal1"
                },
            )
        }
    }
})
