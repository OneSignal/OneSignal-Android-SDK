package com.onesignal.onesignal.iam.internal.prompt

internal class InAppMessageLocationPrompt : InAppMessagePrompt() {
    override suspend fun handlePrompt() : PromptActionResult? {
        // TODO: prompt for native push
        return PromptActionResult.ERROR
//        OneSignal.promptForPushNotifications(
//            true,
//            accepted -> {
//                OneSignal.PromptActionResult result = accepted ?
//                    OneSignal.PromptActionResult.PERMISSION_GRANTED :
//                    OneSignal.PromptActionResult.PERMISSION_DENIED;
//                callback.onCompleted(result);
//            }
//        );
    }

    override val promptKey: String
        get() = LOCATION_PROMPT_KEY

    companion object {
        const val LOCATION_PROMPT_KEY = "location"
    }
}