package com.onesignal.onesignal.iam.internal.prompt

internal class InAppMessagePushPrompt : InAppMessagePrompt() {
    override fun handlePrompt(callback: InAppMessagePrompt.OSPromptActionCompletionCallback?) {
        // TODO: prompt for native location
//        OneSignal.promptLocation(callback, true)
    }

    override val promptKey: String
        get() = PUSH_PROMPT_KEY

    companion object {
        const val PUSH_PROMPT_KEY = "push"
    }
}