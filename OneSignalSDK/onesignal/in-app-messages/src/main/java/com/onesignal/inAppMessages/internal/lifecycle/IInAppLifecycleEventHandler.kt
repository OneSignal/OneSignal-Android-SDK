package com.onesignal.inAppMessages.internal.lifecycle

import com.onesignal.inAppMessages.internal.InAppMessage
import com.onesignal.inAppMessages.internal.InAppMessageClickResult
import com.onesignal.inAppMessages.internal.InAppMessagePage

internal interface IInAppLifecycleEventHandler {
    fun onMessageWillDisplay(message: InAppMessage)
    fun onMessageWasDisplayed(message: InAppMessage)
    fun onMessageActionOccurredOnPreview(message: InAppMessage, action: InAppMessageClickResult)
    fun onMessageActionOccurredOnMessage(message: InAppMessage, action: InAppMessageClickResult)
    fun onMessagePageChanged(message: InAppMessage, page: InAppMessagePage)
    fun onMessageWillDismiss(message: InAppMessage)
    fun onMessageWasDismissed(message: InAppMessage)
}
