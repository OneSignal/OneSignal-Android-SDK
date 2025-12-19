package com.onesignal.inAppMessages.internal.triggers.impl

import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessage
import com.onesignal.inAppMessages.internal.Trigger
import com.onesignal.inAppMessages.internal.state.InAppStateService
import com.onesignal.inAppMessages.internal.triggers.ITriggerHandler
import com.onesignal.inAppMessages.internal.triggers.TriggerModel
import com.onesignal.inAppMessages.internal.triggers.TriggerModelStore
import com.onesignal.mocks.MockHelper
import com.onesignal.session.internal.session.ISessionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.json.JSONArray
import org.json.JSONObject

private class Mocks {
    val triggerModelStore = mockk<TriggerModelStore>(relaxed = true)
    val inAppStateService = mockk<InAppStateService>(relaxed = true)
    val sessionService = mockk<ISessionService>(relaxed = true)
    val time = MockHelper.time(100)
    val dynamicTriggerController = DynamicTriggerController(inAppStateService, sessionService, time)

    val triggerController = TriggerController(triggerModelStore, dynamicTriggerController)

    fun createTrigger(
        id: String = "trigger-id",
        kind: Trigger.OSTriggerKind = Trigger.OSTriggerKind.CUSTOM,
        property: String? = "property-key",
        operator: Trigger.OSTriggerOperator = Trigger.OSTriggerOperator.EQUAL_TO,
        value: Any? = "value",
    ): Trigger {
        val json = JSONObject()
        json.put("id", id)
        json.put("kind", kind.toString())
        if (property != null) {
            json.put("property", property)
        }
        json.put("operator", operator.toString())
        if (value != null) {
            json.put("value", value)
        }
        return Trigger(json)
    }

    fun createInAppMessage(
        messageId: String = "message-id",
        triggers: List<List<Trigger>> = emptyList(),
    ): InAppMessage {
        val json = JSONObject()
        json.put("id", messageId)
        json.put("variants", JSONObject().apply {
            put("all", JSONObject().apply {
                put("en", "variant-id")
            })
        })
        val triggersJson = JSONArray()
        triggers.forEach { andConditions ->
            val andConditionsJson = JSONArray()
            andConditions.forEach { trigger ->
                andConditionsJson.put(trigger.toJSONObject())
            }
            triggersJson.put(andConditionsJson)
        }
        json.put("triggers", triggersJson)
        return InAppMessage(json, time)
    }

    fun createTriggerModel(key: String, value: Any): TriggerModel {
        val model = TriggerModel()
        model.key = key
        model.value = value
        return model
    }
}

