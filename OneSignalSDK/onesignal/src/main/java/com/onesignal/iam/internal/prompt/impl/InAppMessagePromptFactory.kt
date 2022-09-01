package com.onesignal.iam.internal.prompt.impl

import com.onesignal.iam.internal.prompt.IInAppMessagePromptFactory
import com.onesignal.iam.internal.prompt.InAppMessagePromptTypes
import com.onesignal.location.ILocationManager
import com.onesignal.notification.INotificationsManager

internal class InAppMessagePromptFactory(
    private val _notificationsManager: INotificationsManager,
    private val _locationManager: ILocationManager
) : IInAppMessagePromptFactory {

    override fun createPrompt(promptType: String): InAppMessagePrompt? {
        return when (promptType) {
            InAppMessagePromptTypes.PUSH_PROMPT_KEY -> InAppMessagePushPrompt(_notificationsManager)
            InAppMessagePromptTypes.LOCATION_PROMPT_KEY -> InAppMessageLocationPrompt(_locationManager)
            else -> null
        }
    }
}
