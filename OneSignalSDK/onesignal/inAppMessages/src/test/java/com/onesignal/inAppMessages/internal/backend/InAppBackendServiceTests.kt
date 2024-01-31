package com.onesignal.inAppMessages.internal.backend

import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.safeBool
import com.onesignal.common.safeInt
import com.onesignal.common.safeString
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.Trigger
import com.onesignal.inAppMessages.internal.backend.impl.InAppBackendService
import com.onesignal.inAppMessages.internal.hydrators.InAppHydrator
import com.onesignal.inAppMessages.mocks.MockHelper
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class InAppBackendServiceTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("listInAppMessages with no messages returns zero-lengthed array") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.get(any(), any()) } returns HttpResponse(200, "{ in_app_messages: [] }")

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        val response = inAppBackendService.listInAppMessages("appId", "subscriptionId")

        /* Then */
        response shouldNotBe null
        response!!.count() shouldBe 0
        coVerify(exactly = 1) { mockHttpClient.get("apps/appId/subscriptions/subscriptionId/iams", any()) }
    }

    test("listInAppMessages with 1 message returns one-lengthed array") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.get(any(), any()) } returns HttpResponse(200, "{ in_app_messages: [{id: \"messageId1\", variants:{all: {en: \"content1\"}}, triggers:[[{id: \"triggerId1\", kind: \"custom\", property: \"property1\", operator: \"equal\", value: \"value1\"}]], end_time: \"2020-12-13T23:23:23\", redisplay: { limit: 11111, delay: 22222}] }")

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        val response = inAppBackendService.listInAppMessages("appId", "subscriptionId")

        /* Then */
        response shouldNotBe null
        response!!.count() shouldBe 1
        response[0].messageId shouldBe "messageId1"
        response[0].variants.keys shouldBe 1
        response[0].variants["all"] shouldNotBe null
        response[0].variants["all"]!!.keys shouldBe 1
        response[0].variants["all"]!!["en"] shouldBe "content1"
        response[0].triggers.count() shouldBe 1
        response[0].triggers[0].count() shouldBe 1
        response[0].triggers[0][0].triggerId shouldBe "triggerId1"
        response[0].triggers[0][0].kind shouldBe Trigger.OSTriggerKind.CUSTOM
        response[0].triggers[0][0].property shouldBe "property1"
        response[0].triggers[0][0].operatorType shouldBe Trigger.OSTriggerOperator.EQUAL_TO
        response[0].triggers[0][0].value shouldBe "value1"
        response[0].isFinished shouldBe true
        response[0].redisplayStats.displayLimit shouldBe 11111
        response[0].redisplayStats.displayDelay shouldBe 22222

        coVerify(exactly = 1) { mockHttpClient.get("apps/appId/subscriptions/subscriptionId/iams", any()) }
    }

    test("listInAppMessages returns null when non-success response") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.get(any(), any()) } returns HttpResponse(404, null)

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        val response = inAppBackendService.listInAppMessages("appId", "subscriptionId")

        /* Then */
        response shouldBe null
        coVerify(exactly = 1) { mockHttpClient.get("apps/appId/subscriptions/subscriptionId/iams", any()) }
    }

    test("getIAMData successfully hydrates successful response") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.get(any(), any()) } returns HttpResponse(200, "{html: \"html1\", display_duration: 123, styles: {remove_height_margin: true, remove_width_margin: true}}")

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        val response = inAppBackendService.getIAMData("appId", "messageId", "variantId")

        /* Then */
        response shouldNotBe null
        response.shouldRetry shouldBe false
        response.content shouldNotBe null
        response.content!!.contentHtml shouldStartWith "html1"
        response.content!!.displayDuration shouldBe 123
        response.content!!.useHeightMargin shouldBe false
        response.content!!.useWidthMargin shouldBe false
        response.content!!.isFullBleed shouldBe true

        coVerify(exactly = 1) { mockHttpClient.get("in_app_messages/messageId/variants/variantId/html?app_id=appId", any()) }
    }

    test("getIAMData successfully hydrates successful response with no content") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.get(any(), any()) } returns HttpResponse(200, "{}")

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        val response = inAppBackendService.getIAMData("appId", "messageId", "variantId")

        /* Then */
        response shouldNotBe null
        response.shouldRetry shouldBe false
        response.content shouldBe null

        coVerify(exactly = 1) { mockHttpClient.get("in_app_messages/messageId/variants/variantId/html?app_id=appId", any()) }
    }

    test("getIAMData successfully hydrates successful response with no style") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.get(any(), any()) } returns HttpResponse(200, "{html: \"html1\", display_duration: 123 }")

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        val response = inAppBackendService.getIAMData("appId", "messageId", "variantId")

        /* Then */
        response shouldNotBe null
        response.shouldRetry shouldBe false
        response.content shouldNotBe null
        response.content!!.contentHtml shouldStartWith "html1"
        response.content!!.displayDuration shouldBe 123
        response.content!!.useHeightMargin shouldBe true
        response.content!!.useWidthMargin shouldBe true
        response.content!!.isFullBleed shouldBe false

        coVerify(exactly = 1) { mockHttpClient.get("in_app_messages/messageId/variants/variantId/html?app_id=appId", any()) }
    }

    test("getIAMData indicates retry when retryable response provided") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.get(any(), any()) } returns HttpResponse(409, null)

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        val response = inAppBackendService.getIAMData("appId", "messageId", "variantId")

        /* Then */
        response shouldNotBe null
        response.shouldRetry shouldBe true
        response.content shouldBe null

        coVerify(exactly = 1) { mockHttpClient.get("in_app_messages/messageId/variants/variantId/html?app_id=appId", any()) }
    }

    test("getIAMData indicates no retry when non-retryable response provided") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.get(any(), any()) } returns HttpResponse(404, null)

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        val response = inAppBackendService.getIAMData("appId", "messageId", "variantId")

        /* Then */
        response shouldNotBe null
        response.shouldRetry shouldBe false
        response.content shouldBe null

        coVerify(exactly = 1) { mockHttpClient.get("in_app_messages/messageId/variants/variantId/html?app_id=appId", any()) }
    }

    test("getIAMData indicates no retry when retryable response provided more than 3 times") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.get(any(), any()) } returns HttpResponse(409, null)

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        val response1 = inAppBackendService.getIAMData("appId", "messageId", "variantId")
        val response2 = inAppBackendService.getIAMData("appId", "messageId", "variantId")
        val response3 = inAppBackendService.getIAMData("appId", "messageId", "variantId")
        val response4 = inAppBackendService.getIAMData("appId", "messageId", "variantId")

        /* Then */
        response1 shouldNotBe null
        response1.shouldRetry shouldBe true
        response1.content shouldBe null
        response2 shouldNotBe null
        response2.shouldRetry shouldBe true
        response2.content shouldBe null
        response3 shouldNotBe null
        response3.shouldRetry shouldBe true
        response3.content shouldBe null
        response4 shouldNotBe null
        response4.shouldRetry shouldBe false
        response4.content shouldBe null

        coVerify(exactly = 4) { mockHttpClient.get("in_app_messages/messageId/variants/variantId/html?app_id=appId", any()) }
    }

    test("getIAMPreviewData successfully hydrates successful response") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.get(any(), any()) } returns HttpResponse(200, "{html: \"html1\", display_duration: 123, styles: {remove_height_margin: true, remove_width_margin: true}}")

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        val response = inAppBackendService.getIAMPreviewData("appId", "previewUUID")

        /* Then */
        response shouldNotBe null
        response!!.contentHtml shouldStartWith "html1"
        response!!.displayDuration shouldBe 123
        response!!.useHeightMargin shouldBe false
        response!!.useWidthMargin shouldBe false
        response!!.isFullBleed shouldBe true

        coVerify(exactly = 1) { mockHttpClient.get("in_app_messages/device_preview?preview_id=previewUUID&app_id=appId", any()) }
    }

    test("getIAMPreviewData returns no data when response is unsuccessful") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.get(any(), any()) } returns HttpResponse(404, null)

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        val response = inAppBackendService.getIAMPreviewData("appId", "previewUUID")

        /* Then */
        response shouldBe null

        coVerify(exactly = 1) { mockHttpClient.get("in_app_messages/device_preview?preview_id=previewUUID&app_id=appId", any()) }
    }

    test("sendIAMClick is successful when there is a successful response") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.post(any(), any()) } returns HttpResponse(200, "{}")

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        inAppBackendService.sendIAMClick("appId", "subscriptionId", "variantId", "messageId", "clickId", isFirstClick = true)

        /* Then */
        coVerify(exactly = 1) {
            mockHttpClient.post(
                "in_app_messages/messageId/click",
                withArg {
                    it.safeString("app_id") shouldBe "appId"
                    it.safeInt("device_type") shouldBe IDeviceService.DeviceType.Android.value
                    it.safeString("player_id") shouldBe "subscriptionId"
                    it.safeString("click_id") shouldBe "clickId"
                    it.safeString("variant_id") shouldBe "variantId"
                    it.safeBool("first_click") shouldBe true
                }
            )
        }
    }

    test("sendIAMClick throws exception when there is an unsuccessful response") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.post(any(), any()) } returns HttpResponse(409, "{}")

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        val exception = shouldThrowUnit<BackendException> {
            inAppBackendService.sendIAMClick(
                "appId",
                "subscriptionId",
                "variantId",
                "messageId",
                "clickId",
                isFirstClick = true
            )
        }

        /* Then */
        exception.statusCode shouldBe 409
        exception.response shouldBe "{}"
        coVerify(exactly = 1) {
            mockHttpClient.post(
                "in_app_messages/messageId/click",
                withArg {
                    it.safeString("app_id") shouldBe "appId"
                    it.safeInt("device_type") shouldBe IDeviceService.DeviceType.Android.value
                    it.safeString("player_id") shouldBe "subscriptionId"
                    it.safeString("click_id") shouldBe "clickId"
                    it.safeString("variant_id") shouldBe "variantId"
                    it.safeBool("first_click") shouldBe true
                }
            )
        }
    }

    test("sendIAMImpression is successful when there is a successful response") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.post(any(), any()) } returns HttpResponse(200, "{}")

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        inAppBackendService.sendIAMImpression("appId", "subscriptionId", "variantId", "messageId")

        /* Then */
        coVerify(exactly = 1) {
            mockHttpClient.post(
                "in_app_messages/messageId/impression",
                withArg {
                    it.safeString("app_id") shouldBe "appId"
                    it.safeInt("device_type") shouldBe IDeviceService.DeviceType.Android.value
                    it.safeString("player_id") shouldBe "subscriptionId"
                    it.safeString("variant_id") shouldBe "variantId"
                    it.safeBool("first_impression") shouldBe true
                }
            )
        }
    }

    test("sendIAMImpression throws exception when there is an unsuccessful response") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.post(any(), any()) } returns HttpResponse(409, "{}")

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        val exception = shouldThrowUnit<BackendException> {
            inAppBackendService.sendIAMImpression("appId", "subscriptionId", "variantId", "messageId")
        }

        /* Then */
        exception.statusCode shouldBe 409
        exception.response shouldBe "{}"
        coVerify(exactly = 1) {
            mockHttpClient.post(
                "in_app_messages/messageId/impression",
                withArg {
                    it.safeString("app_id") shouldBe "appId"
                    it.safeInt("device_type") shouldBe IDeviceService.DeviceType.Android.value
                    it.safeString("player_id") shouldBe "subscriptionId"
                    it.safeString("variant_id") shouldBe "variantId"
                    it.safeBool("first_impression") shouldBe true
                }
            )
        }
    }

    test("sendIAMPageImpression is successful when there is a successful response") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.post(any(), any()) } returns HttpResponse(200, "{}")

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        inAppBackendService.sendIAMPageImpression("appId", "subscriptionId", "variantId", "messageId", "pageId")

        /* Then */
        coVerify(exactly = 1) {
            mockHttpClient.post(
                "in_app_messages/messageId/pageImpression",
                withArg {
                    it.safeString("app_id") shouldBe "appId"
                    it.safeInt("device_type") shouldBe IDeviceService.DeviceType.Android.value
                    it.safeString("player_id") shouldBe "subscriptionId"
                    it.safeString("variant_id") shouldBe "variantId"
                    it.safeString("page_id") shouldBe "pageId"
                }
            )
        }
    }

    test("sendIAMPageImpression throws exception when there is an unsuccessful response") {
        /* Given */
        val mockHydrator = InAppHydrator(MockHelper.time(1000), MockHelper.propertiesModelStore())
        val mockHttpClient = mockk<IHttpClient>()
        coEvery { mockHttpClient.post(any(), any()) } returns HttpResponse(409, "{}")

        val inAppBackendService = InAppBackendService(mockHttpClient, MockHelper.deviceService(), mockHydrator)

        /* When */
        val exception = shouldThrowUnit<BackendException> {
            inAppBackendService.sendIAMPageImpression("appId", "subscriptionId", "variantId", "messageId", "pageId")
        }

        /* Then */
        exception.statusCode shouldBe 409
        exception.response shouldBe "{}"
        coVerify(exactly = 1) {
            mockHttpClient.post(
                "in_app_messages/messageId/pageImpression",
                withArg {
                    it.safeString("app_id") shouldBe "appId"
                    it.safeInt("device_type") shouldBe IDeviceService.DeviceType.Android.value
                    it.safeString("player_id") shouldBe "subscriptionId"
                    it.safeString("variant_id") shouldBe "variantId"
                    it.safeString("page_id") shouldBe "pageId"
                }
            )
        }
    }
})
