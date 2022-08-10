package com.onesignal.onesignal.iam.internal.triggers.impl

import com.onesignal.onesignal.core.internal.common.events.EventProducer
import com.onesignal.onesignal.core.internal.common.events.IEventNotifier
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.iam.internal.Trigger
import com.onesignal.onesignal.iam.internal.state.InAppStateService
import com.onesignal.onesignal.iam.internal.triggers.ITriggerHandler
import java.util.*
import kotlin.math.abs

internal class DynamicTriggerController(
    private val _state: InAppStateService
) : IEventNotifier<ITriggerHandler> {

    val events = EventProducer<ITriggerHandler>()
    private val scheduledMessages: MutableList<String> = mutableListOf()

    fun dynamicTriggerShouldFire(trigger: Trigger): Boolean {
        if (trigger.value == null)
            return false

        synchronized(scheduledMessages) {

            // All time-based trigger values should be numbers (either timestamps or offsets)
            if (trigger.value !is Number) return false
            var currentTimeInterval: Long = 0
            when (trigger.kind) {
                Trigger.OSTriggerKind.SESSION_TIME -> currentTimeInterval =
                    Date().time - sessionLaunchTime.time
                Trigger.OSTriggerKind.TIME_SINCE_LAST_IN_APP -> {
                    if (_state.inAppMessageShowing)
                        return false
                    val lastTimeAppDismissed = _state.lastTimeInAppDismissed
                    currentTimeInterval =
                        if (lastTimeAppDismissed == null)
                            DEFAULT_LAST_IN_APP_TIME_AGO
                        else Date().time - lastTimeAppDismissed.time
                }
            }
            val triggerId = trigger.triggerId
            val requiredTimeInterval = ((trigger.value as Number?)!!.toDouble() * 1000).toLong()
            if (evaluateTimeIntervalWithOperator(
                    requiredTimeInterval.toDouble(),
                    currentTimeInterval.toDouble(),
                    trigger.operatorType
                )
            ) {
                events.fire { it.onTriggerCompleted(triggerId!!) }
                return true
            }
            val offset = requiredTimeInterval - currentTimeInterval
            if (offset <= 0L) return false

            // Prevents re-scheduling timers for messages that we're already waiting on
            if (scheduledMessages.contains(triggerId))
                return false

            DynamicTriggerTimer.scheduleTrigger(object : TimerTask() {
                override fun run() {
                    scheduledMessages.remove(triggerId)
                    events.fire { it.onTriggerConditionChanged() }
                }
            }, triggerId, offset)
            scheduledMessages.add(triggerId)
        }
        return false
    }

    private fun evaluateTimeIntervalWithOperator(
        timeInterval: Double,
        currentTimeInterval: Double,
        operator: Trigger.OSTriggerOperator
    ): Boolean {
        return when (operator) {
            Trigger.OSTriggerOperator.LESS_THAN -> currentTimeInterval < timeInterval
            Trigger.OSTriggerOperator.LESS_THAN_OR_EQUAL_TO -> currentTimeInterval <= timeInterval || roughlyEqual(
                timeInterval,
                currentTimeInterval
            )
            Trigger.OSTriggerOperator.GREATER_THAN ->                 // Counting equal as greater. This way we don't need to schedule a Runnable for 1ms in the future.
                currentTimeInterval >= timeInterval
            Trigger.OSTriggerOperator.GREATER_THAN_OR_EQUAL_TO -> currentTimeInterval >= timeInterval || roughlyEqual(
                timeInterval,
                currentTimeInterval
            )
            Trigger.OSTriggerOperator.EQUAL_TO -> roughlyEqual(
                timeInterval,
                currentTimeInterval
            )
            Trigger.OSTriggerOperator.NOT_EQUAL_TO -> !roughlyEqual(
                timeInterval,
                currentTimeInterval
            )
            else -> {
                Logging.error("Attempted to apply an invalid operator on a time-based in-app-message trigger: $operator")
                false
            }
        }
    }

    private fun roughlyEqual(left: Double, right: Double): Boolean {
        return abs(left - right) < REQUIRED_ACCURACY
    }

    companion object {
        private const val REQUIRED_ACCURACY = 0.3

        // Assume last time an In-App Message was displayed a very very long time ago.
        private const val DEFAULT_LAST_IN_APP_TIME_AGO: Long = 999999
        private var sessionLaunchTime = Date()
        fun resetSessionLaunchTime() {
            sessionLaunchTime = Date()
        }
    }

    override fun subscribe(handler: ITriggerHandler) = events.subscribe(handler)
    override fun unsubscribe(handler: ITriggerHandler) = events.unsubscribe(handler)
}