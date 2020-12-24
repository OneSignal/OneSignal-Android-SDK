/**
 * Modified MIT License
 * <p>
 * Copyright 2017 OneSignal
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class ActivityLifecycleHandler implements OSSystemConditionController.OSSystemConditionHandler {

    abstract static class ActivityAvailableListener {
        void available(@NonNull Activity activity) {
        }

        void stopped(@NonNull Activity activity) {
        }

        void lostFocus() {
        }
    }

    private static final Object SYNC_LOCK = new Object();
    private static final String FOCUS_LOST_WORKER_TAG = "FOCUS_LOST_WORKER_TAG";
    private static final Map<String, ActivityAvailableListener> sActivityAvailableListeners = new ConcurrentHashMap<>();
    private static final Map<String, OSSystemConditionController.OSSystemConditionObserver> sSystemConditionObservers = new ConcurrentHashMap<>();
    private static final Map<String, KeyboardListener> sKeyboardListeners = new ConcurrentHashMap<>();

    static FocusHandlerThread focusHandlerThread = new FocusHandlerThread();
    @SuppressLint("StaticFieldLeak")
    private Activity curActivity = null;
    private boolean nextResumeIsFirstActivity = false;

    void onConfigurationChanged(Configuration newConfig, Activity activity) {
        // If Activity contains the configChanges orientation flag, re-create the view this way
        if (curActivity != null && OSUtils.hasConfigChangeFlag(curActivity, ActivityInfo.CONFIG_ORIENTATION)) {
            logOrientationChange(newConfig.orientation, activity);
            onOrientationChanged(activity);
        }
    }

    void onActivityCreated(Activity activity) {
    }

    void onActivityStarted(Activity activity) {
    }

    void onActivityResumed(Activity activity) {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "onActivityResumed: " + activity);
        setCurActivity(activity);
        logCurActivity();
        handleFocus();
    }

    void onActivityPaused(Activity activity) {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "onActivityPaused: " + activity);
        if (activity == curActivity) {
            curActivity = null;
            handleLostFocus();
        }

        logCurActivity();
    }

    void onActivityStopped(Activity activity) {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "onActivityStopped: " + activity);

        if (activity == curActivity) {
            curActivity = null;
            handleLostFocus();
        }

        for (Map.Entry<String, ActivityAvailableListener> entry : sActivityAvailableListeners.entrySet()) {
            entry.getValue().stopped(activity);
        }

        logCurActivity();
    }

    void onActivityDestroyed(Activity activity) {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "onActivityDestroyed: " + activity);
        sKeyboardListeners.clear();

        if (activity == curActivity) {
            curActivity = null;
            handleLostFocus();
        }

        logCurActivity();
    }

    private void logCurActivity() {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "curActivity is NOW: " + (curActivity != null ? "" + curActivity.getClass().getName() + ":" + curActivity : "null"));
    }

    private void logOrientationChange(int orientation, Activity activity) {
        // Log device orientation change
        if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Configuration Orientation Change: LANDSCAPE (" + orientation + ") on activity: " + activity);
        else if (orientation == Configuration.ORIENTATION_PORTRAIT)
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Configuration Orientation Change: PORTRAIT (" + orientation + ") on activity: " + activity);
    }

    /**
     * Takes pieces from onActivityResumed and onActivityStopped to recreate the view when the
     * phones orientation is changed from manual detection using the onConfigurationChanged callback
     * This fix was originally implemented for In App Messages not being re-shown when orientation
     * was changed on wrapper SDK apps
     */
    private void onOrientationChanged(Activity activity) {
        // Remove view
        handleLostFocus();
        for (Map.Entry<String, ActivityAvailableListener> entry : sActivityAvailableListeners.entrySet()) {
            entry.getValue().stopped(activity);
        }

        // Show view
        for (Map.Entry<String, ActivityAvailableListener> entry : sActivityAvailableListeners.entrySet()) {
            entry.getValue().available(curActivity);
        }

        ViewTreeObserver treeObserver = curActivity.getWindow().getDecorView().getViewTreeObserver();
        for (Map.Entry<String, OSSystemConditionController.OSSystemConditionObserver> entry : sSystemConditionObservers.entrySet()) {
            KeyboardListener keyboardListener = new KeyboardListener(this, entry.getValue(), entry.getKey());
            treeObserver.addOnGlobalLayoutListener(keyboardListener);
            sKeyboardListeners.put(entry.getKey(), keyboardListener);
        }
        handleFocus();
    }

    void addSystemConditionObserver(String key, OSSystemConditionController.OSSystemConditionObserver systemConditionObserver) {
        if (curActivity != null) {
            ViewTreeObserver treeObserver = curActivity.getWindow().getDecorView().getViewTreeObserver();
            KeyboardListener keyboardListener = new KeyboardListener(this, systemConditionObserver, key);
            treeObserver.addOnGlobalLayoutListener(keyboardListener);
            sKeyboardListeners.put(key, keyboardListener);
        }
        sSystemConditionObservers.put(key, systemConditionObserver);
    }

    public void removeSystemConditionObserver(@NonNull String key, @NonNull KeyboardListener keyboardListener) {
        if (curActivity != null) {
            ViewTreeObserver treeObserver = curActivity.getWindow().getDecorView().getViewTreeObserver();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                treeObserver.removeGlobalOnLayoutListener(keyboardListener);
            } else {
                treeObserver.removeOnGlobalLayoutListener(keyboardListener);
            }
        }

        sKeyboardListeners.remove(key);
        sSystemConditionObservers.remove(key);
    }

    void addActivityAvailableListener(String key, ActivityAvailableListener activityAvailableListener) {
        sActivityAvailableListeners.put(key, activityAvailableListener);
        if (curActivity != null)
            activityAvailableListener.available(curActivity);
    }

    void removeActivityAvailableListener(String key) {
        sActivityAvailableListeners.remove(key);
    }

    public Activity getCurActivity() {
        return curActivity;
    }

    public void setCurActivity(Activity activity) {
        curActivity = activity;
        for (Map.Entry<String, ActivityAvailableListener> entry : sActivityAvailableListeners.entrySet()) {
            entry.getValue().available(curActivity);
        }

        try {
            ViewTreeObserver treeObserver = curActivity.getWindow().getDecorView().getViewTreeObserver();
            for (Map.Entry<String, OSSystemConditionController.OSSystemConditionObserver> entry : sSystemConditionObservers.entrySet()) {
                KeyboardListener keyboardListener = new KeyboardListener(this, entry.getValue(), entry.getKey());
                treeObserver.addOnGlobalLayoutListener(keyboardListener);
                sKeyboardListeners.put(entry.getKey(), keyboardListener);
            }
        } catch (RuntimeException e) {
            // Related to Unity Issue #239 on Github
            // https://github.com/OneSignal/OneSignal-Unity-SDK/issues/239
            // RuntimeException at ActivityLifecycleHandler.setCurActivity on Android (Unity 2.9.0)
            e.printStackTrace();
        }
    }

    void setNextResumeIsFirstActivity(boolean nextResumeIsFirstActivity) {
        this.nextResumeIsFirstActivity = nextResumeIsFirstActivity;
    }

    private void handleLostFocus() {
        focusHandlerThread.runRunnable(new AppFocusRunnable());
    }

    private void handleFocus() {
        if (focusHandlerThread.hasBackgrounded() || nextResumeIsFirstActivity) {
            setNextResumeIsFirstActivity(false);
            focusHandlerThread.resetBackgroundState();
            OneSignal.onAppFocus();
        } else
            focusHandlerThread.stopScheduledRunnable();
    }

    static class FocusHandlerThread extends HandlerThread {
        private Handler mHandler;
        private AppFocusRunnable appFocusRunnable;

        FocusHandlerThread() {
            super("FocusHandlerThread");
            start();
            mHandler = new Handler(getLooper());
        }

        Looper getHandlerLooper() {
            return mHandler.getLooper();
        }

        void resetBackgroundState() {
            if (appFocusRunnable != null)
                appFocusRunnable.backgrounded = false;
        }

        void stopScheduledRunnable() {
            mHandler.removeCallbacksAndMessages(null);
        }

        void runRunnable(AppFocusRunnable runnable) {
            if (appFocusRunnable != null && appFocusRunnable.backgrounded && !appFocusRunnable.completed)
                return;

            appFocusRunnable = runnable;
            mHandler.removeCallbacksAndMessages(null);
            mHandler.postDelayed(runnable, 2000);
        }

        boolean hasBackgrounded() {
            return appFocusRunnable != null && appFocusRunnable.backgrounded;
        }
    }

    private static class AppFocusRunnable implements Runnable {
        private boolean backgrounded, completed;

        public void run() {
            if (OneSignal.getCurrentActivity() != null)
                return;

            backgrounded = true;
            for (Map.Entry<String, ActivityAvailableListener> entry : sActivityAvailableListeners.entrySet()) {
                entry.getValue().lostFocus();
            }
            OneSignal.onAppLostFocus();
            completed = true;
        }
    }

    static class KeyboardListener implements ViewTreeObserver.OnGlobalLayoutListener {

        private final OSSystemConditionController.OSSystemConditionObserver observer;
        private final OSSystemConditionController.OSSystemConditionHandler systemConditionListener;
        private final String key;

        private KeyboardListener(OSSystemConditionController.OSSystemConditionHandler systemConditionListener, OSSystemConditionController.OSSystemConditionObserver observer, String key) {
            this.systemConditionListener = systemConditionListener;
            this.observer = observer;
            this.key = key;
        }

        @Override
        public void onGlobalLayout() {
            boolean keyboardUp = OSViewUtils.isKeyboardUp(new WeakReference<>(OneSignal.getCurrentActivity()));
            if (!keyboardUp) {
                systemConditionListener.removeSystemConditionObserver(key, this);
                observer.systemConditionChanged();
            }
        }
    }
}