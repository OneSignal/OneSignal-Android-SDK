package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

abstract class UserState {

    public static final int DEVICE_TYPE_ANDROID = 1;
    public static final int DEVICE_TYPE_FIREOS = 2;
    public static final int DEVICE_TYPE_EMAIL = 11;
    public static final int DEVICE_TYPE_HUAWEI = 13;

    public static final int PUSH_STATUS_SUBSCRIBED = 1;
    static final int PUSH_STATUS_NO_PERMISSION = 0;
    static final int PUSH_STATUS_UNSUBSCRIBE = -2;
    static final int PUSH_STATUS_MISSING_ANDROID_SUPPORT_LIBRARY = -3;
    static final int PUSH_STATUS_MISSING_FIREBASE_FCM_LIBRARY = -4;
    static final int PUSH_STATUS_OUTDATED_ANDROID_SUPPORT_LIBRARY = -5;
    static final int PUSH_STATUS_INVALID_FCM_SENDER_ID = -6;
    static final int PUSH_STATUS_OUTDATED_GOOGLE_PLAY_SERVICES_APP = -7;
    static final int PUSH_STATUS_FIREBASE_FCM_INIT_ERROR = -8;
    static final int PUSH_STATUS_FIREBASE_FCM_ERROR_SERVICE_NOT_AVAILABLE = -9;
    // -10 is a server side detection only from FCM that the app is no longer installed
    static final int PUSH_STATUS_FIREBASE_FCM_ERROR_IOEXCEPTION = -11;
    static final int PUSH_STATUS_FIREBASE_FCM_ERROR_MISC_EXCEPTION = -12;
    // -13 to -24 reserved for other platforms
    public static final int PUSH_STATUS_HMS_TOKEN_TIMEOUT = -25;
    // Most likely missing "client/app_id".
    // Check that there is "apply plugin: 'com.huawei.agconnect'" in your app/build.gradle
    public static final int PUSH_STATUS_HMS_ARGUMENTS_INVALID = -26;
    public static final int PUSH_STATUS_HMS_API_EXCEPTION_OTHER = -27;
    public static final int PUSH_STATUS_MISSING_HMS_PUSHKIT_LIBRARY = -28;

    private static final String[] LOCATION_FIELDS = new String[] { "lat", "long", "loc_acc", "loc_type", "loc_bg", "loc_time_stamp", "ad_id"};
    private static final Set<String> LOCATION_FIELDS_SET = new HashSet<>(Arrays.asList(LOCATION_FIELDS));

    // Object to synchronize on to prevent concurrent modifications on syncValues and dependValues
    private static final Object syncLock = new Object() {};

    private String persistKey;

    JSONObject dependValues, syncValues;

    UserState(String inPersistKey, boolean load) {
        persistKey = inPersistKey;
        if (load)
            loadState();
        else {
            dependValues = new JSONObject();
            syncValues = new JSONObject();
        }
    }

    abstract UserState newInstance(String persistKey);

