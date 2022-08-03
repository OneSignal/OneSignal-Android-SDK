package com.onesignal.onesignal.iam.internal.lifecycle

import com.onesignal.onesignal.core.internal.common.events.IEventNotifier
import com.onesignal.onesignal.iam.internal.InAppMessage
import org.json.JSONObject

internal interface IIAMLifecycleService : IEventNotifier<IIAMLifecycleEventHandler> {
    fun messageActionOccurredOnPreview(message: InAppMessage, actionJson: JSONObject)
    fun messageActionOccurredOnMessage(message: InAppMessage, actionJson: JSONObject)
    fun pageChanged(message: InAppMessage, eventJson: JSONObject)
    fun messageWasShown(message: InAppMessage)
    fun messageWillDismiss(message: InAppMessage)
    fun messageWasDismissed(message: InAppMessage)
}