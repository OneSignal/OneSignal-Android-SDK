package com.onesignal;

import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

abstract class UserState {

    protected final int NOTIFICATION_TYPES_SUBSCRIBED = 1;
    protected final int NOTIFICATION_TYPES_NO_PERMISSION = 0;
    protected final int NOTIFICATION_TYPES_UNSUBSCRIBE = -2;

    private static final String[] LOCATION_FIELDS = new String[] { "lat", "long", "loc_acc", "loc_type", "loc_bg", "ad_id"};
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
            if (dependValues.optLong("loc_time_stamp") != changedTo.dependValues.getLong("loc_time_stamp")
                    || syncValues.optDouble("lat") != changedTo.syncValues.getDouble("lat")
                    || syncValues.optDouble("long") != changedTo.syncValues.getDouble("long")
                    || syncValues.optDouble("loc_acc") != changedTo.syncValues.getDouble("loc_acc")
                    || syncValues.optInt("loc_type ") != changedTo.syncValues.optInt("loc_type")) {
                changedTo.syncValues.put("loc_bg", changedTo.dependValues.opt("loc_bg"));
                return LOCATION_FIELDS_SET;
            }
        } catch (Throwable t) {}

        return null;
    }

    void setLocation(LocationGMS.LocationPoint point) {
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
            // This makes sure app_id is in all our REST calls.
            if (!sendJson.has("app_id"))
                sendJson.put("app_id", syncValues.optString("app_id"));
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

                if (subscribableStatus == NOTIFICATION_TYPES_UNSUBSCRIBE) {
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
            modifySyncValuesJsonArray("pkgs");

            OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_ONESIGNAL_USERSTATE_SYNCVALYES_ + persistKey, syncValues.toString());
            OneSignalPrefs.saveString(OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_ONESIGNAL_USERSTATE_DEPENDVALYES_ + persistKey, dependValues.toString());
        }
    }

    private void modifySyncValuesJsonArray(String baseKey) {
        if (!syncValues.has(baseKey + "_d") && syncValues.has(baseKey + "_d"))
            return;

        try {
            JSONArray orgArray = syncValues.has(baseKey) ? syncValues.getJSONArray(baseKey) : new JSONArray();
            JSONArray tempArray = new JSONArray();

            if (syncValues.has(baseKey + "_d")) {
                String remArrayStr = JSONUtils.toStringNE(syncValues.getJSONArray(baseKey + "_d"));
                for (int i = 0; i < orgArray.length(); i++)
                    if (!remArrayStr.contains(orgArray.getString(i)))
                        tempArray.put(orgArray.get(i));
            }
            else
                tempArray = orgArray;

            if (syncValues.has(baseKey + "_a")) {
                JSONArray newArray = syncValues.getJSONArray(baseKey + "_a");
                for (int i = 0; i < newArray.length(); i++)
                    tempArray.put(newArray.get(i));
            }

            syncValues.put(baseKey, tempArray);
            syncValues.remove(baseKey + "_a");
            syncValues.remove(baseKey + "_d");
        } catch (Throwable t) {}
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
