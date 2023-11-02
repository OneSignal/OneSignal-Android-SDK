package com.onesignal.inAppMessages.internal.lifecycle

import com.onesignal.common.events.IEventNotifier
import com.onesignal.inAppMessages.internal.InAppMessage
import com.onesignal.inAppMessages.internal.InAppMessageClickResult
import com.onesignal.inAppMessages.internal.InAppMessagePage

internal interface IInAppLifecycleService : IEventNotifier<IInAppLifecycleEventHandler> {
    fun messageWillDisplay(message: InAppMessage)

    fun messageWasDisplayed(message: InAppMessage)

    fun messageActionOccurredOnPreview(
        message: InAppMessage,
        action: InAppMessageClickResult,
    )

    fun messageActionOccurredOnMessage(
        message: InAppMessage,
        action: InAppMessageClickResult,
    )

    fun messagePageChanged(
        message: InAppMessage,
        page: InAppMessagePage,
    )

    fun messageWillDismiss(message: InAppMessage)

    fun messageWasDismissed(message: InAppMessage)
}
