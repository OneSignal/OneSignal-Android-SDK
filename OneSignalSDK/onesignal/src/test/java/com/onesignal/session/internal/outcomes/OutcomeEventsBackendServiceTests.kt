package com.onesignal.session.internal.outcomes

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.influence.InfluenceType
import com.onesignal.session.internal.outcomes.impl.OutcomeEvent
import com.onesignal.session.internal.outcomes.impl.OutcomeEventsBackendService
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class OutcomeEventsBackendServiceTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("send outcome event") {
        /* Given */
        val evnt = OutcomeEvent(InfluenceType.DIRECT, null, "EVENT_NAME", 0, 0F)
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.post(any(), any()) } returns HttpResponse(200, null)
        val outcomeEventsController = OutcomeEventsBackendService(spyHttpClient)

        /* When */
        outcomeEventsController.sendOutcomeEvent("appId", "onesignalId", "subscriptionId", null, evnt)

        /* Then */
        coVerify {
            spyHttpClient.post(
                "outcomes/measure",
                withArg {
                    it.getString("app_id") shouldBe "appId"
                    it.getString("onesignal_id") shouldBe "onesignalId"
                    it.getJSONObject("subscription").getString("id") shouldBe "subscriptionId"
                    it.getString("id") shouldBe "EVENT_NAME"
                    it.has("direct") shouldBe false
                    it.has("notification_ids") shouldBe false
                    it.has("timestamp") shouldBe false
                    it.has("weight") shouldBe false
                }
            )
        }
    }

    test("send outcome event with weight") {
        /* Given */
        val evnt = OutcomeEvent(InfluenceType.DIRECT, null, "EVENT_NAME", 0, 1F)
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.post(any(), any()) } returns HttpResponse(200, null)
        val outcomeEventsController = OutcomeEventsBackendService(spyHttpClient)

        /* When */
        outcomeEventsController.sendOutcomeEvent("appId", "onesignalId", "subscriptionId", null, evnt)

        /* Then */
        coVerify {
            spyHttpClient.post(
                "outcomes/measure",
                withArg {
                    it.getString("app_id") shouldBe "appId"
                    it.getString("onesignal_id") shouldBe "onesignalId"
                    it.getJSONObject("subscription").getString("id") shouldBe "subscriptionId"
                    it.getString("id") shouldBe "EVENT_NAME"
                    it.getInt("weight") shouldBe 1
                    it.has("direct") shouldBe false
                    it.has("notification_ids") shouldBe false
                    it.has("timestamp") shouldBe false
                }
            )
        }
    }

    test("send outcome event with indirect") {
        /* Given */
        val evnt = OutcomeEvent(InfluenceType.DIRECT, null, "EVENT_NAME", 0, 0F)
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.post(any(), any()) } returns HttpResponse(200, null)
        val outcomeEventsController = OutcomeEventsBackendService(spyHttpClient)

        /* When */
        outcomeEventsController.sendOutcomeEvent("appId", "onesignalId", "subscriptionId", false, evnt)

        /* Then */
        coVerify {
            spyHttpClient.post(
                "outcomes/measure",
                withArg {
                    it.getString("app_id") shouldBe "appId"
                    it.getString("onesignal_id") shouldBe "onesignalId"
                    it.getJSONObject("subscription").getString("id") shouldBe "subscriptionId"
                    it.getString("id") shouldBe "EVENT_NAME"
                    it.getBoolean("direct") shouldBe false
                    it.has("notification_ids") shouldBe false
                    it.has("timestamp") shouldBe false
                    it.has("weight") shouldBe false
                }
            )
        }
    }

    test("send outcome event with direct") {
        /* Given */
        val evnt = OutcomeEvent(InfluenceType.DIRECT, null, "EVENT_NAME", 0, 0F)
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.post(any(), any()) } returns HttpResponse(200, null)
        val outcomeEventsController = OutcomeEventsBackendService(spyHttpClient)

        /* When */
        outcomeEventsController.sendOutcomeEvent("appId", "onesignalId", "subscriptionId", true, evnt)

        /* Then */
        coVerify {
            spyHttpClient.post(
                "outcomes/measure",
                withArg {
                    it.getString("app_id") shouldBe "appId"
                    it.getString("onesignal_id") shouldBe "onesignalId"
                    it.getJSONObject("subscription").getString("id") shouldBe "subscriptionId"
                    it.getString("id") shouldBe "EVENT_NAME"
                    it.getBoolean("direct") shouldBe true
                    it.has("notification_ids") shouldBe false
                    it.has("timestamp") shouldBe false
                    it.has("weight") shouldBe false
                }
            )
        }
    }

    test("send outcome event with timestamp") {
        /* Given */
        val evnt = OutcomeEvent(InfluenceType.DIRECT, null, "EVENT_NAME", 1111L, 0F)
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.post(any(), any()) } returns HttpResponse(200, null)
        val outcomeEventsController = OutcomeEventsBackendService(spyHttpClient)

        /* When */
        outcomeEventsController.sendOutcomeEvent("appId", "onesignalId", "subscriptionId", null, evnt)

        /* Then */
        coVerify {
            spyHttpClient.post(
                "outcomes/measure",
                withArg {
                    it.getString("app_id") shouldBe "appId"
                    it.getString("onesignal_id") shouldBe "onesignalId"
                    it.getJSONObject("subscription").getString("id") shouldBe "subscriptionId"
                    it.getString("id") shouldBe "EVENT_NAME"
                    it.getInt("timestamp") shouldBe 1111
                    it.has("notification_ids") shouldBe false
                    it.has("weight") shouldBe false
                    it.has("direct") shouldBe false
                }
            )
        }
    }

    test("send outcome event with unsuccessful response") {
        /* Given */
        val evnt = OutcomeEvent(InfluenceType.DIRECT, null, "EVENT_NAME", 1111L, 0F)
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.post(any(), any()) } returns HttpResponse(503, "SERVICE UNAVAILABLE")
        val outcomeEventsController = OutcomeEventsBackendService(spyHttpClient)

        /* When */
        val exception = shouldThrowUnit<BackendException> {
            outcomeEventsController.sendOutcomeEvent("appId", "onesignalId", "subscriptionId", null, evnt)
        }

        /* Then */
        exception.statusCode shouldBe 503
        exception.response shouldBe "SERVICE UNAVAILABLE"
    }
})
