package com.onesignal.inAppMessages.internal

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessagingHelpers.Companion.buildTestMessageWithRedisplay
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.UUID


class InAppMessagesTests : FunSpec({
    val IAM_CLICK_ID = "button_id_123"
    val LIMIT = 5
    val DELAY: Long = 60

//  Define message at class level with lazy initialization
    val message: InAppMessagingHelpers.OSTestInAppMessageInternal by lazy {
        InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
            Trigger.OSTriggerKind.SESSION_TIME,
            null,
            Trigger.OSTriggerOperator.GREATER_THAN_OR_EQUAL_TO.toString(),
            3
        )
    }

    beforeAny {
        Logging.logLevel = LogLevel.NONE
        // TODO: add more from Player Model @BeforeClass in InAppMessagingUnitTests.java
    }

    beforeTest {
        // TODO: add more from Player Model @BeforeTest in InAppMessagingUnitTests.java
    }

    afterTest {
        // TODO: reset back to default: clear timers and clean up helpers
    }

    test("testBuiltMessage") {
        // Given
        val messageId = message.messageId
        val variants = message.variants

        // Then
        UUID.fromString(messageId) // Throws if invalid
        variants shouldNotBe null
    }

    test("testBuiltMessageVariants") {
        message.variants["android"]?.get("es") shouldBe InAppMessagingHelpers.TEST_SPANISH_ANDROID_VARIANT_ID
        message.variants["android"]?.get("en") shouldBe InAppMessagingHelpers.TEST_ENGLISH_ANDROID_VARIANT_ID
    }

    test("testBuiltMessageReDisplay") {
        // Given
        val message = buildTestMessageWithRedisplay(LIMIT, DELAY)

        // Then
        message.redisplayStats.isRedisplayEnabled shouldBe true
        message.redisplayStats.displayLimit shouldBe LIMIT
        message.redisplayStats.displayDelay shouldBe DELAY
        message.redisplayStats.lastDisplayTime shouldBe -1
        message.redisplayStats.displayQuantity shouldBe 0

        // When
        val messageWithoutDisplay: InAppMessagingHelpers.OSTestInAppMessageInternal =
            InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                Trigger.OSTriggerKind.SESSION_TIME,
                null,
                Trigger.OSTriggerOperator.GREATER_THAN_OR_EQUAL_TO.toString(),
                3
            )

        // Then
        messageWithoutDisplay.redisplayStats.isRedisplayEnabled shouldBe false
        messageWithoutDisplay.redisplayStats.displayLimit shouldBe 1
        messageWithoutDisplay.redisplayStats.displayDelay shouldBe 0
        messageWithoutDisplay.redisplayStats.lastDisplayTime shouldBe -1
        messageWithoutDisplay.redisplayStats.displayQuantity shouldBe 0
    }

    test("testBuiltMessageRedisplayLimit") {
        val message: InAppMessagingHelpers.OSTestInAppMessageInternal =
            buildTestMessageWithRedisplay(
                LIMIT,
                DELAY
            )
        for (i in 0 until LIMIT) {
            message.redisplayStats.shouldDisplayAgain() shouldBe true
            message.redisplayStats.incrementDisplayQuantity()
        }
        message.redisplayStats.incrementDisplayQuantity()
        message.redisplayStats.shouldDisplayAgain() shouldBe false
    }


    test("testBuiltMessageRedisplayDelay") {
        // TODO
    }

    test("testBuiltMessageRedisplayCLickId") {
        val message: InAppMessagingHelpers.OSTestInAppMessageInternal =
            buildTestMessageWithRedisplay(
                LIMIT,
                DELAY
            )

        message.clickedClickIds.isEmpty() shouldBe true
        message.isClickAvailable(IAM_CLICK_ID)

        message.addClickId(IAM_CLICK_ID)
        message.clearClickIds()

        message.clickedClickIds.isEmpty() shouldBe true

        message.addClickId(IAM_CLICK_ID)
        message.addClickId(IAM_CLICK_ID)
        message.clickedClickIds.size shouldBe 1

        message.isClickAvailable(IAM_CLICK_ID) shouldBe false

        val messageWithoutDisplay2: InAppMessagingHelpers.OSTestInAppMessageInternal =
            InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                Trigger.OSTriggerKind.SESSION_TIME,
                null,
                Trigger.OSTriggerOperator.GREATER_THAN_OR_EQUAL_TO.toString(),
                3
            )

        messageWithoutDisplay2.addClickId(IAM_CLICK_ID)
        messageWithoutDisplay2.isClickAvailable(IAM_CLICK_ID) shouldBe false
    }
})
