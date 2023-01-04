package com.onesignal.sdktest.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.onesignal.sdktest.constant.Text;

public class SharedPreferenceUtil {

    private static final String APP_SHARED_PREFS = "com.onesignal.sdktest";

    public static final  String OS_APP_ID_SHARED_PREF = "OS_APP_ID_SHARED_PREF";
    private static final String PRIVACY_CONSENT_SHARED_PREF = "PRIVACY_CONSENT_SHARED_PREF";
    public static final String USER_EXTERNAL_USER_ID_SHARED_PREF = "USER_EXTERNAL_USER_ID_SHARED_PREF";
    private static final String LOCATION_SHARED_PREF = "LOCATION_SHARED_PREF";
    private static final String IN_APP_MESSAGING_PAUSED_PREF = "IN_APP_MESSAGING_PAUSED_PREF";

    private static SharedPreferences getSharedPreference(Context context) {
        return context.getSharedPreferences(APP_SHARED_PREFS, Context.MODE_PRIVATE);
    }

    public static boolean exists(Context context, String key) {
        return getSharedPreference(context).contains(key);
    }

    public static String getOneSignalAppId(Context context) {
        return getSharedPreference(context).getString(OS_APP_ID_SHARED_PREF, "77e32082-ea27-42e3-a898-c72e141824ef");
    }

    public static boolean getUserPrivacyConsent(Context context) {
        return getSharedPreference(context).getBoolean(PRIVACY_CONSENT_SHARED_PREF, false);
    }

    public static String getCachedUserExternalUserId(Context context) {
        return getSharedPreference(context).getString(USER_EXTERNAL_USER_ID_SHARED_PREF, Text.EMPTY);
    }

    public static boolean getCachedLocationSharedStatus(Context context) {
        return getSharedPreference(context).getBoolean(LOCATION_SHARED_PREF, false);
    }

    public static boolean getCachedInAppMessagingPausedStatus(Context context) {
        return getSharedPreference(context).getBoolean(IN_APP_MESSAGING_PAUSED_PREF, true);
    }

    public static void cacheOneSignalAppId(Context context, String appId) {
        getSharedPreference(context).edit().putString(OS_APP_ID_SHARED_PREF, appId).apply();
    }

    public static void cacheUserPrivacyConsent(Context context, boolean privacyConsent) {
        getSharedPreference(context).edit().putBoolean(PRIVACY_CONSENT_SHARED_PREF, privacyConsent).apply();
    }

    public static void cacheUserExternalUserId(Context context, String userId) {
        getSharedPreference(context).edit().putString(USER_EXTERNAL_USER_ID_SHARED_PREF, userId).apply();
    }

    public static void cacheLocationSharedStatus(Context context, boolean subscribed) {
        getSharedPreference(context).edit().putBoolean(LOCATION_SHARED_PREF, subscribed).apply();
    }

    public static void cacheInAppMessagingPausedStatus(Context context, boolean paused) {
        getSharedPreference(context).edit().putBoolean(IN_APP_MESSAGING_PAUSED_PREF, paused).apply();
    }
}
