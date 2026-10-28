package com.onesignal.inAppMessages.internal.triggers.impl

import com.onesignal.inAppMessages.internal.Trigger
import com.onesignal.inAppMessages.internal.state.InAppStateService
import com.onesignal.mocks.MockHelper
import com.onesignal.session.internal.session.ISessionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.json.JSONObject

private fun timeSinceLastInAppTrigger(
    seconds: Number,
    operator: Trigger.OSTriggerOperator = Trigger.OSTriggerOperator.GREATER_THAN
): Trigger {
    val json = JSONObject()
    json.put("id", "trigger-id")
    json.put("kind", Trigger.OSTriggerKind.TIME_SINCE_LAST_IN_APP.toString())
    json.put("operator", operator.toString())
    json.put("value", seconds)
    return Trigger(json)
}

class DynamicTriggerControllerTests : FunSpec({
    val oneHourInSeconds = 3600

    test("time since last in-app trigger fires when no in-app message was dismissed yet") {
        // Given
        val state = InAppStateService()
        val controller = DynamicTriggerController(state, mockk<ISessionService>(relaxed = true), MockHelper.time(1_000_000))

        // When
        val result = controller.dynamicTriggerShouldFire(timeSinceLastInAppTrigger(oneHourInSeconds))

        // Then
        result shouldBe true
    }

    test("time since last in-app trigger does not fire before the interval has elapsed") {
        // Given
        val now = 10_000_000L
        val state = InAppStateService()
        state.lastTimeInAppDismissed = now - 60_000L
        val controller = DynamicTriggerController(state, mockk<ISessionService>(relaxed = true), MockHelper.time(now))

        // When
        val result = controller.dynamicTriggerShouldFire(timeSinceLastInAppTrigger(oneHourInSeconds))

        // Then
        result shouldBe false
    }

    test("time since last in-app trigger with a negative less-than value does not fire when no in-app message was dismissed yet") {
        // Given
        val state = InAppStateService()
        val controller = DynamicTriggerController(state, mockk<ISessionService>(relaxed = true), MockHelper.time(1_000_000))

        // When
        val result = controller.dynamicTriggerShouldFire(timeSinceLastInAppTrigger(-1, Trigger.OSTriggerOperator.LESS_THAN))

        // Then
        result shouldBe false
    }
})
