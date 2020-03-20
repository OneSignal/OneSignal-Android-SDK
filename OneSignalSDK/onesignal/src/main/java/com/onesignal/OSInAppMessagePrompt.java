package com.onesignal;

abstract class OSInAppMessagePrompt {

    private boolean prompted = false;

    OSInAppMessagePrompt() {
    }

    abstract void handlePrompt(OneSignal.OSPromptActionCompletionCallback callback);
    abstract String getPromptKey();

    boolean hasPrompted() {
        return prompted;
    }

    void setPrompted(boolean prompted) {
        this.prompted = prompted;
    }

    @Override
    public String toString() {
        return "OSInAppMessagePrompt{" +
                "key=" + getPromptKey() +
                " prompted=" + prompted +
                '}';
    }
}
