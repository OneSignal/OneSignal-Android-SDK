package com.onesignal.onesignal.iam.internal.lifecycle

import com.onesignal.onesignal.iam.internal.InAppMessage
import com.onesignal.onesignal.iam.internal.InAppMessageAction
import com.onesignal.onesignal.iam.internal.InAppMessagePage

internal interface IInAppLifecycleEventHandler {
    fun onMessageWillDisplay(message: InAppMessage)
    fun onMessageWasDisplayed(message: InAppMessage)
    fun onMessageActionOccurredOnPreview(message: InAppMessage, action: InAppMessageAction)
    fun onMessageActionOccurredOnMessage(message: InAppMessage, action: InAppMessageAction)
    fun onMessagePageChanged(message: InAppMessage, page: InAppMessagePage)
    fun onMessageWillDismiss(message: InAppMessage)
    fun onMessageWasDismissed(message: InAppMessage)
}
