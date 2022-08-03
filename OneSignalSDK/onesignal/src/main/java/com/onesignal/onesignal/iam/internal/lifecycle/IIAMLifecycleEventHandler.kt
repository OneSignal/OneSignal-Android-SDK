package com.onesignal.onesignal.iam.internal.lifecycle

import com.onesignal.onesignal.iam.internal.InAppMessage
import org.json.JSONObject

internal interface IIAMLifecycleEventHandler {
    fun onMessageActionOccurredOnPreview(message: InAppMessage, actionJson: JSONObject)
    fun onMessageActionOccurredOnMessage(message: InAppMessage, actionJson: JSONObject)
    fun onPageChanged(message: InAppMessage, eventJson: JSONObject)
    fun onMessageWasShown(message: InAppMessage)
    fun onMessageWillDismiss(message: InAppMessage)
    fun onMessageWasDismissed(message: InAppMessage)
}
