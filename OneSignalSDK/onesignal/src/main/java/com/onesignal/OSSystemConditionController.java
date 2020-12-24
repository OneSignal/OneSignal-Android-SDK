package com.onesignal;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;

import java.lang.ref.WeakReference;
import java.util.List;

class OSSystemConditionController {

    interface OSSystemConditionObserver {
        // Alerts the systemConditionObserver that a system condition has being activated
        void systemConditionChanged();
    }

    interface OSSystemConditionHandler {
        void removeSystemConditionObserver(@NonNull String key, @NonNull ActivityLifecycleHandler.KeyboardListener listener);
    }

    private static final String TAG = OSSystemConditionController.class.getCanonicalName();
    private final OSSystemConditionObserver systemConditionObserver;

    OSSystemConditionController(OSSystemConditionObserver systemConditionObserver) {
        this.systemConditionObserver = systemConditionObserver;
    }

    boolean systemConditionsAvailable() {
        if (OneSignal.getCurrentActivity() == null) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.WARN, "OSSystemConditionObserver curActivity null");
            return false;
        }

        try {
            if (isDialogFragmentShowing(OneSignal.getCurrentActivity())) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.WARN, "OSSystemConditionObserver dialog fragment detected");
                return false;
            }
        } catch (NoClassDefFoundError exception) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "AppCompatActivity is not used in this app, skipping 'isDialogFragmentShowing' check: " + exception);
        }

        ActivityLifecycleHandler activityLifecycleHandler = ActivityLifecycleListener.getActivityLifecycleHandler();
        boolean keyboardUp = OSViewUtils.isKeyboardUp(new WeakReference<>(OneSignal.getCurrentActivity()));

        if (keyboardUp && activityLifecycleHandler != null) {
            activityLifecycleHandler.addSystemConditionObserver(TAG, systemConditionObserver);
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.WARN, "OSSystemConditionObserver keyboard up detected");
        }
        return !keyboardUp;
    }

    boolean isDialogFragmentShowing(Context context) throws NoClassDefFoundError {
        // Detect if user has a dialog fragment showing
        if (context instanceof AppCompatActivity) {
            final FragmentManager manager = ((AppCompatActivity) context).getSupportFragmentManager();
            manager.registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {

                @Override
                public void onFragmentDetached(@NonNull FragmentManager fm, @NonNull Fragment fragmentDetached) {
                    super.onFragmentDetached(fm, fragmentDetached);
                    if (fragmentDetached instanceof DialogFragment) {
                        manager.unregisterFragmentLifecycleCallbacks(this);
                        systemConditionObserver.systemConditionChanged();
                    }
                }

            }, true);
            List<Fragment> fragments = manager.getFragments();
            int size = fragments.size();
            if (size > 0) {
                // We only care of the last fragment available
                Fragment fragment = fragments.get(size - 1);
                return fragment.isVisible() && fragment instanceof DialogFragment;
            }
        }
        // We already have Activity lifecycle listener, that listener will handle Activity focus/unFocus state
        //   - Permission prompts will make activity loose focus
        // We cannot detect AlertDialogs because they are added to the decor view as linear layout without an identification
        return false;
    }
}