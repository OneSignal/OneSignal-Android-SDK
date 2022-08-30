package com.onesignal.onesignal.iam.internal.triggers

import com.onesignal.onesignal.core.internal.common.events.IEventNotifier
import com.onesignal.onesignal.iam.internal.InAppMessage

internal interface ITriggerController : IEventNotifier<ITriggerHandler> {
    /**
     * This function evaluates all of the triggers for a message. The triggers are organized in
     * a 2D array where the outer array represents OR conditions and the inner array represents
     * AND conditions. If all of the triggers in an inner array evaluate to true, it means the
     * message should be shown and the function returns true.
     *
     * @return true if the IAM has triggered "now" and should be displayed. False otherwise.
     */
    fun evaluateMessageTriggers(message: InAppMessage): Boolean

    /**
     * Part of redisplay logic
     *
     * If trigger key is part of message triggers, then return true, otherwise false
     */
    fun isTriggerOnMessage(
        message: InAppMessage,
        newTriggersKeys: Collection<String>
    ): Boolean

    /**
     * Part of redisplay logic
     *
     * If message has only dynamic trigger return true, otherwise false
     */
    fun messageHasOnlyDynamicTriggers(message: InAppMessage): Boolean

    /**
     * Trigger Set/Delete/Persist Logic
     */
    fun addTriggers(key: String, value: Any)
    fun removeTriggersForKeys(key: String)
}
