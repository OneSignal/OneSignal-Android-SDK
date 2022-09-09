package com.onesignal.iam.internal.triggers

import com.onesignal.core.user.IUserManager

/**
 * Implement this interface and subscribe it via [ITriggerController.subscribe] to be notified when
 * a trigger event has occurred.
 */
internal interface ITriggerHandler {
    /**
     * Called when a time-based trigger (dynamic trigger) was evaluated to true (called during
     * the [ITriggerController.evaluateMessageTriggers] call.
     */
    fun onTriggerCompleted(triggerId: String)

    /**
     * Called when a time-based trigger (dynamic trigger) will now evaluate to true.
     */
    fun onTriggerConditionChanged()

    /**
     * Called when a new trigger has been added, or an existing trigger's value has been
     * updated, to the device via [IUserManager.setTrigger].
     */
    fun onTriggerChanged(newTriggerKey: String)
}
