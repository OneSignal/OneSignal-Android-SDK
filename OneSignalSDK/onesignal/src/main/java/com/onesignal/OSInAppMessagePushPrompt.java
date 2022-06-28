package com.onesignal;

public class OSInAppMessagePushPrompt extends OSInAppMessagePrompt {

    static final String PUSH_PROMPT_KEY = "push";

    @Override
    void handlePrompt(OneSignal.OSPromptActionCompletionCallback callback) {
        OneSignal.promptForPushNotifications(
            true,
            accepted -> {
                OneSignal.PromptActionResult result = accepted ?
                    OneSignal.PromptActionResult.PERMISSION_GRANTED :
                    OneSignal.PromptActionResult.PERMISSION_DENIED;
                callback.onCompleted(result);
            }
        );
    }

    @Override
    String getPromptKey() {
        return PUSH_PROMPT_KEY;
    }

}
