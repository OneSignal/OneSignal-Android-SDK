package com.onesignal.user.internal.backend

import com.onesignal.common.consistency.RywData
import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.impl.SubscriptionBackendService
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

// WARNING: Adding @RobolectricTest will cause JSONObject.map() to stop working
// at runtime.
class SubscriptionBackendServiceTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("create subscription") {
        // Given
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery {
            spyHttpClient.post(any(), any(), any())
        } returns HttpResponse(202, "{ \"subscription\": { id: \"subscriptionId\" }, \"ryw_token\": \"123\"}")
        val subscriptionBackendService = SubscriptionBackendService(spyHttpClient)

        // When
        val subscription =
            SubscriptionObject(
                "no-id",
                SubscriptionObjectType.ANDROID_PUSH,
                "pushToken",
                true,
                SubscriptionStatus.SUBSCRIBED.value,
            )

        val response = subscriptionBackendService.createSubscription("appId", aliasLabel, aliasValue, subscription)

        // Then
        response shouldBe Pair("subscriptionId", RywData("123", null))
        coVerify {
            spyHttpClient.post(
                "apps/appId/users/by/$aliasLabel/$aliasValue/subscriptions",
                withArg {
                    val sub = it.getJSONObject("subscription")
                    sub.has("id") shouldBe false
                    sub.getString("type") shouldBe "AndroidPush"
                    sub.getString("token") shouldBe "pushToken"
                    sub.getBoolean("enabled") shouldBe true
                    sub.getInt("notification_types") shouldBe 1
                },
                any(),
            )
        }
    }

    test("create subscription throws exception when bad response") {
        // Given
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.post(any(), any(), any()) } returns HttpResponse(404, "NOT FOUND")
        val subscriptionBackendService = SubscriptionBackendService(spyHttpClient)

        // When
        val subscription =
            SubscriptionObject(
                "no-id",
                SubscriptionObjectType.ANDROID_PUSH,
                "pushToken",
                true,
                SubscriptionStatus.SUBSCRIBED.value,
            )
        val exception =
            shouldThrowUnit<BackendException> {
                subscriptionBackendService.createSubscription(
                    "appId",
                    aliasLabel,
                    aliasValue,
                    subscription,
                )
            }

        exception.statusCode shouldBe 404
        exception.response shouldBe "NOT FOUND"
        // Then
        coVerify {
            spyHttpClient.post(
                "apps/appId/users/by/$aliasLabel/$aliasValue/subscriptions",
                withArg {
                    val sub = it.getJSONObject("subscription")
                    sub.has("id") shouldBe false
                    sub.getString("type") shouldBe "AndroidPush"
                    sub.getString("token") shouldBe "pushToken"
                    sub.getBoolean("enabled") shouldBe true
                    sub.getInt("notification_types") shouldBe 1
                },
                any(),
            )
        }
    }

    test("delete subscription by type and token successfully") {
        // Given
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.delete(any(), any()) } returns HttpResponse(200, "")
        val subscriptionBackendService = SubscriptionBackendService(spyHttpClient)

        // When
        subscriptionBackendService.deleteSubscription("appId", SubscriptionType.EMAIL, "test@example.com", "jwt-token")

        // Then
        coVerify {
            spyHttpClient.delete(
                "apps/appId/by/type/email/token/test@example.com/subscriptions",
                any(),
            )
        }
    }

    test("delete subscription throws exception when bad response") {
        // Given
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.delete(any(), any()) } returns HttpResponse(404, "NOT FOUND")
        val subscriptionBackendService = SubscriptionBackendService(spyHttpClient)

        // When
        val exception =
            shouldThrowUnit<BackendException> {
                subscriptionBackendService.deleteSubscription("appId", SubscriptionType.SMS, "+1234567890")
            }

        // Then
        exception.statusCode shouldBe 404
        exception.response shouldBe "NOT FOUND"
        coVerify {
            spyHttpClient.delete(
                "apps/appId/by/type/sms/token/+1234567890/subscriptions",
                any(),
            )
        }
    }
})
