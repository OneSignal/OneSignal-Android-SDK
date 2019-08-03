package com.onesignal;

import java.lang.ref.WeakReference;

class OSSystemConditionController {

    interface OSSystemConditionObserver {
        // Alerts the systemConditionObserver that a system condition has being activated
        void messageTriggerConditionChanged();
    }

    private static final String TAG = OSSystemConditionController.class.getCanonicalName();
    private final OSSystemConditionObserver systemConditionObserver;

    OSSystemConditionController(OSSystemConditionObserver systemConditionObserver) {
        this.systemConditionObserver = systemConditionObserver;
    }

    boolean systemConditionsAvailable() {
        if (ActivityLifecycleHandler.curActivity == null) {
            return false;
        }
        boolean keyboardUp = OSViewUtils.isKeyboardUp(new WeakReference<>(ActivityLifecycleHandler.curActivity));
        if (keyboardUp) {
            ActivityLifecycleHandler.setSystemConditionObserver(TAG, systemConditionObserver);
        }
        return !keyboardUp;
    }

}
