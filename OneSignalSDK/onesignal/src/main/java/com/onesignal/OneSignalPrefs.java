/**
 * Modified MIT License
 *
 * Copyright 2018 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package com.onesignal;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.Set;

class OneSignalPrefs {

    // TODO: Remove this once the tasks below have been finished...
    //  1. Fix all of the SharedPreference Keys so they are organized by usage with comments
    //  ex.
    //   // In-App Messaging
    //   public static final String PREFS_OS_CACHED_IAMS = "PREFS_OS_CACHED_IAMS";
    //   public static final String PREFS_OS_DISMISSED_IAMS = "PREFS_OS_DISPLAYED_IAMS";
    //   public static final String PREFS_OS_IMPRESSIONED_IAMS = "PREFS_OS_IMPRESSIONED_IAMS";
    //   public static final String PREFS_OS_CLICKED_CLICK_IDS_IAMS = "PREFS_OS_CLICKED_CLICK_IDS_IAMS";
    //  2. Match keys with value names
    //  ex.
    //   public static final String PREFS_OS_LAST_LOCATION_TIME = "OS_LAST_LOCATION_TIME";
    //  3. Follow syntax and make new names relevant (specific and as short as possible)
    //  ex.
    //   Start with prefix "PREFS_OS_" + "LAST_LOCATION_TIME"

    // SharedPreferences Instances
    public static final String PREFS_ONESIGNAL = OneSignal.class.getSimpleName();
    public static final String PREFS_PLAYER_PURCHASES = "GTPlayerPurchases";
    public static final String PREFS_TRIGGERS = "OneSignalTriggers";

    // SharedPreference Keys
    // Unorganized Keys
    public static final String PREFS_OS_LAST_LOCATION_TIME = "OS_LAST_LOCATION_TIME";
    public static final String PREFS_GT_SOUND_ENABLED = "GT_SOUND_ENABLED";
    public static final String PREFS_OS_LAST_SESSION_TIME = "OS_LAST_SESSION_TIME";
    public static final String PREFS_GT_VIBRATE_ENABLED = "GT_VIBRATE_ENABLED";
    public static final String PREFS_GT_FIREBASE_TRACKING_ENABLED = "GT_FIREBASE_TRACKING_ENABLED";
    public static final String PREFS_OS_CLEAR_GROUP_SUMMARY_CLICK = "OS_CLEAR_GROUP_SUMMARY_CLICK";
    public static final String PREFS_OS_RESTORE_TTL_FILTER = "OS_RESTORE_TTL_FILTER";
    public static final String PREFS_OS_FILTER_OTHER_GCM_RECEIVERS = "OS_FILTER_OTHER_GCM_RECEIVERS";
    public static final String PREFS_GT_APP_ID = "GT_APP_ID";
    public static final String PREFS_GT_PLAYER_ID = "GT_PLAYER_ID";
    public static final String PREFS_GT_UNSENT_ACTIVE_TIME = "GT_UNSENT_ACTIVE_TIME";
    public static final String PREFS_OS_UNSENT_ATTRIBUTED_ACTIVE_TIME = "OS_UNSENT_ATTRIBUTED_ACTIVE_TIME";
    public static final String PREFS_ONESIGNAL_USERSTATE_DEPENDVALYES_ = "ONESIGNAL_USERSTATE_DEPENDVALYES_";
    public static final String PREFS_ONESIGNAL_USERSTATE_SYNCVALYES_ = "ONESIGNAL_USERSTATE_SYNCVALYES_";
    public static final String PREFS_ONESIGNAL_ACCEPTED_NOTIFICATION_LAST = "ONESIGNAL_ACCEPTED_NOTIFICATION_LAST";
    public static final String PREFS_ONESIGNAL_SUBSCRIPTION_LAST = "ONESIGNAL_SUBSCRIPTION_LAST";
    public static final String PREFS_ONESIGNAL_PLAYER_ID_LAST = "ONESIGNAL_PLAYER_ID_LAST";
    public static final String PREFS_ONESIGNAL_PUSH_TOKEN_LAST = "ONESIGNAL_PUSH_TOKEN_LAST";
    public static final String PREFS_ONESIGNAL_PERMISSION_ACCEPTED_LAST = "ONESIGNAL_PERMISSION_ACCEPTED_LAST";
    public static final String PREFS_GT_DO_NOT_SHOW_MISSING_GPS = "GT_DO_NOT_SHOW_MISSING_GPS";
    public static final String PREFS_ONESIGNAL_SUBSCRIPTION = "ONESIGNAL_SUBSCRIPTION";
    public static final String PREFS_ONESIGNAL_SYNCED_SUBSCRIPTION = "ONESIGNAL_SYNCED_SUBSCRIPTION";
    public static final String PREFS_GT_REGISTRATION_ID = "GT_REGISTRATION_ID";
    public static final String PREFS_ONESIGNAL_USER_PROVIDED_CONSENT = "ONESIGNAL_USER_PROVIDED_CONSENT";
    public static final String PREFS_OS_ETAG_PREFIX = "PREFS_OS_ETAG_PREFIX_";
    public static final String PREFS_OS_HTTP_CACHE_PREFIX = "PREFS_OS_HTTP_CACHE_PREFIX_";
    // On Focus Influence
    public static final String PREFS_OS_ATTRIBUTED_INFLUENCES = "PREFS_OS_ATTRIBUTED_INFLUENCES";
    // Email
    public static final String PREFS_OS_EMAIL_ID = "OS_EMAIL_ID";
    public static final String PREFS_ONESIGNAL_EMAIL_ID_LAST = "PREFS_ONESIGNAL_EMAIL_ID_LAST";
    public static final String PREFS_ONESIGNAL_EMAIL_ADDRESS_LAST = "PREFS_ONESIGNAL_EMAIL_ADDRESS_LAST";
    // In-App Messaging
    public static final String PREFS_OS_CACHED_IAMS = "PREFS_OS_CACHED_IAMS";
    public static final String PREFS_OS_DISMISSED_IAMS = "PREFS_OS_DISPLAYED_IAMS";
    public static final String PREFS_OS_IMPRESSIONED_IAMS = "PREFS_OS_IMPRESSIONED_IAMS";
    public static final String PREFS_OS_CLICKED_CLICK_IDS_IAMS = "PREFS_OS_CLICKED_CLICK_IDS_IAMS";
    // Receive Receipts (aka Confirmed Deliveries)
    public static final String PREFS_OS_RECEIVE_RECEIPTS_ENABLED = "PREFS_OS_RECEIVE_RECEIPTS_ENABLED";
    // Outcomes
    static final String PREFS_OS_OUTCOMES_V2 = "PREFS_OS_OUTCOMES_V2";
    // Player Purchase Keys
    static final String PREFS_PURCHASE_TOKENS = "purchaseTokens";
    static final String PREFS_EXISTING_PURCHASES = "ExistingPurchases";

    // Buffered writes to apply on WritePrefHandlerThread with a short delay
    static HashMap<String, HashMap<String, Object>> prefsToApply;
    public static WritePrefHandlerThread prefsHandler;

    static {
        initializePool();
    }

    public static class WritePrefHandlerThread extends HandlerThread {
        private @Nullable Handler mHandler;

        private static final int WRITE_CALL_DELAY_TO_BUFFER_MS = 200;
        private long lastSyncTime = 0L;

        WritePrefHandlerThread(String name) {
            super(name);
        }

        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();

            // Getting handler here as onLooperPrepared guarantees getLooper() will be non-null
            mHandler = new Handler(getLooper());

            // Kicks off our first flush, startDelayedWrite will schedule all flushes after that
            scheduleFlushToDisk();
        }

        private synchronized void startDelayedWrite() {
            // A Context is required to write,
            //   if not available now later OneSignal.setContext will call this again.
            if (OneSignal.appContext == null)
                return;

            startThread();
            scheduleFlushToDisk();
        }

        private boolean threadStartCalled;
        private void startThread() {
            if (threadStartCalled)
                return;

            start();
            threadStartCalled = true;
        }

        private synchronized void scheduleFlushToDisk() {
            // Could be null if looper thread just started
            if (mHandler == null)
                return;

            mHandler.removeCallbacksAndMessages(null);

            if (lastSyncTime == 0)
                lastSyncTime = System.currentTimeMillis();
            long delay = lastSyncTime - System.currentTimeMillis() + WRITE_CALL_DELAY_TO_BUFFER_MS;

            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    flushBufferToDisk();
                }
            };
            mHandler.postDelayed(runnable, delay);
        }

        private void flushBufferToDisk() {
            for (String pref : prefsToApply.keySet()) {
                SharedPreferences prefsToWrite = getSharedPrefsByName(pref);
                SharedPreferences.Editor editor = prefsToWrite.edit();
                HashMap<String, Object> prefHash = prefsToApply.get(pref);
                synchronized (prefHash) {
                    for (String key : prefHash.keySet()) {
                        Object value = prefHash.get(key);
                        if (value instanceof String)
                            editor.putString(key, (String)value);
                        else if (value instanceof Boolean)
                            editor.putBoolean(key, (Boolean)value);
                        else if (value instanceof Integer)
                            editor.putInt(key, (Integer)value);
                        else if (value instanceof Long)
                            editor.putLong(key, (Long)value);
                        else if (value instanceof Set)
                            editor.putStringSet(key, (Set<String>)value);
                    }
                    prefHash.clear();
                }
                editor.apply();
            }

            lastSyncTime = System.currentTimeMillis();
        }
    }

    public static void initializePool() {
        prefsToApply = new HashMap<>();
        prefsToApply.put(PREFS_ONESIGNAL, new HashMap<String, Object>());
        prefsToApply.put(PREFS_PLAYER_PURCHASES, new HashMap<String, Object>());
        prefsToApply.put(PREFS_TRIGGERS, new HashMap<String, Object>());

        prefsHandler = new WritePrefHandlerThread("OSH_WritePrefs");
    }

    public static void startDelayedWrite() {
       prefsHandler.startDelayedWrite();
    }

    public static void saveString(final String prefsName, final String key, final String value) {
        save(prefsName, key, value);
    }

    public static void saveStringSet(@NonNull final String prefsName, @NonNull final String key, @NonNull final Set<String> value) {
        save(prefsName, key, value);
    }

    public static void saveBool(String prefsName, String key, boolean value) {
        save(prefsName, key, value);
    }

    public static void saveInt(String prefsName, String key, int value) {
        save(prefsName, key, value);
    }

    public static void saveLong(String prefsName, String key, long value) {
        save(prefsName, key, value);
    }

    public static void saveObject(String prefsName, String key, Object value) {
        save(prefsName, key, value);
    }

    static private void save(String prefsName, String key, Object value) {
        HashMap<String, Object> pref = prefsToApply.get(prefsName);
        synchronized (pref) {
            pref.put(key, value);
        }
        startDelayedWrite();
    }

    static String getString(String prefsName, String key, String defValue) {
        return (String)get(prefsName, key, String.class, defValue);
    }

    static boolean getBool(String prefsName, String key, boolean defValue) {
        return (Boolean)get(prefsName, key, Boolean.class, defValue);
    }

    static int getInt(String prefsName, String key, int defValue) {
        return (Integer)get(prefsName, key, Integer.class, defValue);
    }

    static long getLong(String prefsName, String key, long defValue) {
        return (Long)get(prefsName, key, Long.class, defValue);
    }

    public static @Nullable Set<String> getStringSet(@NonNull String prefsName, @NonNull String key, @Nullable Set<String> defValue) {
        return (Set<String>)get(prefsName, key, Set.class, defValue);
    }

    static Object getObject(String prefsName, String key, Object defValue) {
        return get(prefsName, key, Object.class, defValue);
    }

    // If type == Object then this is a contains check
    private static @Nullable Object get(String prefsName, String key, Class type, Object defValue) {
        HashMap<String, Object> pref = prefsToApply.get(prefsName);

        synchronized (pref) {
            if (type.equals(Object.class) && pref.containsKey(key))
                return true;

            Object cachedValue = pref.get(key);
            if (cachedValue != null || pref.containsKey(key))
                return cachedValue;
        }

        SharedPreferences prefs = getSharedPrefsByName(prefsName);
        if (prefs != null) {
            if (type.equals(String.class))
               return prefs.getString(key, (String)defValue);
            else if (type.equals(Boolean.class))
                return prefs.getBoolean(key, (Boolean)defValue);
            else if (type.equals(Integer.class))
                return prefs.getInt(key, (Integer)defValue);
            else if (type.equals(Long.class))
                return prefs.getLong(key, (Long)defValue);
            else if (type.equals(Set.class))
                return prefs.getStringSet(key, (Set<String>)defValue);
            else if (type.equals(Object.class))
                return prefs.contains(key);

            return null;
        }

        return defValue;
    }

    static synchronized SharedPreferences getSharedPrefsByName(String prefsName) {
        if (OneSignal.appContext == null) {
            String msg = "OneSignal.appContext null, could not read " + prefsName + " from getSharedPreferences.";
            OneSignal.Log(OneSignal.LOG_LEVEL.WARN, msg, new Throwable());
            return null;
        }

        return OneSignal.appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }

}
