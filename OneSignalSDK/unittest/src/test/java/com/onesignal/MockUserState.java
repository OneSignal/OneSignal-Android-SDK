package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MockUserState extends UserState {

    protected MockUserState currentUserState;

    protected final Object syncLock = new Object() {};
    private String persistKey;

    public JSONObject dependValues, syncValues;

    private static final String[] LOCATION_FIELDS = new String[] { "lat", "long", "loc_acc", "loc_type", "loc_bg", "loc_time_stamp", "ad_id"};
    private static final Set<String> LOCATION_FIELDS_SET = new HashSet<>(Arrays.asList(LOCATION_FIELDS));

    public MockUserState(String inPersistKey, boolean load) {
        super(inPersistKey, load);
        persistKey = inPersistKey;
        if (load)
            loadState();
        else {
            dependValues = new JSONObject();
            syncValues = new JSONObject();
        }
    }

    private Set<String> getGroupChangeFields(MockUserState changedTo) {
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

    @Override
    public MockUserState newInstance(String persistKey) {
        synchronized (syncLock) {
            if (currentUserState == null) {
                MockUserState clonedUserState = newInstance(persistKey);

                try {
                    clonedUserState.dependValues = new JSONObject(dependValues.toString());
                    clonedUserState.syncValues = new JSONObject(syncValues.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                return clonedUserState;

            } else
                return currentUserState;
        }
    }

    @Override
    protected void addDependFields() {

    }

    @Override
    boolean isSubscribed() {
        return false;
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

    MockUserState getCurrentUserState() {
        synchronized (syncLock) {
            if (currentUserState == null)
                currentUserState = newInstance("CURRENT_STATE");
        }

        return currentUserState;
    }

    public JSONObject generateJsonDiff(JSONObject cur, JSONObject changedTo, JSONObject baseOutput, Set<String> includeFields) {
        synchronized (syncLock) {
            return OneSignalPackagePrivateHelper.JSONUtils.jsonDiff(cur, changedTo, baseOutput, includeFields);
        }
    }
}
