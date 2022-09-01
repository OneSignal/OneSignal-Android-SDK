package com.onesignal.iam.internal.lifecycle.impl

import com.onesignal.core.internal.common.events.EventProducer
import com.onesignal.iam.internal.InAppMessage
import com.onesignal.iam.internal.InAppMessageAction
import com.onesignal.iam.internal.InAppMessagePage
import com.onesignal.iam.internal.lifecycle.IInAppLifecycleEventHandler
import com.onesignal.iam.internal.lifecycle.IInAppLifecycleService

internal class IAMLifecycleService() :
    EventProducer<IInAppLifecycleEventHandler>(),
    IInAppLifecycleService {

    override fun messageWillDisplay(message: InAppMessage) {
        fire { it.onMessageWillDisplay(message) }
    }

    override fun messageWasDisplayed(message: InAppMessage) {
        fire { it.onMessageWasDisplayed(message) }
    }

    override fun messageActionOccurredOnPreview(message: InAppMessage, action: InAppMessageAction) {
        fire { it.onMessageActionOccurredOnPreview(message, action) }
    }

    override fun messageActionOccurredOnMessage(message: InAppMessage, action: InAppMessageAction) {
        fire { it.onMessageActionOccurredOnMessage(message, action) }
    }

    override fun messagePageChanged(message: InAppMessage, page: InAppMessagePage) {
        fire { it.onMessagePageChanged(message, page) }
    }

    override fun messageWillDismiss(message: InAppMessage) {
        fire { it.onMessageWillDismiss(message) }
    }

    override fun messageWasDismissed(message: InAppMessage) {
        fire { it.onMessageWasDismissed(message) }
    }
}
