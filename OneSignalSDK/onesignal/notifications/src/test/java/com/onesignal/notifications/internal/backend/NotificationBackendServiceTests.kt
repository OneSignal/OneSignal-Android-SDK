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
        )

        // Then
        coVerify {
            spyHttpClient.put(
                "notifications/notificationId/report_received",
                withArg {
                    it.getString("app_id") shouldBe "appId"
                    it.getString("player_id") shouldBe "subscriptionId"
                    it.getInt("device_type") shouldBe IDeviceService.DeviceType.Android.value
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
