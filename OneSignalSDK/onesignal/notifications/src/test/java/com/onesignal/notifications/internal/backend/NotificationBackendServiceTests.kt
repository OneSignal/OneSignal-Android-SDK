package com.onesignal.notifications.internal.backend

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.backend.impl.NotificationBackendService
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.json.JSONObject
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class NotificationBackendServiceTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("updateNotificationAsReceived succeeds when response is successful") {
        /* Given */
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.put(any(), any()) } returns HttpResponse(202, null)

        val notificationBackendService = NotificationBackendService(spyHttpClient)

        /* When */
        notificationBackendService.updateNotificationAsReceived("appId", "notificationId", "subscriptionId", IDeviceService.DeviceType.Android)

        /* Then */
        coVerify {
            spyHttpClient.put(
                "notifications/notificationId/report_received",
                withArg {
                    it.getString("app_id") shouldBe "appId"
                    it.getString("player_id") shouldBe "subscriptionId"
                    it.getInt("device_type") shouldBe IDeviceService.DeviceType.Android.value
                }
            )
        }
    }

    test("updateNotificationAsReceived throws exception when response is unsuccessful") {
        /* Given */
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.put(any(), any()) } returns HttpResponse(404, null)

        val notificationBackendService = NotificationBackendService(spyHttpClient)

        /* When */
        val exception = shouldThrowUnit<BackendException> {
            notificationBackendService.updateNotificationAsReceived(
                "appId",
                "notificationId",
                "subscriptionId",
                IDeviceService.DeviceType.Android
            )
        }

        /* Then */
        exception.statusCode shouldBe 404
    }

    test("updateNotificationAsOpened succeeds when response is successful") {
        /* Given */
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.put(any(), any()) } returns HttpResponse(202, null)

        val notificationBackendService = NotificationBackendService(spyHttpClient)

        /* When */
        notificationBackendService.updateNotificationAsOpened("appId", "notificationId", "subscriptionId", IDeviceService.DeviceType.Android)

        /* Then */
        coVerify {
            spyHttpClient.put(
                "notifications/notificationId",
                withArg {
                    it.getString("app_id") shouldBe "appId"
                    it.getString("player_id") shouldBe "subscriptionId"
                    it.getBoolean("opened") shouldBe true
                    it.getInt("device_type") shouldBe IDeviceService.DeviceType.Android.value
                }
            )
        }
    }

    test("updateNotificationAsOpened throws exception when response is unsuccessful") {
        /* Given */
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.put(any(), any()) } returns HttpResponse(404, null)

        val notificationBackendService = NotificationBackendService(spyHttpClient)

        /* When */
        val exception = shouldThrowUnit<BackendException> {
            notificationBackendService.updateNotificationAsOpened(
                "appId",
                "notificationId",
                "subscriptionId",
                IDeviceService.DeviceType.Android
            )
        }

        /* Then */
        exception.statusCode shouldBe 404
    }

    test("postNotification succeeds when response is successful and appId not provided in payload") {
        /* Given */
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.post(any(), any()) } returns HttpResponse(202, "{prop1: \"val1\"}")

        val notificationBackendService = NotificationBackendService(spyHttpClient)

        val jsonObject = JSONObject().put("prop1", "val1")

        /* When */
        val response = notificationBackendService.postNotification("appId", jsonObject)

        /* Then */
        response.getString("prop1") shouldBe "val1"
        coVerify {
            spyHttpClient.post(
                "notifications/",
                withArg {
                    it.getString("app_id") shouldBe "appId"
                    it.getString("prop1") shouldBe "val1"
                }
            )
        }
    }

    test("postNotification succeeds when response is successful and appId provided in payload") {
        /* Given */
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.post(any(), any()) } returns HttpResponse(202, "{prop1: \"val1\"}")

        val notificationBackendService = NotificationBackendService(spyHttpClient)

        val jsonObject = JSONObject().put("app_id", "appId1").put("prop1", "val1")

        /* When */
        val response = notificationBackendService.postNotification("appId2", jsonObject)

        /* Then */
        response.getString("prop1") shouldBe "val1"
        coVerify {
            spyHttpClient.post(
                "notifications/",
                withArg {
                    it.getString("app_id") shouldBe "appId1"
                    it.getString("prop1") shouldBe "val1"
                }
            )
        }
    }

    test("postNotification throws exception when response is unsuccessful") {
        /* Given */
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.post(any(), any()) } returns HttpResponse(404, null)

        val notificationBackendService = NotificationBackendService(spyHttpClient)

        val jsonObject = JSONObject().put("prop1", "val1")

        /* When */
        val exception = shouldThrowUnit<BackendException> {
            notificationBackendService.postNotification("appId", jsonObject)
        }

        /* Then */
        exception.statusCode shouldBe 404
    }
})
