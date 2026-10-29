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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class NotificationBackendServiceTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("updateNotificationAsReceived succeeds when response is successful") {
        // Given
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.put(any(), any()) } returns HttpResponse(202, null)

        val notificationBackendService = NotificationBackendService(spyHttpClient)

        // When
        notificationBackendService.updateNotificationAsReceived(
            "appId",
            "notificationId",
            "subscriptionId",
            IDeviceService.DeviceType.Android,
            "onesignalId",
        )

        // Then
        coVerify {
            spyHttpClient.put(
                "notifications/notificationId/report_received",
                withArg {
                    it.getString("app_id") shouldBe "appId"
                    it.getString("player_id") shouldBe "subscriptionId"
                    it.getInt("device_type") shouldBe IDeviceService.DeviceType.Android.value
                    it.getString("onesignal_id") shouldBe "onesignalId"
                },
            )
        }
    }

    test("updateNotificationAsReceived omits onesignal_id when the SDK has no backend id") {
        // Given: the receipt was enqueued while the user still had a local placeholder id. The
        // server looks the user up by this field, so a placeholder must never be sent, and the
        // agreed contract is to leave the key out entirely rather than send null.
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.put(any(), any()) } returns HttpResponse(202, null)

        val notificationBackendService = NotificationBackendService(spyHttpClient)

        // When
        notificationBackendService.updateNotificationAsReceived(
            "appId",
            "notificationId",
            "subscriptionId",
            IDeviceService.DeviceType.Android,
            null,
        )

        // Then
        coVerify {
            spyHttpClient.put(
                "notifications/notificationId/report_received",
                withArg {
                    it.getString("player_id") shouldBe "subscriptionId"
                    it.has("onesignal_id") shouldBe false
                },
            )
        }
    }

    test("updateNotificationAsReceived throws exception when response is unsuccessful") {
        // Given
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.put(any(), any()) } returns HttpResponse(404, null)

        val notificationBackendService = NotificationBackendService(spyHttpClient)

        // When
        val exception =
            shouldThrowUnit<BackendException> {
                notificationBackendService.updateNotificationAsReceived(
                    "appId",
                    "notificationId",
                    "subscriptionId",
                    IDeviceService.DeviceType.Android,
                    null,
                )
            }

        // Then
        exception.statusCode shouldBe 404
    }

    test("updateNotificationAsOpened succeeds when response is successful") {
        // Given
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.put(any(), any()) } returns HttpResponse(202, null)

        val notificationBackendService = NotificationBackendService(spyHttpClient)

        // When
        notificationBackendService.updateNotificationAsOpened(
            "appId",
            "notificationId",
            "subscriptionId",
            IDeviceService.DeviceType.Android,
        )

        // Then
        coVerify {
            spyHttpClient.put(
                "notifications/notificationId",
                withArg {
                    it.getString("app_id") shouldBe "appId"
                    it.getString("player_id") shouldBe "subscriptionId"
                    it.getBoolean("opened") shouldBe true
                    it.getInt("device_type") shouldBe IDeviceService.DeviceType.Android.value
                },
            )
        }
    }

    test("updateNotificationAsOpened throws exception when response is unsuccessful") {
        // Given
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.put(any(), any()) } returns HttpResponse(404, null)

        val notificationBackendService = NotificationBackendService(spyHttpClient)

        // When
        val exception =
            shouldThrowUnit<BackendException> {
                notificationBackendService.updateNotificationAsOpened(
                    "appId",
                    "notificationId",
                    "subscriptionId",
                    IDeviceService.DeviceType.Android,
                )
            }

        // Then
        exception.statusCode shouldBe 404
    }
})
