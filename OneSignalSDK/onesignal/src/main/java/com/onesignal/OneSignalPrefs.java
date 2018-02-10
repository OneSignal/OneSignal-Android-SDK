package com.onesignal;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * Copyright 2017 OneSignal
 * Created by alamgir on 9/20/17.
 */

class OneSignalPrefs {

    static final String PREFS_ONESIGNAL = OneSignal.class.getSimpleName();
    static final String PREFS_PLAYER_PURCHASES = "GTPlayerPurchases";

//    PREFERENCES KEYS
    static final String PREFS_OS_LAST_LOCATION_TIME = "OS_LAST_LOCATION_TIME";
    static final String PREFS_GT_SOUND_ENABLED = "GT_SOUND_ENABLED";
    static final String PREFS_OS_LAST_SESSION_TIME = "OS_LAST_SESSION_TIME";
    static final String PREFS_GT_VIBRATE_ENABLED = "GT_VIBRATE_ENABLED";
    static final String PREFS_GT_FIREBASE_TRACKING_ENABLED = "GT_FIREBASE_TRACKING_ENABLED";
    static final String PREFS_OS_FILTER_OTHER_GCM_RECEIVERS = "OS_FILTER_OTHER_GCM_RECEIVERS";
    static final String PREFS_GT_APP_ID = "GT_APP_ID";
    static final String PREFS_GT_PLAYER_ID = "GT_PLAYER_ID";
    static final String PREFS_OS_EMAIL_ID = "OS_EMAIL_ID";
    static final String PREFS_GT_UNSENT_ACTIVE_TIME = "GT_UNSENT_ACTIVE_TIME";
    static final String PREFS_ONESIGNAL_USERSTATE_DEPENDVALYES_ = "ONESIGNAL_USERSTATE_DEPENDVALYES_";
    static final String PREFS_ONESIGNAL_USERSTATE_SYNCVALYES_ = "ONESIGNAL_USERSTATE_SYNCVALYES_";
    static final String PREFS_ONESIGNAL_ACCEPTED_NOTIFICATION_LAST = "ONESIGNAL_ACCEPTED_NOTIFICATION_LAST";
    static final String PREFS_ONESIGNAL_SUBSCRIPTION_LAST = "ONESIGNAL_SUBSCRIPTION_LAST";
    static final String PREFS_ONESIGNAL_PLAYER_ID_LAST = "ONESIGNAL_PLAYER_ID_LAST";
    static final String PREFS_ONESIGNAL_PUSH_TOKEN_LAST = "ONESIGNAL_PUSH_TOKEN_LAST";
    static final String PREFS_ONESIGNAL_PERMISSION_ACCEPTED_LAST = "ONESIGNAL_PERMISSION_ACCEPTED_LAST";
    static final String PREFS_GT_DO_NOT_SHOW_MISSING_GPS = "GT_DO_NOT_SHOW_MISSING_GPS";
    static final String PREFS_ONESIGNAL_SUBSCRIPTION = "ONESIGNAL_SUBSCRIPTION";
    static final String PREFS_ONESIGNAL_SYNCED_SUBSCRIPTION = "ONESIGNAL_SYNCED_SUBSCRIPTION";
    static final String PREFS_GT_REGISTRATION_ID = "GT_REGISTRATION_ID";

//    PLAYER PURCHASE KEYS
    static final String PREFS_PURCHASE_TOKENS = "purchaseTokens";
    static final String PREFS_EXISTING_PURCHASES = "ExistingPurchases";

