package com.onesignal;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

class OSSystemConditionController {

    interface OSSystemConditionObserver {
        // Alerts the systemConditionObserver that a system condition has being activated
        void messageTriggerConditionChanged();
    }

    private static final String TAG = OSSystemConditionController.class.getCanonicalName();
    private final OSSystemConditionObserver systemConditionObserver;

    private final ArrayList<String> scheduledMessages;

    OSSystemConditionController(OSSystemConditionObserver systemConditionObserver) {
        scheduledMessages = new ArrayList<>();
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
