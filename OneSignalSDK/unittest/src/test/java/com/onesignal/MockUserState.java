package com.onesignal;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MockUserState extends UserState {

    private static final String CURRENT_STATE = "CURRENT_STATE";
    private static final String LAT = "lat";
    private static final String LONG = "long";
    private static final String LOC_ACC = "loc_acc";
    private static final String LOC_TYPE = "loc_type";
    private static final String LOC_BG = "loc_bg";
    private static final String LOC_TIME_STAMP = "loc_time_stamp";
    private static final String AD_ID = "ad_id";
    private static final String IDENTIFIER = "identifier";
    private static final String SUBSCRIBABLE_STATUS = "subscribableStatus";
    private static final String UNSUBSCRIBE_PREF = "userSubscribePref";

    protected MockUserState currentUserState;

    protected final Object syncLock = new Object() {
    };
    private String persistKey;

    public JSONObject dependValues, syncValues;

    private static final String[] LOCATION_FIELDS = new String[]{LAT, LONG, LOC_ACC, LOC_TYPE, LOC_BG, LOC_TIME_STAMP, AD_ID};
    private static final Set<String> LOCATION_FIELDS_SET = new HashSet<>(Arrays.asList(LOCATION_FIELDS));

    public MockUserState(String inPersistKey, boolean load) {
        super(inPersistKey, load);
        persistKey = inPersistKey;
        if (load) {
            loadState();
        } else {
            dependValues = new JSONObject();
            syncValues = new JSONObject();
        }
    }

    private Set<String> getGroupChangeFields(MockUserState changedTo) {
        try {
            if (dependValues.optLong(LOC_TIME_STAMP) != changedTo.dependValues.getLong(LOC_TIME_STAMP)) {
                changedTo.syncValues.put(LOC_BG, changedTo.dependValues.opt(LOC_BG));
                changedTo.syncValues.put(LOC_TIME_STAMP, changedTo.dependValues.opt(LOC_TIME_STAMP));
                return LOCATION_FIELDS_SET;
            }
        } catch (Throwable t) {
        }

        return null;
    }

    void setLocation(LocationController.LocationPoint point) {
        try {
            syncValues.put(LAT, point.lat);
            syncValues.put(LONG, point.log);
            syncValues.put(LOC_ACC, point.accuracy);
            syncValues.put(LOC_TYPE, point.type);
            dependValues.put(LOC_BG, point.bg);
            dependValues.put(LOC_TIME_STAMP, point.timeStamp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void clearLocation() {
        try {
            syncValues.put(LAT, null);
            syncValues.put(LONG, null);
            syncValues.put(LOC_ACC, null);
            syncValues.put(LOC_TYPE, null);

            syncValues.put(LOC_BG, null);
            syncValues.put(LOC_TIME_STAMP, null);

            dependValues.put(LOC_BG, null);
            dependValues.put(LOC_TIME_STAMP, null);
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
                OneSignalPrefs.PREFS_ONESIGNAL_USERSTATE_DEPENDVALYES_ + persistKey, null);

        if (dependValuesStr == null) {
            dependValues = new JSONObject();
            try {
                int subscribableStatus;
                boolean userSubscribePref = true;
                // Convert 1.X SDK settings to 2.0+.
                if (persistKey.equals(CURRENT_STATE))
                    subscribableStatus = OneSignalPrefs.getInt(OneSignalPrefs.PREFS_ONESIGNAL,
                            OneSignalPrefs.PREFS_ONESIGNAL_SUBSCRIPTION, 1);
                else
                    subscribableStatus = OneSignalPrefs.getInt(OneSignalPrefs.PREFS_ONESIGNAL,
                            OneSignalPrefs.PREFS_ONESIGNAL_SYNCED_SUBSCRIPTION, 1);

                if (subscribableStatus == PUSH_STATUS_UNSUBSCRIBE) {
                    subscribableStatus = 1;
                    userSubscribePref = false;
                }

                dependValues.put(SUBSCRIBABLE_STATUS, subscribableStatus);
                dependValues.put(UNSUBSCRIBE_PREF, userSubscribePref);
            } catch (JSONException e) {
            }
        } else {
            try {
                dependValues = new JSONObject(dependValuesStr);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        String syncValuesStr = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_ONESIGNAL_USERSTATE_SYNCVALYES_ + persistKey, null);
        try {
            if (syncValuesStr == null) {
                syncValues = new JSONObject();
                String gtRegistrationId = OneSignalPrefs.getString(OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_GT_REGISTRATION_ID, null);
                syncValues.put(IDENTIFIER, gtRegistrationId);
            } else
                syncValues = new JSONObject(syncValuesStr);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    MockUserState getCurrentUserState() {
        synchronized (syncLock) {
            if (currentUserState == null)
                currentUserState = newInstance(CURRENT_STATE);
        }

        return currentUserState;
    }

    public JSONObject generateJsonDiff(JSONObject cur, JSONObject changedTo, JSONObject baseOutput, Set<String> includeFields) {
        synchronized (syncLock) {
            return OneSignalPackagePrivateHelper.JSONUtils.jsonDiff(cur, changedTo, baseOutput, includeFields);
        }
    }
}
