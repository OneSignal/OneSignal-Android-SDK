package com.onesignal.inAppMessages.internal.prompt.impl

import com.onesignal.inAppMessages.internal.prompt.InAppMessagePromptTypes
import com.onesignal.notifications.INotificationsManager

internal class InAppMessagePushPrompt(
    private val _notificationsManager: INotificationsManager
) : InAppMessagePrompt() {
    override suspend fun handlePrompt(): PromptActionResult? {
        val result = _notificationsManager.requestPermission(true)

        return if (result) PromptActionResult.PERMISSION_GRANTED else PromptActionResult.PERMISSION_DENIED
    }

    override val promptKey: String
        get() = InAppMessagePromptTypes.PUSH_PROMPT_KEY
}
