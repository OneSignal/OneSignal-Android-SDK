package com.onesignal;

class OSInAppMessageLocationPrompt extends OSInAppMessagePrompt {

    static final String LOCATION_PROMPT_KEY = "location";

    @Override
    void handlePrompt(OneSignal.OperationCompletedCallback callback) {
        OneSignal.promptLocation(callback);
    }

}
