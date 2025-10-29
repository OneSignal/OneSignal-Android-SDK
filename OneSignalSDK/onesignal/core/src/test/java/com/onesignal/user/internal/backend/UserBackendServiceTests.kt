package com.onesignal.user.internal.backend

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.impl.UserBackendService
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal

class UserBackendServiceTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("create user with nothing throws an exception") {
        // Given
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.post(any(), any()) } returns HttpResponse(403, "FORBIDDEN")
        val userBackendService = UserBackendService(spyHttpClient)
        val identities = mapOf<String, String>()
        val properties = mapOf<String, String>()
        val subscriptions = listOf<SubscriptionObject>()

        // When
        val exception =
            shouldThrowUnit<BackendException> {
                userBackendService.createUser("appId", identities, subscriptions, properties)
            }

        // Then
        exception.statusCode shouldBe 403
        exception.response shouldBe "FORBIDDEN"
    }

    test("create user with an alias creates a new user") {
        // Given
        val osId = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery {
            spyHttpClient.post(any(), any())
        } returns HttpResponse(202, "{identity:{onesignal_id: \"$osId\", aliasLabel1: \"aliasValue1\"}, properties:{timezone_id: \"testTimeZone\", language: \"testLanguage\"}}")
        val userBackendService = UserBackendService(spyHttpClient)
        val identities = mapOf("aliasLabel1" to "aliasValue1")
        val properties = mapOf("timezone_id" to "testTimeZone", "language" to "testLanguage")
        val subscriptions = listOf<SubscriptionObject>()

        // When
        val response = userBackendService.createUser("appId", identities, subscriptions, properties)

        // Then
        response.identities["onesignal_id"] shouldBe osId
        response.identities["aliasLabel1"] shouldBe "aliasValue1"
        response.properties.timezoneId shouldBe "testTimeZone"
        response.properties.language shouldBe "testLanguage"
        response.subscriptions.count() shouldBe 0
        coVerify {
            spyHttpClient.post(
                "apps/appId/users",
                withArg {
                    it.has("identity") shouldBe true
                    it.getJSONObject("identity").has("aliasLabel1") shouldBe true
                    it.getJSONObject("identity").getString("aliasLabel1") shouldBe "aliasValue1"
                    it.has("properties") shouldBe true
                    it.has("subscriptions") shouldBe false
                },
            )
        }
    }

    test("create user with a subscription creates a new user") {
        // Given
        val osId = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery {
            spyHttpClient.post(any(), any())
        } returns HttpResponse(202, "{identity:{onesignal_id: \"$osId\"}, subscriptions:[{id:\"subscriptionId1\", type:\"AndroidPush\"}], properties:{timezone_id: \"testTimeZone\", language: \"testLanguage\"}}")
        val userBackendService = UserBackendService(spyHttpClient)
        val identities = mapOf<String, String>()
        val subscriptions = mutableListOf<SubscriptionObject>()
        val properties = mapOf("timezone_id" to "testTimeZone", "language" to "testLanguage")
        subscriptions.add(SubscriptionObject("SHOULDNOTUSE", SubscriptionObjectType.ANDROID_PUSH))

        // When
        val response = userBackendService.createUser("appId", identities, subscriptions, properties)

        // Then
        response.identities["onesignal_id"] shouldBe osId
        response.properties.timezoneId shouldBe "testTimeZone"
        response.properties.language shouldBe "testLanguage"
        response.subscriptions.count() shouldBe 1
        response.subscriptions[0].id shouldBe "subscriptionId1"
        response.subscriptions[0].type shouldBe SubscriptionObjectType.ANDROID_PUSH

        coVerify {
            spyHttpClient.post(
                "apps/appId/users",
                withArg {
                    it.has("identity") shouldBe false
                    it.has("properties") shouldBe true
                    it.has("subscriptions") shouldBe true
                    it.getJSONArray("subscriptions").length() shouldBe 1
                    it.getJSONArray("subscriptions").getJSONObject(0).has("type") shouldBe true
                    it.getJSONArray("subscriptions").getJSONObject(0).getString("type") shouldBe "AndroidPush"
                },
            )
        }
    }

    test("update user tags") {
        // Given
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.patch(any(), any()) } returns HttpResponse(202, "{properties: { tags: {tagKey1: tagValue1}}}")
        val userBackendService = UserBackendService(spyHttpClient)
        val properties = PropertiesObject(tags = mapOf("tagkey1" to "tagValue1"))
        val propertiesDelta = PropertiesDeltasObject()

        // When
        userBackendService.updateUser("appId", aliasLabel, aliasValue, properties, refreshDeviceMetadata = true, propertiesDelta)

        // Then
        coVerify {
            spyHttpClient.patch(
                "apps/appId/users/by/$aliasLabel/$aliasValue",
                withArg {
                    it.has("properties") shouldBe true
                    it.getJSONObject("properties").has("tags") shouldBe true
                    it.getJSONObject("properties").getJSONObject("tags").has("tagkey1") shouldBe true
                    it.getJSONObject("properties").getJSONObject("tags").getString("tagkey1") shouldBe "tagValue1"
                },
            )
        }
    }

    test("update user language") {
        // Given
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.patch(any(), any()) } returns HttpResponse(202, "{properties: { tags: {tagKey1: tagValue1}}}")
        val userBackendService = UserBackendService(spyHttpClient)
        val properties = PropertiesObject(language = "newLanguage")
        val propertiesDelta = PropertiesDeltasObject()

        // When
        userBackendService.updateUser("appId", aliasLabel, aliasValue, properties, refreshDeviceMetadata = true, propertiesDelta)

        // Then
        coVerify {
            spyHttpClient.patch(
                "apps/appId/users/by/$aliasLabel/$aliasValue",
                withArg {
                    it.has("properties") shouldBe true
                    it.getJSONObject("properties").has("language") shouldBe true
                    it.getJSONObject("properties").getString("language") shouldBe "newLanguage"
                },
            )
        }
    }

    test("update user timezone") {
        // Given
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.patch(any(), any()) } returns HttpResponse(202, "{properties: { timezone_id: \"America/New_York\"}}")
        val userBackendService = UserBackendService(spyHttpClient)
        val properties = PropertiesObject(timezoneId = "America/New_York")
        val propertiesDelta = PropertiesDeltasObject()

        // When
        userBackendService.updateUser("appId", aliasLabel, aliasValue, properties, refreshDeviceMetadata = true, propertiesDelta)

        // Then
        coVerify {
            spyHttpClient.patch(
                "apps/appId/users/by/$aliasLabel/$aliasValue",
                withArg {
                    it.has("properties") shouldBe true
                    it.getJSONObject("properties").has("timezone_id") shouldBe true
                    it.getJSONObject("properties").getString("timezone_id") shouldBe "America/New_York"
                },
            )
        }
    }

    test("update user country") {
        // Given
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.patch(any(), any()) } returns HttpResponse(202, "{properties: { country: \"TV\"}}")
        val userBackendService = UserBackendService(spyHttpClient)
        val properties = PropertiesObject(country = "TV")
        val propertiesDelta = PropertiesDeltasObject()

        // When
        userBackendService.updateUser("appId", aliasLabel, aliasValue, properties, refreshDeviceMetadata = true, propertiesDelta)

        // Then
        coVerify {
            spyHttpClient.patch(
                "apps/appId/users/by/$aliasLabel/$aliasValue",
                withArg {
                    it.has("properties") shouldBe true
                    it.getJSONObject("properties").has("country") shouldBe true
                    it.getJSONObject("properties").getString("country") shouldBe "TV"
                },
            )
        }
    }

    test("update user location") {
        // Given
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.patch(any(), any()) } returns HttpResponse(202, "{properties: { lat: 12.34, long: 45.67}}")
        val userBackendService = UserBackendService(spyHttpClient)
        val properties = PropertiesObject(latitude = 12.34, longitude = 45.67)
        val propertiesDelta = PropertiesDeltasObject()

        // When
        userBackendService.updateUser("appId", aliasLabel, aliasValue, properties, refreshDeviceMetadata = true, propertiesDelta)

        // Then
        coVerify {
            spyHttpClient.patch(
                "apps/appId/users/by/$aliasLabel/$aliasValue",
                withArg {
                    it.has("properties") shouldBe true
                    it.getJSONObject("properties").has("lat") shouldBe true
                    it.getJSONObject("properties").getDouble("lat") shouldBe 12.34
                    it.getJSONObject("properties").has("long") shouldBe true
                    it.getJSONObject("properties").getDouble("long") shouldBe 45.67
                },
            )
        }
    }

    test("update user with refresh metadata") {
        // Given
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.patch(any(), any()) } returns HttpResponse(202, "{properties: {} }")
        val userBackendService = UserBackendService(spyHttpClient)
        val properties = PropertiesObject(tags = mapOf("tagkey1" to "tagValue1"))
        val propertiesDelta = PropertiesDeltasObject()

        // When
        userBackendService.updateUser("appId", aliasLabel, aliasValue, properties, refreshDeviceMetadata = true, propertiesDelta)

        // Then
        coVerify {
            spyHttpClient.patch(
                "apps/appId/users/by/$aliasLabel/$aliasValue",
                withArg {
                    it.getBoolean("refresh_device_metadata") shouldBe true
                },
            )
        }
    }

    test("update user without refresh metadata") {
        // Given
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.patch(any(), any()) } returns HttpResponse(202, "{properties: {} }")
        val userBackendService = UserBackendService(spyHttpClient)
        val properties = PropertiesObject(tags = mapOf("tagkey1" to "tagValue1"))
        val propertiesDelta = PropertiesDeltasObject()

        // When
        userBackendService.updateUser("appId", aliasLabel, aliasValue, properties, refreshDeviceMetadata = false, propertiesDelta)

        // Then
        coVerify {
            spyHttpClient.patch(
                "apps/appId/users/by/$aliasLabel/$aliasValue",
                withArg {
                    it.getBoolean("refresh_device_metadata") shouldBe false
                },
            )
        }
    }

    test("update user delta session") {
        // Given
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.patch(any(), any()) } returns HttpResponse(202, "{properties: { }}")
        val userBackendService = UserBackendService(spyHttpClient)
        val properties = PropertiesObject()
        val propertiesDelta = PropertiesDeltasObject(sessionTime = 1111, sessionCount = 1)

        // When
        userBackendService.updateUser("appId", aliasLabel, aliasValue, properties, refreshDeviceMetadata = true, propertiesDelta)

        // Then
        coVerify {
            spyHttpClient.patch(
                "apps/appId/users/by/$aliasLabel/$aliasValue",
                withArg {
                    it.has("deltas") shouldBe true
                    it.getJSONObject("deltas").has("session_time") shouldBe true
                    it.getJSONObject("deltas").getLong("session_time") shouldBe 1111
                    it.getJSONObject("deltas").has("session_count") shouldBe true
                    it.getJSONObject("deltas").getInt("session_count") shouldBe 1
                },
            )
        }
    }

    test("update user delta purchase") {
        // Given
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.patch(any(), any()) } returns HttpResponse(202, "{properties: { }}")
        val userBackendService = UserBackendService(spyHttpClient)
        val properties = PropertiesObject()
        val propertiesDelta =
            PropertiesDeltasObject(
                amountSpent = BigDecimal(1111),
                purchases =
                listOf(
                    PurchaseObject("sku1", "iso1", BigDecimal(2222)),
                    PurchaseObject("sku2", "iso2", BigDecimal(4444)),
                ),
            )

        // When
        userBackendService.updateUser("appId", aliasLabel, aliasValue, properties, refreshDeviceMetadata = true, propertiesDelta)

        // Then
        coVerify {
            spyHttpClient.patch(
                "apps/appId/users/by/$aliasLabel/$aliasValue",
                withArg {
                    it.has("deltas") shouldBe true
                    it.getJSONObject("deltas").has("amount_spent") shouldBe true
                    it.getJSONObject("deltas").getDouble("amount_spent") shouldBe 1111
                    it.getJSONObject("deltas").has("purchases") shouldBe true
                    it.getJSONObject("deltas").getJSONArray("purchases").length() shouldBe 2
                    it.getJSONObject("deltas").getJSONArray("purchases").getJSONObject(0).has("sku") shouldBe true
                    it.getJSONObject("deltas").getJSONArray("purchases").getJSONObject(0).getString("sku") shouldBe "sku1"
                    it.getJSONObject("deltas").getJSONArray("purchases").getJSONObject(0).has("iso") shouldBe true
                    it.getJSONObject("deltas").getJSONArray("purchases").getJSONObject(0).getString("iso") shouldBe "iso1"
                    it.getJSONObject("deltas").getJSONArray("purchases").getJSONObject(0).has("amount") shouldBe true
                    it.getJSONObject("deltas").getJSONArray("purchases").getJSONObject(0).getDouble("amount") shouldBe 2222
                    it.getJSONObject("deltas").getJSONArray("purchases").getJSONObject(1).has("sku") shouldBe true
                    it.getJSONObject("deltas").getJSONArray("purchases").getJSONObject(1).getString("sku") shouldBe "sku2"
                    it.getJSONObject("deltas").getJSONArray("purchases").getJSONObject(1).has("iso") shouldBe true
                    it.getJSONObject("deltas").getJSONArray("purchases").getJSONObject(1).getString("iso") shouldBe "iso2"
                    it.getJSONObject("deltas").getJSONArray("purchases").getJSONObject(1).has("amount") shouldBe true
                    it.getJSONObject("deltas").getJSONArray("purchases").getJSONObject(1).getDouble("amount") shouldBe 4444
                },
            )
        }
    }

    test("update user but user not found throws exception") {
        // Given
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.patch(any(), any()) } returns HttpResponse(404, "NOT FOUND")
        val userBackendService = UserBackendService(spyHttpClient)
        val properties = PropertiesObject(tags = mapOf("tagkey1" to "tagValue1"))
        val propertiesDelta = PropertiesDeltasObject()

        // When
        val exception =
            shouldThrowUnit<BackendException> {
                userBackendService.updateUser("appId", aliasLabel, aliasValue, properties, refreshDeviceMetadata = true, propertiesDelta)
            }

        // Then
        exception.statusCode shouldBe 404
        exception.response shouldBe "NOT FOUND"
    }

    test("update user but other error throws exception") {
        // Given
        val aliasLabel = "onesignal_id"
        val aliasValue = "11111111-1111-1111-1111-111111111111"
        val spyHttpClient = mockk<IHttpClient>()
        coEvery { spyHttpClient.patch(any(), any()) } returns HttpResponse(403, "FORBIDDEN")
        val userBackendService = UserBackendService(spyHttpClient)
        val properties = PropertiesObject(tags = mapOf("tagkey1" to "tagValue1"))
        val propertiesDelta = PropertiesDeltasObject()

        // When
        val exception =
            shouldThrowUnit<BackendException> {
                userBackendService.updateUser("appId", aliasLabel, aliasValue, properties, refreshDeviceMetadata = true, propertiesDelta)
            }

        // Then
        exception.statusCode shouldBe 403
        exception.response shouldBe "FORBIDDEN"
    }
})