    static ConcurrentHashMap<String,SharedPreferences> preferencesMap = new ConcurrentHashMap<>();
    //use a thread pool executor to execute disk writes
    private static final ScheduledThreadPoolExecutor prefsExecutor = new ScheduledThreadPoolExecutor(10);
    static {
        prefsExecutor.setThreadFactory(new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable runnable) {
                Thread newThread = new Thread(runnable);
                newThread.setName("ONESIGNAL_EXECUTOR_" + newThread.getId());
                return newThread;
            }
        });
    }

    private static class PrefsWriteRunnable implements Runnable {

        private String prefsName;
        private String prefKeyToWrite;
        private Object prefValueToWrite;

        PrefsWriteRunnable(String prefsName,
                                  String prefKeyToWrite,
                                  Object prefValueToWrite) {
            this.prefsName = prefsName;
            this.prefKeyToWrite = prefKeyToWrite;
            this.prefValueToWrite = prefValueToWrite;
        }

        @Override
        public void run() {
            SharedPreferences prefsToWrite = getSharedPrefsByName(prefsName);

            if(prefsToWrite != null) {
                SharedPreferences.Editor editor = prefsToWrite.edit();

                if(prefValueToWrite instanceof String) {
                    editor.putString(prefKeyToWrite,(String)prefValueToWrite);
                } else if(prefValueToWrite instanceof Boolean) {
                    editor.putBoolean(prefKeyToWrite, (Boolean)prefValueToWrite);
                } else if(prefValueToWrite instanceof Integer) {
                    editor.putInt(prefKeyToWrite, (Integer)prefValueToWrite);
                } else if(prefValueToWrite instanceof Long) {
                    editor.putLong(prefKeyToWrite, (Long)prefValueToWrite);
                }
                OneSignal.Log(OneSignal.LOG_LEVEL.INFO,
                        "updating prefs: " + prefsName + ", "+ prefKeyToWrite);

                editor.apply();
            }

        }
    }


    static void saveString(final String prefsName,final String key,final String value) {
        PrefsWriteRunnable saveStringRunnable = new PrefsWriteRunnable(prefsName, key, value);
        prefsExecutor.execute(saveStringRunnable);
    }

    static void saveBool(String prefsName, String key, boolean value) {
        PrefsWriteRunnable saveBoolRunnable = new PrefsWriteRunnable(prefsName, key, value);
        prefsExecutor.execute(saveBoolRunnable);
    }

    static void saveInt(String prefsName, String key, int value) {
        PrefsWriteRunnable saveBoolRunnable = new PrefsWriteRunnable(prefsName, key, value);
        prefsExecutor.execute(saveBoolRunnable);
    }

    static void saveLong(String prefsName, String key, long value) {
        PrefsWriteRunnable saveBoolRunnable = new PrefsWriteRunnable(prefsName,key,value);
        prefsExecutor.execute(saveBoolRunnable);
    }

    static boolean hasBool(String prefsName, String key) {
        SharedPreferences prefs = getSharedPrefsByName(prefsName);
        if(prefs != null)
            return getSharedPrefsByName(prefsName).contains(key);

        return false;
    }

    static boolean has(String prefsName, String key) {
        SharedPreferences prefs = getSharedPrefsByName(prefsName);
        if(prefs != null)
            return getSharedPrefsByName(prefsName).contains(key);

        return false;
    }

    static String getString(String prefsName, String key, String defValue) {
        SharedPreferences prefs = getSharedPrefsByName(prefsName);
        if(prefs != null)
            return prefs.getString(key, defValue);

        return defValue;
    }

    static boolean getBool(String prefsName, String key, boolean defValue) {
        SharedPreferences prefs = getSharedPrefsByName(prefsName);
        if(prefs != null)
            return prefs.getBoolean(key, defValue);

        return defValue;
    }

    static int getInt(String prefsName, String key, int defValue) {
        SharedPreferences prefs = getSharedPrefsByName(prefsName);
        if(prefs != null)
            return prefs.getInt(key, defValue);

        return defValue;
    }

    static long getLong(String prefsName, String key, long defValue) {
        SharedPreferences prefs = getSharedPrefsByName(prefsName);
        if(prefs != null)
            return prefs.getLong(key, defValue);

        return defValue;
    }

    static void remove(String prefsName, String key) {
        SharedPreferences prefs = getSharedPrefsByName(prefsName);
        if(prefs != null) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(key);
            editor.apply();
        }
    }

    private static synchronized SharedPreferences getSharedPrefsByName(String prefsName) {
        SharedPreferences prefs;
        if(OneSignal.appContext == null)
            return null;

        if(!preferencesMap.contains(prefsName)) {
            prefs = OneSignal.appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            preferencesMap.put(prefsName, prefs);
        }
        else
            prefs = preferencesMap.get(prefsName);

        return prefs;
    }

}
