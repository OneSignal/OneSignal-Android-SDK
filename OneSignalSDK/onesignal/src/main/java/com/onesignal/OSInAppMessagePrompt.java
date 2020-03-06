package com.onesignal;

abstract class OSInAppMessagePrompt {

    private boolean didAppear = false;

    OSInAppMessagePrompt() {
    }

    abstract void handlePrompt(OneSignal.OperationCompletedCallback callback);

    public boolean didAppear() {
        return didAppear;
    }

    public void setDidAppear(boolean didAppear) {
        this.didAppear = didAppear;
    }
}
