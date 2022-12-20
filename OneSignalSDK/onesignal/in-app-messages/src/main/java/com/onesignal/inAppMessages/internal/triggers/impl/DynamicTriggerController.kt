package com.onesignal.inAppMessages.internal.triggers.impl

import com.onesignal.common.events.EventProducer
import com.onesignal.common.events.IEventNotifier
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.Trigger
import com.onesignal.inAppMessages.internal.state.InAppStateService
import com.onesignal.inAppMessages.internal.triggers.ITriggerHandler
import com.onesignal.session.internal.session.ISessionService
import java.util.TimerTask
import kotlin.math.abs

/**
 * Controller for dynamic triggers (i.e. time-based).
 */
internal class DynamicTriggerController(
    private val _state: InAppStateService,
    private val _session: ISessionService,
    private val _time: ITime

) : IEventNotifier<ITriggerHandler> {

    val events = EventProducer<ITriggerHandler>()
    private val scheduledMessages: MutableList<String> = mutableListOf()

    /**
     * Determine if the provided dynamic trigger should fire.  If it should fire, the
     * [ITriggerHandler.onTriggerCompleted] will be called just prior to returning. If
     * it shouldn't fire a timer is scheduled and [ITriggerHandler.onTriggerConditionChanged]
     * will be called once the timer has expired.
     *
     * @param trigger The trigger to evaluate.
     *
     * @return true if the trigger currently evaluates to true, false otherwise.
     */
    fun dynamicTriggerShouldFire(trigger: Trigger): Boolean {
        if (trigger.value == null) {
            return false
        }

        synchronized(scheduledMessages) {
            // All time-based trigger values should be numbers (either timestamps or offsets)
            if (trigger.value !is Number) {
                return false
            }

            var currentTimeInterval: Long = 0
            when (trigger.kind) {
                Trigger.OSTriggerKind.SESSION_TIME ->
                    currentTimeInterval = _time.currentTimeMillis - _session.startTime
                Trigger.OSTriggerKind.TIME_SINCE_LAST_IN_APP -> {
                    if (_state.inAppMessageIdShowing != null) {
                        return false
                    }
                    val lastTimeAppDismissed = _state.lastTimeInAppDismissed
                    currentTimeInterval =
                        if (lastTimeAppDismissed == null) {
                            DEFAULT_LAST_IN_APP_TIME_AGO
                        } else {
                            _time.currentTimeMillis - lastTimeAppDismissed
                        }
                }
                else -> {}
            }

            val triggerId = trigger.triggerId
            val requiredTimeInterval = ((trigger.value as Number?)!!.toDouble() * 1000).toLong()

            if (evaluateTimeIntervalWithOperator(
                    requiredTimeInterval.toDouble(),
                    currentTimeInterval.toDouble(),
                    trigger.operatorType
                )
            ) {
                events.fire { it.onTriggerCompleted(triggerId) }
                return true
            }

            val offset = requiredTimeInterval - currentTimeInterval
            if (offset <= 0L) {
                return false
            }

            // Prevents re-scheduling timers for messages that we're already waiting on
            if (scheduledMessages.contains(triggerId)) {
                return false
            }

            DynamicTriggerTimer.scheduleTrigger(
                object : TimerTask() {
                    override fun run() {
                        scheduledMessages.remove(triggerId)
                        events.fire { it.onTriggerConditionChanged() }
                    }
                },
                triggerId,
                offset
            )

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
            Trigger.OSTriggerOperator.GREATER_THAN -> // Counting equal as greater. This way we don't need to schedule a Runnable for 1ms in the future.
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
    }

    override fun subscribe(handler: ITriggerHandler) = events.subscribe(handler)
    override fun unsubscribe(handler: ITriggerHandler) = events.unsubscribe(handler)
}