class TriggerControllerTests : FunSpec({
    lateinit var mocks: Mocks

    beforeAny {
        Logging.logLevel = LogLevel.NONE
        mocks = Mocks()
    }

    context("evaluateMessageTriggers") {
        test("returns true when message has no triggers") {
            // Given
            val message = mocks.createInAppMessage(triggers = emptyList())

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns true when single AND condition is satisfied") {
            // Given
            val trigger1 = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = "value1",
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger1)))
            mocks.triggerController.triggers["key1"] = "value1"

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns false when single AND condition is not satisfied") {
            // Given
            val trigger1 = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = "value1",
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger1)))
            mocks.triggerController.triggers["key1"] = "different-value"

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }

        test("returns true when all triggers in AND condition are satisfied") {
            // Given
            val trigger1 = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = "value1",
            )
            val trigger2 = mocks.createTrigger(
                id = "trigger2",
                property = "key2",
                value = 10,
                operator = Trigger.OSTriggerOperator.GREATER_THAN,
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger1, trigger2)))
            mocks.triggerController.triggers["key1"] = "value1"
            mocks.triggerController.triggers["key2"] = 20

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns false when one trigger in AND condition is not satisfied") {
            // Given
            val trigger1 = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = "value1",
            )
            val trigger2 = mocks.createTrigger(
                id = "trigger2",
                property = "key2",
                value = 10,
                operator = Trigger.OSTriggerOperator.GREATER_THAN,
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger1, trigger2)))
            mocks.triggerController.triggers["key1"] = "value1"
            mocks.triggerController.triggers["key2"] = 5 // Less than 10

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }

        test("returns true when first OR condition is satisfied") {
            // Given
            val trigger1 = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = "value1",
            )
            val trigger2 = mocks.createTrigger(
                id = "trigger2",
                property = "key2",
                value = "value2",
            )
            val message = mocks.createInAppMessage(
                triggers = listOf(
                    listOf(trigger1),
                    listOf(trigger2),
                ),
            )
            mocks.triggerController.triggers["key1"] = "value1"
            // key2 is not set, so second OR condition fails

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns true when second OR condition is satisfied") {
            // Given
            val trigger1 = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = "value1",
            )
            val trigger2 = mocks.createTrigger(
                id = "trigger2",
                property = "key2",
                value = "value2",
            )
            val message = mocks.createInAppMessage(
                triggers = listOf(
                    listOf(trigger1),
                    listOf(trigger2),
                ),
            )
            // key1 is not set, so first OR condition fails
            mocks.triggerController.triggers["key2"] = "value2"

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns false when all OR conditions fail") {
            // Given
            val trigger1 = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = "value1",
            )
            val trigger2 = mocks.createTrigger(
                id = "trigger2",
                property = "key2",
                value = "value2",
            )
            val message = mocks.createInAppMessage(
                triggers = listOf(
                    listOf(trigger1),
                    listOf(trigger2),
                ),
            )
            mocks.triggerController.triggers["key1"] = "different-value"
            mocks.triggerController.triggers["key2"] = "different-value"

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }

        test("returns false for UNKNOWN trigger kind") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                kind = Trigger.OSTriggerKind.UNKNOWN,
                property = "key1",
                value = "value1",
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))
            mocks.triggerController.triggers["key1"] = "value1"

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }

        test("delegates to dynamicTriggerController for non-CUSTOM triggers") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                kind = Trigger.OSTriggerKind.SESSION_TIME,
                property = null,
                value = 100, // 100 seconds
                operator = Trigger.OSTriggerOperator.GREATER_THAN,
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))
            // Set up dependencies so dynamicTriggerShouldFire returns true
            // Required time: 100 * 1000 = 100000ms
            // Current session time should be > 100000ms (200000ms = 200 seconds)
            val mockSessionService = mockk<ISessionService>(relaxed = true)
            val mockTime = MockHelper.time(200000) // 200 seconds > 100 seconds
            every { mockSessionService.startTime } returns 0L
            val dynamicController = DynamicTriggerController(mocks.inAppStateService, mockSessionService, mockTime)
            val triggerController = TriggerController(mocks.triggerModelStore, dynamicController)

            // When
            val result = triggerController.evaluateMessageTriggers(message)

            // Then
            // The result should be true because session time (200s) > required time (100s)
            // This verifies that TriggerController delegates to DynamicTriggerController
            result shouldBe true
        }
    }

    context("evaluateTrigger - Custom triggers") {
        test("returns true for EXISTS operator when key exists") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                operator = Trigger.OSTriggerOperator.EXISTS,
                value = null,
            )
            mocks.triggerController.triggers["key1"] = "any-value"
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns false for EXISTS operator when key does not exist") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                operator = Trigger.OSTriggerOperator.EXISTS,
                value = null,
            )
            // key1 is not in triggers
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }

        test("returns true for NOT_EXISTS operator when key does not exist") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                operator = Trigger.OSTriggerOperator.NOT_EXISTS,
                value = null,
            )
            // key1 is not in triggers
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns false for NOT_EXISTS operator when key exists") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                operator = Trigger.OSTriggerOperator.NOT_EXISTS,
                value = null,
            )
            mocks.triggerController.triggers["key1"] = "any-value"
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }

        test("returns false for NOT_EQUAL_TO operator when key does not exist") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                operator = Trigger.OSTriggerOperator.NOT_EQUAL_TO,
                value = null,
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }

        test("returns true for NOT_EQUAL_TO operator when key exists with different value") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                operator = Trigger.OSTriggerOperator.NOT_EQUAL_TO,
                value = "value1",
            )
            mocks.triggerController.triggers["key1"] = "value2"
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns false for NOT_EQUAL_TO operator when key exists with same value") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                operator = Trigger.OSTriggerOperator.NOT_EQUAL_TO,
                value = "value1",
            )
            mocks.triggerController.triggers["key1"] = "value1"
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }

        test("returns true for CONTAINS operator when collection contains value") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                operator = Trigger.OSTriggerOperator.CONTAINS,
                value = "item1",
            )
            mocks.triggerController.triggers["key1"] = listOf("item1", "item2", "item3")
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns false for CONTAINS operator when collection does not contain value") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                operator = Trigger.OSTriggerOperator.CONTAINS,
                value = "item4",
            )
            mocks.triggerController.triggers["key1"] = listOf("item1", "item2", "item3")
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }

        test("returns false for CONTAINS operator when value is not a collection") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                operator = Trigger.OSTriggerOperator.CONTAINS,
                value = "item1",
            )
            mocks.triggerController.triggers["key1"] = "not-a-collection"
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }
    }

    context("evaluateTrigger - String operators") {
        test("returns true for EQUAL_TO operator with matching strings") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = "value1",
                operator = Trigger.OSTriggerOperator.EQUAL_TO,
            )
            mocks.triggerController.triggers["key1"] = "value1"
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns false for EQUAL_TO operator with non-matching strings") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = "value1",
                operator = Trigger.OSTriggerOperator.EQUAL_TO,
            )
            mocks.triggerController.triggers["key1"] = "different-value"
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }

        test("returns true for NOT_EQUAL_TO operator with different strings") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = "value1",
                operator = Trigger.OSTriggerOperator.NOT_EQUAL_TO,
            )
            mocks.triggerController.triggers["key1"] = "different-value"
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns false for NOT_EQUAL_TO operator with matching strings") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = "value1",
                operator = Trigger.OSTriggerOperator.NOT_EQUAL_TO,
            )
            mocks.triggerController.triggers["key1"] = "value1"
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }
    }

    context("evaluateTrigger - Numeric operators") {
        test("returns true for EQUAL_TO operator with matching numbers") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = 10,
                operator = Trigger.OSTriggerOperator.EQUAL_TO,
            )
            mocks.triggerController.triggers["key1"] = 10
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns true for GREATER_THAN operator when device value is greater") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = 10,
                operator = Trigger.OSTriggerOperator.GREATER_THAN,
            )
            mocks.triggerController.triggers["key1"] = 20
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns false for GREATER_THAN operator when device value is less") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = 10,
                operator = Trigger.OSTriggerOperator.GREATER_THAN,
            )
            mocks.triggerController.triggers["key1"] = 5
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }

        test("returns true for LESS_THAN operator when device value is less") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = 10,
                operator = Trigger.OSTriggerOperator.LESS_THAN,
            )
            mocks.triggerController.triggers["key1"] = 5
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns false for LESS_THAN operator when device value is greater") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = 10,
                operator = Trigger.OSTriggerOperator.LESS_THAN,
            )
            mocks.triggerController.triggers["key1"] = 20
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }

        test("returns true for GREATER_THAN_OR_EQUAL_TO operator when device value is greater") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = 10,
                operator = Trigger.OSTriggerOperator.GREATER_THAN_OR_EQUAL_TO,
            )
            mocks.triggerController.triggers["key1"] = 20
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns true for GREATER_THAN_OR_EQUAL_TO operator when device value is equal") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = 10,
                operator = Trigger.OSTriggerOperator.GREATER_THAN_OR_EQUAL_TO,
            )
            mocks.triggerController.triggers["key1"] = 10
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns true for LESS_THAN_OR_EQUAL_TO operator when device value is less") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = 10,
                operator = Trigger.OSTriggerOperator.LESS_THAN_OR_EQUAL_TO,
            )
            mocks.triggerController.triggers["key1"] = 5
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns true for LESS_THAN_OR_EQUAL_TO operator when device value is equal") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = 10,
                operator = Trigger.OSTriggerOperator.LESS_THAN_OR_EQUAL_TO,
            )
            mocks.triggerController.triggers["key1"] = 10
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }
    }

    context("evaluateTrigger - Flex matching") {
        test("returns true for EQUAL_TO with number and string conversion") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = "5",
                operator = Trigger.OSTriggerOperator.EQUAL_TO,
            )
            mocks.triggerController.triggers["key1"] = 5
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns true for EQUAL_TO with string and number conversion") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = 5,
                operator = Trigger.OSTriggerOperator.EQUAL_TO,
            )
            mocks.triggerController.triggers["key1"] = "5"
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns true for NOT_EQUAL_TO with number and string conversion") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = "5",
                operator = Trigger.OSTriggerOperator.NOT_EQUAL_TO,
            )
            mocks.triggerController.triggers["key1"] = 10
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns true for numeric comparison with string device value") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = 10,
                operator = Trigger.OSTriggerOperator.GREATER_THAN,
            )
            mocks.triggerController.triggers["key1"] = "20"
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns false for numeric comparison with invalid string device value") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
                value = 10,
                operator = Trigger.OSTriggerOperator.GREATER_THAN,
            )
            mocks.triggerController.triggers["key1"] = "not-a-number"
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.evaluateMessageTriggers(message)

            // Then
            result shouldBe false
        }
    }

    context("isTriggerOnMessage") {
        test("returns true when message contains trigger key by property") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.isTriggerOnMessage(message, listOf("key1"))

            // Then
            result shouldBe true
        }

        test("returns true when message contains trigger key by triggerId") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger-id-1",
                property = "key1",
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.isTriggerOnMessage(message, listOf("trigger-id-1"))

            // Then
            result shouldBe true
        }

        test("returns false when message does not contain trigger key") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.isTriggerOnMessage(message, listOf("different-key"))

            // Then
            result shouldBe false
        }

        test("returns false when message has null triggers") {
            // Given
            val message = mocks.createInAppMessage(triggers = emptyList())
            // Manually set triggers to null using reflection or create a message without triggers
            // For now, we'll test with empty triggers which should return false

            // When
            val result = mocks.triggerController.isTriggerOnMessage(message, listOf("key1"))

            // Then
            result shouldBe false
        }

        test("returns true when any of multiple trigger keys match") {
            // Given
            val trigger1 = mocks.createTrigger(
                id = "trigger1",
                property = "key1",
            )
            val trigger2 = mocks.createTrigger(
                id = "trigger2",
                property = "key2",
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger1, trigger2)))

            // When
            val result = mocks.triggerController.isTriggerOnMessage(message, listOf("key3", "key1"))

            // Then
            result shouldBe true
        }
    }

    context("messageHasOnlyDynamicTriggers") {
        test("returns false when message has no triggers") {
            // Given
            val message = mocks.createInAppMessage(triggers = emptyList())

            // When
            val result = mocks.triggerController.messageHasOnlyDynamicTriggers(message)

            // Then
            result shouldBe false
        }

        test("returns true when message has only SESSION_TIME triggers") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                kind = Trigger.OSTriggerKind.SESSION_TIME,
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.messageHasOnlyDynamicTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns true when message has only TIME_SINCE_LAST_IN_APP triggers") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                kind = Trigger.OSTriggerKind.TIME_SINCE_LAST_IN_APP,
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.messageHasOnlyDynamicTriggers(message)

            // Then
            result shouldBe true
        }

        test("returns false when message has CUSTOM trigger") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                kind = Trigger.OSTriggerKind.CUSTOM,
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.messageHasOnlyDynamicTriggers(message)

            // Then
            result shouldBe false
        }

        test("returns false when message has UNKNOWN trigger") {
            // Given
            val trigger = mocks.createTrigger(
                id = "trigger1",
                kind = Trigger.OSTriggerKind.UNKNOWN,
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(trigger)))

            // When
            val result = mocks.triggerController.messageHasOnlyDynamicTriggers(message)

            // Then
            result shouldBe false
        }

        test("returns false when message has mixed dynamic and custom triggers") {
            // Given
            val dynamicTrigger = mocks.createTrigger(
                id = "trigger1",
                kind = Trigger.OSTriggerKind.SESSION_TIME,
            )
            val customTrigger = mocks.createTrigger(
                id = "trigger2",
                kind = Trigger.OSTriggerKind.CUSTOM,
            )
            val message = mocks.createInAppMessage(triggers = listOf(listOf(dynamicTrigger, customTrigger)))

            // When
            val result = mocks.triggerController.messageHasOnlyDynamicTriggers(message)

            // Then
            result shouldBe false
        }
    }

    context("Model store change handlers") {
        test("onModelAdded adds trigger and fires event") {
            // Given
            val model = mocks.createTriggerModel("key1", "value1")
            val mockHandler = mockk<ITriggerHandler>(relaxed = true)
            mocks.triggerController.subscribe(mockHandler)

            // When
            mocks.triggerController.onModelAdded(model, "tag")

            // Then
            mocks.triggerController.triggers["key1"] shouldBe "value1"
            verify { mockHandler.onTriggerChanged("key1") }
        }

        test("onModelUpdated updates trigger and fires event") {
            // Given
            val model = mocks.createTriggerModel("key1", "new-value")
            val args = ModelChangedArgs(model, "path", "property", "old-value", "new-value")
            val mockHandler = mockk<ITriggerHandler>(relaxed = true)
            mocks.triggerController.subscribe(mockHandler)

            // When
            mocks.triggerController.onModelUpdated(args, "tag")

            // Then
            mocks.triggerController.triggers["key1"] shouldBe "new-value"
            verify { mockHandler.onTriggerChanged("key1") }
        }

        test("onModelRemoved removes trigger") {
            // Given
            val model = mocks.createTriggerModel("key1", "value1")
            mocks.triggerController.triggers["key1"] = "value1"

            // When
            mocks.triggerController.onModelRemoved(model, "tag")

            // Then
            mocks.triggerController.triggers.containsKey("key1") shouldBe false
        }
    }

    context("Event subscription") {
        test("subscribe adds handler to dynamicTriggerController") {
            // Given
            val mockHandler = mockk<ITriggerHandler>(relaxed = true)
            val mockDynamicController = spyk(mocks.dynamicTriggerController)
            val triggerController = TriggerController(mocks.triggerModelStore, mockDynamicController)

            // When
            triggerController.subscribe(mockHandler)

            // Then
            verify { mockDynamicController.subscribe(mockHandler) }
        }

        test("unsubscribe removes handler from dynamicTriggerController") {
            // Given
            val mockHandler = mockk<ITriggerHandler>(relaxed = true)
            val mockDynamicController = spyk(mocks.dynamicTriggerController)
            val triggerController = TriggerController(mocks.triggerModelStore, mockDynamicController)

            // When
            triggerController.unsubscribe(mockHandler)

            // Then
            verify { mockDynamicController.unsubscribe(mockHandler) }
        }

        test("hasSubscribers delegates to dynamicTriggerController") {
            // Given
            val mockHandler = mockk<ITriggerHandler>(relaxed = true)
            val mockDynamicController = spyk(mocks.dynamicTriggerController)
            every { mockDynamicController.hasSubscribers } returns true
            val triggerController = TriggerController(mocks.triggerModelStore, mockDynamicController)

            // When
            val result = triggerController.hasSubscribers

            // Then
            result shouldBe true
        }
    }
})

