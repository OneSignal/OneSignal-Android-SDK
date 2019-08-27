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

class ActivityLifecycleHandler {

    static boolean nextResumeIsFirstActivity;

    abstract static class ActivityAvailableListener {
        void available(@NonNull Activity activity) {
        }

        void stopped(WeakReference<Activity> reference) {
        }
    }

    private static Map<String, ActivityAvailableListener> sActivityAvailableListeners = new ConcurrentHashMap<>();
    private static Map<String, OSSystemConditionController.OSSystemConditionObserver> sSystemConditionObservers = new ConcurrentHashMap<>();
    private static Map<String, KeyboardListener> sKeyboardListeners = new ConcurrentHashMap<>();
    static FocusHandlerThread focusHandlerThread = new FocusHandlerThread();
    @SuppressLint("StaticFieldLeak")
    static Activity curActivity;

    static void setSystemConditionObserver(String key, OSSystemConditionController.OSSystemConditionObserver systemConditionObserver) {
        if (curActivity != null) {
            ViewTreeObserver treeObserver = curActivity.getWindow().getDecorView().getViewTreeObserver();
            KeyboardListener keyboardListener = new KeyboardListener(systemConditionObserver, key);
            treeObserver.addOnGlobalLayoutListener(keyboardListener);
            sKeyboardListeners.put(key, keyboardListener);
        }
        sSystemConditionObservers.put(key, systemConditionObserver);
    }

    static void setActivityAvailableListener(String key, ActivityAvailableListener activityAvailableListener) {
        sActivityAvailableListeners.put(key, activityAvailableListener);
        if (curActivity != null)
            activityAvailableListener.available(curActivity);
    }

    static void removeSystemConditionObserver(String key) {
        sKeyboardListeners.remove(key);
        sSystemConditionObservers.remove(key);
    }

    static void removeActivityAvailableListener(String key) {
        sActivityAvailableListeners.remove(key);
    }

    private static void setCurActivity(Activity activity) {
        curActivity = activity;
        for (Map.Entry<String, ActivityAvailableListener> entry : sActivityAvailableListeners.entrySet()) {
            entry.getValue().available(curActivity);
        }

        ViewTreeObserver treeObserver = curActivity.getWindow().getDecorView().getViewTreeObserver();
        for (Map.Entry<String, OSSystemConditionController.OSSystemConditionObserver> entry : sSystemConditionObservers.entrySet()) {
            KeyboardListener keyboardListener = new KeyboardListener(entry.getValue(), entry.getKey());
            treeObserver.addOnGlobalLayoutListener(keyboardListener);
            sKeyboardListeners.put(entry.getKey(), keyboardListener);
        }
    }

    static void onConfigurationChanged(Configuration newConfig) {
        // If Activity contains the configChanges orientation flag, re-create the view this way
        if (curActivity != null && OSUtils.hasConfigChangeFlag(curActivity, ActivityInfo.CONFIG_ORIENTATION)) {
            logOrientationChange(newConfig.orientation);
            onOrientationChanged();
        }
    }

    static void onActivityCreated(Activity activity) {
    }

    static void onActivityStarted(Activity activity) {
    }

    static void onActivityResumed(Activity activity) {
        setCurActivity(activity);
        logCurActivity();
        handleFocus();
    }

    static void onActivityPaused(Activity activity) {
        if (activity == curActivity) {
            curActivity = null;
            handleLostFocus();
        }

        logCurActivity();
    }

    static void onActivityStopped(Activity activity) {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "onActivityStopped: " + activity);

        if (activity == curActivity) {
            curActivity = null;
            handleLostFocus();
        }

        for (Map.Entry<String, ActivityAvailableListener> entry : sActivityAvailableListeners.entrySet()) {
            entry.getValue().stopped(new WeakReference<>(activity));
        }

        logCurActivity();
    }

    static void onActivityDestroyed(Activity activity) {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "onActivityDestroyed: " + activity);
        sKeyboardListeners.clear();

        if (activity == curActivity) {
            curActivity = null;
            handleLostFocus();
        }

        logCurActivity();
    }

    static private void logCurActivity() {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "curActivity is NOW: " + (curActivity != null ? "" + curActivity.getClass().getName() + ":" + curActivity : "null"));
    }

    private static void logOrientationChange(int orientation) {
        // Log device orientation change
        if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Configuration Orientation Change: LANDSCAPE (" + orientation + ")");
        else if (orientation == Configuration.ORIENTATION_PORTRAIT)
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Configuration Orientation Change: PORTRAIT (" + orientation + ")");
    }

    /**
     * Takes pieces from onActivityResumed and onActivityStopped to recreate the view when the
     * phones orientation is changed from manual detection using the onConfigurationChanged callback
     * This fix was originally implemented for In App Messages not being re-shown when orientation
     * was changed on wrapper SDK apps
     */
    private static void onOrientationChanged() {
        // Remove view
        handleLostFocus();
        for (Map.Entry<String, ActivityAvailableListener> entry : sActivityAvailableListeners.entrySet()) {
            entry.getValue().stopped(new WeakReference<>(curActivity));
        }

        // Show view
        for (Map.Entry<String, ActivityAvailableListener> entry : sActivityAvailableListeners.entrySet()) {
            entry.getValue().available(curActivity);
        }

        ViewTreeObserver treeObserver = curActivity.getWindow().getDecorView().getViewTreeObserver();
        for (Map.Entry<String, OSSystemConditionController.OSSystemConditionObserver> entry : sSystemConditionObservers.entrySet()) {
            KeyboardListener keyboardListener = new KeyboardListener(entry.getValue(), entry.getKey());
            treeObserver.addOnGlobalLayoutListener(keyboardListener);
            sKeyboardListeners.put(entry.getKey(), keyboardListener);
        }
        handleFocus();
    }

    static private void handleLostFocus() {
        focusHandlerThread.runRunnable(new AppFocusRunnable());
    }

    static private void handleFocus() {
        if (focusHandlerThread.hasBackgrounded() || nextResumeIsFirstActivity) {
            nextResumeIsFirstActivity = false;
            focusHandlerThread.resetBackgroundState();
            OneSignal.onAppFocus();
        } else
            focusHandlerThread.stopScheduledRunnable();
    }

    static class FocusHandlerThread extends HandlerThread {
        Handler mHandler;
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
            if (curActivity != null)
                return;

            backgrounded = true;
            OneSignal.onAppLostFocus();
            completed = true;
        }
    }

    private static class KeyboardListener implements ViewTreeObserver.OnGlobalLayoutListener {

        private final OSSystemConditionController.OSSystemConditionObserver observer;
        private final String key;

        private KeyboardListener(OSSystemConditionController.OSSystemConditionObserver observer, String key) {
            this.observer = observer;
            this.key = key;
        }

        @Override
        public void onGlobalLayout() {
            boolean keyboardUp = OSViewUtils.isKeyboardUp(new WeakReference<>(ActivityLifecycleHandler.curActivity));
            if (!keyboardUp) {
                if (curActivity != null) {
                    ViewTreeObserver treeObserver = curActivity.getWindow().getDecorView().getViewTreeObserver();
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                        treeObserver.removeGlobalOnLayoutListener(KeyboardListener.this);
                    } else {
                        treeObserver.removeOnGlobalLayoutListener(KeyboardListener.this);
                    }
                }
                ActivityLifecycleHandler.removeSystemConditionObserver(key);
                observer.messageTriggerConditionChanged();
            }
        }
    }
}