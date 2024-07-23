package com.onesignal.inAppMessages.internal

import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessagingHelpers.Companion.buildTestMessageWithRedisplay
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.UUID

class InAppMessagesTests : FunSpec({
    val iamClickId = "button_id_123"
    val limit = 5
    val delay: Long = 60

    fun setLocalTriggerValue(
        key: String,
        localValue: String,
    ) {
        if (localValue != null) {
            OneSignal.InAppMessages.addTrigger(
                key,
                localValue,
            )
        } else {
            OneSignal.InAppMessages.removeTrigger(key)
        }
    }

    fun comparativeOperatorTest(
        operator: Trigger.OSTriggerOperator,
        triggerValue: String,
        localValue: String,
    ): Boolean {
//        TODO
//        setLocalTriggerValue("test_property", localValue)
//        val testMessage: InAppMessagingHelpers.OSTestInAppMessageInternal =
//            InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
//                Trigger.OSTriggerKind.CUSTOM,
//                "test_property",
//                operator.toString(),
//                triggerValue
//            )
//
//        return InAppMessagingHelpers.evaluateMessage(testMessage)
        return true
    }

//  Define message at class level with lazy initialization
    val message: InAppMessagingHelpers.OSTestInAppMessageInternal by lazy {
        InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
            Trigger.OSTriggerKind.SESSION_TIME,
            null,
            Trigger.OSTriggerOperator.GREATER_THAN_OR_EQUAL_TO.toString(),
            3,
        )
    }

    beforeAny {
        Logging.logLevel = LogLevel.NONE
        // TODO: add more from Player Model @BeforeClass in InAppMessagingUnitTests.java
    }

    beforeTest {
        // TODO: add more from Player Model @BeforeTest in InAppMessagingUnitTests.java
        var iamLifecycleCounter = 0
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
        val message = buildTestMessageWithRedisplay(limit, delay)

        // Then
        message.redisplayStats.isRedisplayEnabled shouldBe true
        message.redisplayStats.displayLimit shouldBe limit
        message.redisplayStats.displayDelay shouldBe delay
        message.redisplayStats.lastDisplayTime shouldBe -1
        message.redisplayStats.displayQuantity shouldBe 0

        // When
        val messageWithoutDisplay: InAppMessagingHelpers.OSTestInAppMessageInternal =
            InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                Trigger.OSTriggerKind.SESSION_TIME,
                null,
                Trigger.OSTriggerOperator.GREATER_THAN_OR_EQUAL_TO.toString(),
                3,
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
                limit,
                delay,
            )
        for (i in 0 until limit) {
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
                limit,
                delay,
            )

        message.clickedClickIds.isEmpty() shouldBe true
        message.isClickAvailable(iamClickId)

        message.addClickId(iamClickId)
        message.clearClickIds()

        message.clickedClickIds.isEmpty() shouldBe true

        message.addClickId(iamClickId)
        message.addClickId(iamClickId)
        message.clickedClickIds.size shouldBe 1

        message.isClickAvailable(iamClickId) shouldBe false

        val messageWithoutDisplay2: InAppMessagingHelpers.OSTestInAppMessageInternal =
            InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                Trigger.OSTriggerKind.SESSION_TIME,
                null,
                Trigger.OSTriggerOperator.GREATER_THAN_OR_EQUAL_TO.toString(),
                3,
            )

        messageWithoutDisplay2.addClickId(iamClickId)
        messageWithoutDisplay2.isClickAvailable(iamClickId) shouldBe false
    }

    test("testBuiltMessageTrigger") {
        // TODO
    }

    test("testParsesMessageActions") {
        // TODO
    }

    test("testSaveMultipleTriggerValuesGetTrigger") {
        // TODO
        // Since trigger getter method no longer exists, will need to refactor
    }

    test("testSaveMultipleTriggerValues") {
        // TODO
    }

    // removed tests checking for non-string trigger values

    // create new test for ensuring only string trigger value
    test("testTriggerValuesAreStrings") {
        // TODO
    }

    test("testDeleteSavedTriggerValueGetTriggers") {
        // TODO
    }

    test("testDeleteSavedTriggerValue") {
        // TODO
    }

    test("testDeleteMultipleTriggers") {
        // TODO
    }

    test("testDeleteAllTriggers") {
        // TODO
    }

    test("testGreaterThanOperator") {
        // TODO
    }

    test("testGreaterThanOperatorWithString") {
        // TODO
    }

    // add more operator tests

    test("testMessageSchedulesSessionDurationTimer") {
        // TODO
    }

    // more trigger tests

    test("testOnMessageActionOccurredOnMessage") {
        // TODO:

        // add clickListener

        // val clickListener = object : IInAppMessageClickListener {
        // override fun onClick(event: IInAppMessageClickEvent) {
        // print(event.result.actionId)
        // }
        // }
        // OneSignal.InAppMessages.addClickListener(clickListener)

        // assertMainThread()
        // threadAndTaskWait()

        // call onMessageActionOccurredOnMessage

        // Ensure we make REST call to OneSignal to report click.

        // Ensure we fire public callback that In-App was clicked.
    }

    test("testOnMessageWasShown") {
        // TODO:
//        threadAndTaskWait()
//        InAppMessagingHelpers.onMessageWasDisplayed(message)
//
//        Compare Shadow Rest Client request
    }

    test("testOnPageChanged") {
        // TODO
    }

    // Tests for IAM Lifecycle
//    var iamLifecycleCounter = 0

    test("testIAMLifecycleEventsFlow") {

        // TODO
        // add listener and incremenet counter
//        val lifecycleListener = object : IInAppMessageLifecycleListener {
//            override fun onWillDisplay(event: IInAppMessageWillDisplayEvent) {
//                iamLifecycleCounter++
//            }
//
//            override fun onDidDisplay(event: IInAppMessageDidDisplayEvent) {
//                iamLifecycleCounter++
//            }
//
//            override fun onWillDismiss(event: IInAppMessageWillDismissEvent) {
//                iamLifecycleCounter++
//            }
//
//            override fun onDidDismiss(event: IInAppMessageDidDismissEvent) {
//                iamLifecycleCounter++
//            }
//        }
//        OneSignal.InAppMessages.addLifecycleListener(lifecycleListener)

//        threadAndTaskWait()
//        iamLifecycleCounter shouldBe  0
//        // maybe need threadAndTaskWait
//
//        InAppMessagingHelpers.onMessageWillDisplay(message)
//        iamLifecycleCounter shouldBe 1
//
//        InAppMessagingHelpers.onMessageWasDisplayed(message)
//        iamLifecycleCounter shouldBe 2
//
//        InAppMessagingHelpers.onMessageWillDismiss(message)
//        iamLifecycleCounter shouldBe 3
//
//        InAppMessagingHelpers.onMessageDidDismiss(message)
//        iamLifecycleCounter shouldBe 4
    }
})
