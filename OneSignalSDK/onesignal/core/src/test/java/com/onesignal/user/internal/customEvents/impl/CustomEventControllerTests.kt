package com.onesignal.user.internal.customEvents.impl

import com.onesignal.common.JSONUtils
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.operations.TrackCustomEventOperation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.json.JSONObject

class CustomEventControllerTests : FunSpec({
    test("should create and enqueue TrackCustomEventOperation with all fields") {
        // Given
        val appId = "test-app-id"
        val onesignalId = "test-onesignal-id"
        val externalId = "test-external-id"
        val timestamp = 1234567890L
        val eventName = "test-event"
        val properties = mapOf(
            "key1" to "value1",
            "key2" to 42,
        )

        val configModelStore = MockHelper.configModelStore {
            it.appId = appId
        }
        val identityModelStore = MockHelper.identityModelStore {
            it.onesignalId = onesignalId
            it.externalId = externalId
        }
        val time = MockHelper.time(timestamp)
        val opRepo = mockk<IOperationRepo>(relaxed = true)
        every { opRepo.enqueue(any()) } just runs

        val controller = CustomEventController(
            identityModelStore,
            configModelStore,
            time,
            opRepo,
        )

        // When
        controller.sendCustomEvent(eventName, properties)

        // Then
        val operationSlot = slot<TrackCustomEventOperation>()
        verify(exactly = 1) { opRepo.enqueue(capture(operationSlot)) }

        val operation = operationSlot.captured
        operation.appId shouldBe appId
        operation.onesignalId shouldBe onesignalId
        operation.externalId shouldBe externalId
        operation.timeStamp shouldBe timestamp
        operation.eventName shouldBe eventName
        operation.eventProperties shouldBe JSONUtils.mapToJson(properties).toString()
    }

    test("should handle null properties") {
        // Given
        val appId = "test-app-id"
        val onesignalId = "test-onesignal-id"
        val timestamp = 1234567890L
        val eventName = "test-event"

        val configModelStore = MockHelper.configModelStore {
            it.appId = appId
        }
        val identityModelStore = MockHelper.identityModelStore {
            it.onesignalId = onesignalId
        }
        val time = MockHelper.time(timestamp)
        val opRepo = mockk<IOperationRepo>(relaxed = true)
        every { opRepo.enqueue(any()) } just runs

        val controller = CustomEventController(
            identityModelStore,
            configModelStore,
            time,
            opRepo,
        )

        // When
        controller.sendCustomEvent(eventName, null)

        // Then
        val operationSlot = slot<TrackCustomEventOperation>()
        verify(exactly = 1) { opRepo.enqueue(capture(operationSlot)) }

        val operation = operationSlot.captured
        operation.appId shouldBe appId
        operation.onesignalId shouldBe onesignalId
        operation.timeStamp shouldBe timestamp
        operation.eventName shouldBe eventName
        operation.eventProperties shouldBe null
    }

    test("should convert properties with nested structures to JSON") {
        // Given
        val appId = "test-app-id"
        val onesignalId = "test-onesignal-id"
        val timestamp = 1234567890L
        val eventName = "test-event"
        val properties = mapOf(
            "someNum" to 123,
            "someFloat" to 3.14159,
            "someString" to "abc",
            "someBool" to true,
            "someObject" to mapOf(
                "abc" to "123",
                "nested" to mapOf(
                    "def" to "456",
                ),
                "ghi" to null,
            ),
            "someArray" to listOf(1, 2),
            "someMixedArray" to listOf(1, "2", mapOf("abc" to "123"), null),
            "someNull" to null,
        )

        val configModelStore = MockHelper.configModelStore {
            it.appId = appId
        }
        val identityModelStore = MockHelper.identityModelStore {
            it.onesignalId = onesignalId
        }
        val time = MockHelper.time(timestamp)
        val opRepo = mockk<IOperationRepo>(relaxed = true)
        every { opRepo.enqueue(any()) } just runs

        val controller = CustomEventController(
            identityModelStore,
            configModelStore,
            time,
            opRepo,
        )

        // When
        controller.sendCustomEvent(eventName, properties)

        // Then
        val operationSlot = slot<TrackCustomEventOperation>()
        verify(exactly = 1) { opRepo.enqueue(capture(operationSlot)) }

        val operation = operationSlot.captured
        val jsonProperties = JSONObject(operation.eventProperties!!)

        jsonProperties.getInt("someNum") shouldBe 123
        jsonProperties.getDouble("someFloat") shouldBe 3.14159
        jsonProperties.getString("someString") shouldBe "abc"
        jsonProperties.getBoolean("someBool") shouldBe true

        val someObject = jsonProperties.getJSONObject("someObject")
        someObject.getString("abc") shouldBe "123"
        val nested = someObject.getJSONObject("nested")
        nested.getString("def") shouldBe "456"
        someObject.isNull("ghi") shouldBe true

        val someArray = jsonProperties.getJSONArray("someArray")
        someArray.length() shouldBe 2
        someArray.getInt(0) shouldBe 1
        someArray.getInt(1) shouldBe 2

        val someMixedArray = jsonProperties.getJSONArray("someMixedArray")
        someMixedArray.length() shouldBe 4
        someMixedArray.getInt(0) shouldBe 1
        someMixedArray.getString(1) shouldBe "2"
        val arrayObj = someMixedArray.getJSONObject(2)
        arrayObj.getString("abc") shouldBe "123"
        someMixedArray.get(3) shouldBe JSONObject.NULL

        jsonProperties.isNull("someNull") shouldBe true
    }

    test("should handle empty properties map") {
        // Given
        val appId = "test-app-id"
        val onesignalId = "test-onesignal-id"
        val timestamp = 1234567890L
        val eventName = "test-event"
        val properties = emptyMap<String, Any?>()

        val configModelStore = MockHelper.configModelStore {
            it.appId = appId
        }
        val identityModelStore = MockHelper.identityModelStore {
            it.onesignalId = onesignalId
        }
        val time = MockHelper.time(timestamp)
        val opRepo = mockk<IOperationRepo>(relaxed = true)
        every { opRepo.enqueue(any()) } just runs

        val controller = CustomEventController(
            identityModelStore,
            configModelStore,
            time,
            opRepo,
        )

        // When
        controller.sendCustomEvent(eventName, properties)

        // Then
        val operationSlot = slot<TrackCustomEventOperation>()
        verify(exactly = 1) { opRepo.enqueue(capture(operationSlot)) }

        val operation = operationSlot.captured
        val jsonProperties = JSONObject(operation.eventProperties!!)
        jsonProperties.length() shouldBe 0
    }

    test("should use current timestamp from time service") {
        // Given
        val appId = "test-app-id"
        val onesignalId = "test-onesignal-id"
        val timestamp = 1000L
        val eventName = "test-event"

        val configModelStore = MockHelper.configModelStore {
            it.appId = appId
        }
        val identityModelStore = MockHelper.identityModelStore {
            it.onesignalId = onesignalId
        }
        val time = MockHelper.time(timestamp)
        val opRepo = mockk<IOperationRepo>(relaxed = true)
        every { opRepo.enqueue(any()) } just runs

        val controller = CustomEventController(
            identityModelStore,
            configModelStore,
            time,
            opRepo,
        )

        // When
        controller.sendCustomEvent(eventName, null)

        // Then
        val operationSlot = slot<TrackCustomEventOperation>()
        verify(exactly = 1) { opRepo.enqueue(capture(operationSlot)) }

        val operation = operationSlot.captured
        operation.timeStamp shouldBe timestamp
    }
})
