package com.onesignal.inAppMessages.internal.lifecycle

import com.onesignal.inAppMessages.internal.InAppMessage
import com.onesignal.inAppMessages.internal.InAppMessageAction
import com.onesignal.inAppMessages.internal.InAppMessagePage

internal interface IInAppLifecycleEventHandler {
    fun onMessageWillDisplay(message: InAppMessage)
    fun onMessageWasDisplayed(message: InAppMessage)
    fun onMessageActionOccurredOnPreview(message: InAppMessage, action: InAppMessageAction)
    fun onMessageActionOccurredOnMessage(message: InAppMessage, action: InAppMessageAction)
    fun onMessagePageChanged(message: InAppMessage, page: InAppMessagePage)
    fun onMessageWillDismiss(message: InAppMessage)
    fun onMessageWasDismissed(message: InAppMessage)
}
