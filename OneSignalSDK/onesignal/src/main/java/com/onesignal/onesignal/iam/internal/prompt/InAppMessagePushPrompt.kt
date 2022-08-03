package com.onesignal.onesignal.iam.internal.prompt

internal class InAppMessagePushPrompt : InAppMessagePrompt() {
    override suspend fun handlePrompt() : PromptActionResult? {
        // TODO: prompt for native location
        return PromptActionResult.ERROR
//        OneSignal.promptLocation(callback, true)
    }

    override val promptKey: String
        get() = PUSH_PROMPT_KEY

    companion object {
        const val PUSH_PROMPT_KEY = "push"
    }
}