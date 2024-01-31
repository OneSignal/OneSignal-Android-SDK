package com.onesignal.onesignal.iam.internal.lifecycle

import com.onesignal.onesignal.core.internal.common.events.IEventNotifier
import com.onesignal.onesignal.iam.internal.InAppMessage
import com.onesignal.onesignal.iam.internal.InAppMessageAction
import com.onesignal.onesignal.iam.internal.InAppMessagePage

internal interface IInAppLifecycleService : IEventNotifier<IInAppLifecycleEventHandler> {
    fun messageWillDisplay(message: InAppMessage)
    fun messageWasDisplayed(message: InAppMessage)
    fun messageActionOccurredOnPreview(message: InAppMessage, action: InAppMessageAction)
    fun messageActionOccurredOnMessage(message: InAppMessage, action: InAppMessageAction)
    fun messagePageChanged(message: InAppMessage, page: InAppMessagePage)
    fun messageWillDismiss(message: InAppMessage)
    fun messageWasDismissed(message: InAppMessage)
}