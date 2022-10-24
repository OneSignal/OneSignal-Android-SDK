package com.onesignal.iam.internal.triggers

import com.onesignal.common.events.IEventNotifier
import com.onesignal.iam.internal.InAppMessage

internal interface ITriggerController : IEventNotifier<ITriggerHandler> {
    /**
     * This function evaluates all of the triggers for a message. The triggers are organized in
     * a 2D array where the outer array represents OR conditions and the inner array represents
     * AND conditions. If all of the triggers in an inner array evaluate to true, it means the
     * message should be shown and the function returns true.
     *
     * [ITriggerHandler.onTriggerCompleted] will be called for any time-based triggers that
     * *are* currently triggered.
     *
     * Any time-based triggers (dynamic triggers) that aren't currently triggered will have
     * been scheduled, and [ITriggerHandler.onTriggerConditionChanged] will be called once
     * it's condition has changed.
     *
     * @return true if the IAM has triggered "now" and should be displayed. False otherwise.
     */
    fun evaluateMessageTriggers(message: InAppMessage): Boolean

    /**
     * Determine if the provided message contains any of the provided trigger keys.
     *
     * @param message The message to check
     * @param triggersKeys A collection of the trigger keys to check for.
     *
     * @return true if the provided message contains at least one of the [triggersKeys], false otherwise.
     */
    fun isTriggerOnMessage(message: InAppMessage, triggersKeys: Collection<String>): Boolean

    /**
     * Determine if the provided message only has dynamic triggers.
     *
     * @param message The message to check.
     * @return true if the message only has dynamic triggers, false otherwise.
     */
    fun messageHasOnlyDynamicTriggers(message: InAppMessage): Boolean
}
