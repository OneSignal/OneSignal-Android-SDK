package com.onesignal.iam.internal.triggers

internal interface ITriggerHandler {
    // Alerts the observer that a trigger evaluated to true
    fun onTriggerCompleted(triggerId: String)

    // Alerts the observer that a trigger timer has fired
    fun onTriggerConditionChanged()

    fun onTriggerChanged(newTriggerKey: String)
}