    UserState deepClone(String persistKey) {
        UserState clonedUserState = newInstance(persistKey);

        try {
            clonedUserState.dependValues = new JSONObject(dependValues.toString());
            clonedUserState.syncValues = new JSONObject(syncValues.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return clonedUserState;
    }

    abstract protected void addDependFields();

    abstract boolean isSubscribed();

    private Set<String> getGroupChangeFields(UserState changedTo) {
        try {
            if (dependValues.optLong("loc_time_stamp") != changedTo.dependValues.getLong("loc_time_stamp")) {
                changedTo.syncValues.put("loc_bg", changedTo.dependValues.opt("loc_bg"));
                changedTo.syncValues.put("loc_time_stamp", changedTo.dependValues.opt("loc_time_stamp"));
                return LOCATION_FIELDS_SET;
            }
        } catch (Throwable t) {}

        return null;
    }

    void setLocation(LocationController.LocationPoint point) {
        try {
            syncValues.put("lat", point.lat);
            syncValues.put("long",point.log);
            syncValues.put("loc_acc", point.accuracy);
            syncValues.put("loc_type", point.type);
            dependValues.put("loc_bg", point.bg);
            dependValues.put("loc_time_stamp", point.timeStamp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void clearLocation() {
        try {
            syncValues.put("lat", null);
            syncValues.put("long", null);
            syncValues.put("loc_acc", null);
            syncValues.put("loc_type", null);

            syncValues.put("loc_bg", null);
            syncValues.put("loc_time_stamp", null);

            dependValues.put("loc_bg", null);
            dependValues.put("loc_time_stamp", null);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    JSONObject generateJsonDiff(UserState newState, boolean isSessionCall) {
        addDependFields(); newState.addDependFields();
        Set<String> includeFields = getGroupChangeFields(newState);
        JSONObject sendJson = generateJsonDiff(syncValues, newState.syncValues, null, includeFields);

        if (!isSessionCall && sendJson.toString().equals("{}"))
            return null;

        try {
            // app_id required for all REST API calls
            if (!sendJson.has("app_id"))
                sendJson.put("app_id", syncValues.optString("app_id"));
            if (syncValues.has("email_auth_hash"))
                sendJson.put("email_auth_hash", syncValues.optString("email_auth_hash"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return sendJson;
    }

    void set(String key, Object value) {
        try {
            syncValues.put(key, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void loadState() {
        // null if first run of a 2.0+ version.
        String dependValuesStr = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_ONESIGNAL_USERSTATE_DEPENDVALYES_ + persistKey,null);

        if (dependValuesStr == null) {
            dependValues = new JSONObject();
            try {
                int subscribableStatus;
                boolean userSubscribePref = true;
                // Convert 1.X SDK settings to 2.0+.
                if (persistKey.equals("CURRENT_STATE"))
                    subscribableStatus = OneSignalPrefs.getInt(OneSignalPrefs.PREFS_ONESIGNAL,
                            OneSignalPrefs.PREFS_ONESIGNAL_SUBSCRIPTION,1);
                else
                    subscribableStatus = OneSignalPrefs.getInt(OneSignalPrefs.PREFS_ONESIGNAL,
                            OneSignalPrefs.PREFS_ONESIGNAL_SYNCED_SUBSCRIPTION,1);

                if (subscribableStatus == PUSH_STATUS_UNSUBSCRIBE) {
                    subscribableStatus = 1;
                    userSubscribePref = false;
                }

                dependValues.put("subscribableStatus", subscribableStatus);
                dependValues.put("userSubscribePref", userSubscribePref);
            } catch (JSONException e) {}
        }
        else {
            try {
                dependValues = new JSONObject(dependValuesStr);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        String syncValuesStr = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_ONESIGNAL_USERSTATE_SYNCVALYES_ + persistKey,null);
        try {
            if (syncValuesStr == null) {
                syncValues = new JSONObject();
                String gtRegistrationId = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_GT_REGISTRATION_ID,null);
                syncValues.put("identifier", gtRegistrationId);
            }
            else
                syncValues = new JSONObject(syncValuesStr);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void persistState() {
        synchronized(syncLock) {
            OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_ONESIGNAL_USERSTATE_SYNCVALYES_ + persistKey, syncValues.toString());
            OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_ONESIGNAL_USERSTATE_DEPENDVALYES_ + persistKey, dependValues.toString());
        }
    }

    void persistStateAfterSync(JSONObject inDependValues, JSONObject inSyncValues) {
        if (inDependValues != null)
            generateJsonDiff(dependValues, inDependValues, dependValues, null);

        if (inSyncValues != null) {
            generateJsonDiff(syncValues, inSyncValues, syncValues, null);
            mergeTags(inSyncValues, null);
        }

        if (inDependValues != null || inSyncValues != null)
            persistState();
    }

    void mergeTags(JSONObject inSyncValues, JSONObject omitKeys) {
        synchronized (syncLock) {
            if (inSyncValues.has("tags")) {
                JSONObject newTags;
                if (syncValues.has("tags")) {
                    try {
                        newTags = new JSONObject(syncValues.optString("tags"));
                    } catch (JSONException e) {
                        newTags = new JSONObject();
                    }
                }
                else
                    newTags = new JSONObject();

                JSONObject curTags = inSyncValues.optJSONObject("tags");
                Iterator<String> keys = curTags.keys();
                String key;

                try {
                    while (keys.hasNext()) {
                        key = keys.next();
                        if ("".equals(curTags.optString(key)))
                            newTags.remove(key);
                        else if (omitKeys == null || !omitKeys.has(key))
                            newTags.put(key, curTags.optString(key));
                    }

                    if (newTags.toString().equals("{}"))
                        syncValues.remove("tags");
                    else
                        syncValues.put("tags", newTags);
                } catch (Throwable t) {}
            }
        }
    }

    private static JSONObject generateJsonDiff(JSONObject cur, JSONObject changedTo, JSONObject baseOutput, Set<String> includeFields) {
        synchronized (syncLock) {
            return JSONUtils.generateJsonDiff(cur, changedTo, baseOutput, includeFields);
        }
    }
}
