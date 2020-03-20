package com.onesignal;

class OSInAppMessageLocationPrompt extends OSInAppMessagePrompt {

    static final String LOCATION_PROMPT_KEY = "location";

    @Override
    void handlePrompt(OneSignal.OSPromptActionCompletionCallback callback) {
        OneSignal.promptLocation(callback);
    }

    @Override
    String getPromptKey() {
        return LOCATION_PROMPT_KEY;
    }

}
