package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class UserState {

    // Object to synchronize on to prevent concurrent modifications on syncValues and dependValues
    private static final Object LOCK = new Object();

    public static final String TAGS = "tags";
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

    private String persistKey;

    private JSONObject dependValues, syncValues;

    public ImmutableJSONObject getDependValues() {
        try {
            return new ImmutableJSONObject(getDependValuesCopy());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new ImmutableJSONObject();
    }

    void setDependValues(JSONObject dependValues) {
        synchronized (LOCK) {
            this.dependValues = dependValues;
        }
    }

    JSONObject getDependValuesCopy() throws JSONException {
        synchronized (LOCK) {
            return new JSONObject(dependValues.toString());
        }
    }

    public ImmutableJSONObject getSyncValues() {
        try {
            return new ImmutableJSONObject(getSyncValuesCopy());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new ImmutableJSONObject();
    }

    public JSONObject getSyncValuesCopy() throws JSONException {
        synchronized (LOCK) {
            return new JSONObject(syncValues.toString());
        }
    }

    public void setSyncValues(JSONObject syncValues) {
        synchronized (LOCK) {
            this.syncValues = syncValues;
        }
    }

    UserState(String inPersistKey, boolean load) {
        persistKey = inPersistKey;
        if (load) {
            loadState();
        } else {
            dependValues = new JSONObject();
            syncValues = new JSONObject();
        }
    }

    abstract UserState newInstance(String persistKey);

    UserState deepClone(String persistKey) {
        UserState clonedUserState = newInstance(persistKey);

        try {
            clonedUserState.dependValues = getDependValuesCopy();
            clonedUserState.syncValues = getSyncValuesCopy();
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

                HashMap<String, Object> syncValuesToPut = new HashMap<>();

                syncValuesToPut.put("loc_bg", changedTo.dependValues.opt("loc_bg"));
                syncValuesToPut.put("loc_time_stamp", changedTo.dependValues.opt("loc_time_stamp"));

                putValues(changedTo.syncValues, syncValuesToPut);

                return LOCATION_FIELDS_SET;
            }
        } catch (Throwable t) {}

        return null;
    }

    void putOnSyncValues(String key, Object value) throws JSONException {
        synchronized (LOCK) {
            syncValues.put(key, value);
        }
    }


    void putOnDependValues(String key, Object value) throws JSONException {
        synchronized (LOCK) {
            dependValues.put(key, value);
        }
    }

    private void putValues(JSONObject jsonObject, HashMap<String, Object> values) throws JSONException {
        synchronized (LOCK) {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                jsonObject.put(entry.getKey(), entry.getValue());
            }
        }
    }

    void removeFromSyncValues(String key) {
        synchronized (LOCK) {
            syncValues.remove(key);
        }
    }

    void removeFromSyncValues(List<String> keys) {
        synchronized (LOCK) {
            for (String key : keys) {
                syncValues.remove(key);
            }
        }
    }

    void removeFromDependValues(String key) {
        synchronized (LOCK) {
            dependValues.remove(key);
        }
    }

    void removeFromDependValues(List<String> keys) {
        synchronized (LOCK) {
            for (String key : keys) {
                dependValues.remove(key);
            }
        }
    }

    void setLocation(LocationController.LocationPoint point) {
        try {
            HashMap<String, Object> syncValuesToPut = new HashMap<>();
            syncValuesToPut.put("lat", point.lat);
            syncValuesToPut.put("long",point.log);
            syncValuesToPut.put("loc_acc", point.accuracy);
            syncValuesToPut.put("loc_type", point.type);
            putValues(syncValues, syncValuesToPut);

            HashMap<String, Object> dependValuesToPut = new HashMap<>();
            dependValuesToPut.put("loc_bg", point.bg);
            dependValuesToPut.put("loc_time_stamp", point.timeStamp);
            putValues(dependValues, dependValuesToPut);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void clearLocation() {
        try {
            HashMap<String, Object> syncValuesToPut = new HashMap<>();
            syncValuesToPut.put("lat", null);
            syncValuesToPut.put("long", null);
            syncValuesToPut.put("loc_acc", null);
            syncValuesToPut.put("loc_type", null);
            syncValuesToPut.put("loc_bg", null);
            syncValuesToPut.put("loc_time_stamp", null);
            putValues(syncValues, syncValuesToPut);

            HashMap<String, Object> dependValuesToPut = new HashMap<>();
            dependValuesToPut.put("loc_bg", null);
            dependValuesToPut.put("loc_time_stamp", null);
            putValues(dependValues, dependValuesToPut);
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
            if (syncValues.has("external_user_id_auth_hash"))
                sendJson.put("external_user_id_auth_hash", syncValues.optString("external_user_id_auth_hash"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return sendJson;
    }

    private void loadState() {
        // null if first run of a 2.0+ version.
        String dependValuesStr = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_ONESIGNAL_USERSTATE_DEPENDVALYES_ + persistKey,null);

        if (dependValuesStr == null) {
            setDependValues(new JSONObject());
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

                HashMap<String, Object> dependValuesToPut = new HashMap<>();
                dependValuesToPut.put("subscribableStatus", subscribableStatus);
                dependValuesToPut.put("userSubscribePref", userSubscribePref);
                putValues(dependValues, dependValuesToPut);
            } catch (JSONException e) {}
        } else {
            try {
                JSONObject dependValues = new JSONObject(dependValuesStr);
                setDependValues(dependValues);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        String syncValuesStr = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_ONESIGNAL_USERSTATE_SYNCVALYES_ + persistKey,null);
        try {
            JSONObject syncValues;
            if (syncValuesStr == null) {
                syncValues = new JSONObject();
                String gtRegistrationId = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_GT_REGISTRATION_ID,null);
                syncValues.put("identifier", gtRegistrationId);
            } else {
                syncValues = new JSONObject(syncValuesStr);
            }

            setSyncValues(syncValues);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void persistState() {
        synchronized(LOCK) {
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
        if (!inSyncValues.has(TAGS))
            return;

        try {
            JSONObject syncValues = getSyncValuesCopy();
            JSONObject newTags;
            if (syncValues.has(TAGS)) {
                try {
                    newTags = new JSONObject(syncValues.optString(TAGS));
                } catch (JSONException e) {
                    newTags = new JSONObject();
                }
            } else {
                newTags = new JSONObject();
            }
            JSONObject curTags = inSyncValues.optJSONObject(TAGS);
            Iterator<String> keys = curTags.keys();
            String key;

            while (keys.hasNext()) {
                key = keys.next();
                if ("".equals(curTags.optString(key)))
                    newTags.remove(key);
                else if (omitKeys == null || !omitKeys.has(key))
                    newTags.put(key, curTags.optString(key));
            }

            synchronized (LOCK) {
                if (newTags.toString().equals("{}"))
                    this.syncValues.remove(TAGS);
                else
                    this.syncValues.put(TAGS, newTags);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    JSONObject generateJsonDiffFromIntoSyncValued(JSONObject changedTo, Set<String> includeFields) {
        synchronized (LOCK) {
            return JSONUtils.generateJsonDiff(syncValues, changedTo, syncValues, includeFields);
        }
    }

    JSONObject generateJsonDiffFromSyncValued(UserState changedTo, Set<String> includeFields) {
        synchronized (LOCK) {
            return JSONUtils.generateJsonDiff(syncValues, changedTo.syncValues, null, includeFields);
        }
    }

    JSONObject generateJsonDiffFromIntoDependValues(JSONObject changedTo, Set<String> includeFields) {
        synchronized (LOCK) {
            return JSONUtils.generateJsonDiff(dependValues, changedTo, dependValues, includeFields);
        }
    }

    JSONObject generateJsonDiffFromDependValues(UserState changedTo, Set<String> includeFields) {
        synchronized (LOCK) {
            return JSONUtils.generateJsonDiff(dependValues, changedTo.dependValues, null, includeFields);
        }
    }

    private static JSONObject generateJsonDiff(JSONObject cur, JSONObject changedTo, JSONObject baseOutput, Set<String> includeFields) {
        synchronized (LOCK) {
            return JSONUtils.generateJsonDiff(cur, changedTo, baseOutput, includeFields);
        }
    }

    @Override
    public String toString() {
        return "UserState{" +
                "persistKey='" + persistKey + '\'' +
                ", dependValues=" + dependValues +
                ", syncValues=" + syncValues +
                '}';
    }
}
