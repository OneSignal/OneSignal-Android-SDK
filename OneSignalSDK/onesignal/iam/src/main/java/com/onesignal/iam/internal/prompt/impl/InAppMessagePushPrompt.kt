package com.onesignal.iam.internal.prompt.impl

import com.onesignal.iam.internal.prompt.InAppMessagePromptTypes
import com.onesignal.notification.INotificationsManager

internal class InAppMessagePushPrompt(
    private val _notificationsManager: INotificationsManager
) : InAppMessagePrompt() {
    override suspend fun handlePrompt(): PromptActionResult? {
        val result = _notificationsManager.requestPermission()

        return when (result) {
            true -> PromptActionResult.PERMISSION_GRANTED
            false -> PromptActionResult.PERMISSION_DENIED
            null -> null
        }
    }

    override val promptKey: String
        get() = InAppMessagePromptTypes.PUSH_PROMPT_KEY
}
