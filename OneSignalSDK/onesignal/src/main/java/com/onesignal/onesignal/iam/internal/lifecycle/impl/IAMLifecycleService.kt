package com.onesignal.onesignal.iam.internal.lifecycle.impl

import com.onesignal.onesignal.core.internal.common.events.EventProducer
import com.onesignal.onesignal.iam.internal.InAppMessage
import com.onesignal.onesignal.iam.internal.lifecycle.IIAMLifecycleEventHandler
import com.onesignal.onesignal.iam.internal.lifecycle.IIAMLifecycleService
import org.json.JSONObject

internal class IAMLifecycleService() :
    EventProducer<IIAMLifecycleEventHandler>(),
    IIAMLifecycleService {

    override fun messageActionOccurredOnPreview(message: InAppMessage, actionJson: JSONObject) {
        fire { it.onMessageActionOccurredOnPreview(message, actionJson) }
    }

    override fun messageActionOccurredOnMessage(message: InAppMessage, actionJson: JSONObject) {
        fire { it.onMessageActionOccurredOnMessage(message, actionJson) }
    }

    override fun pageChanged(message: InAppMessage, eventJson: JSONObject) {
        fire { it.onPageChanged(message, eventJson) }
    }

    override fun messageWasShown(message: InAppMessage) {
        fire { it.onMessageWasShown(message) }
    }

    override fun messageWillDismiss(message: InAppMessage) {
        fire { it.onMessageWillDismiss(message) }
    }

    override fun messageWasDismissed(message: InAppMessage) {
        fire { it.onMessageWasDismissed(message) }
    }
}