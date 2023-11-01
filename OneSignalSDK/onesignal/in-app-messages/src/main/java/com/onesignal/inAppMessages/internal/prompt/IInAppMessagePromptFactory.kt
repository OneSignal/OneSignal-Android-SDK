package com.onesignal.inAppMessages.internal.prompt

import com.onesignal.inAppMessages.internal.prompt.impl.InAppMessagePrompt

/**
 * Factory which provides an IAM message prompt implementation depending on
 * the callers needs.
 */
internal interface IInAppMessagePromptFactory {
    /**
     * Create a new prompt instance depending on the prompt type required.
     *
     * @param promptType The type of prompt to instantiate, one of [InAppMessagePromptTypes]
     */
    fun createPrompt(promptType: String): InAppMessagePrompt?
}

internal object InAppMessagePromptTypes {
    const val PUSH_PROMPT_KEY = "push"
    const val LOCATION_PROMPT_KEY = "location"
}
