package com.onesignal.influence;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.onesignal.OSSharedPreferences;
import com.onesignal.OneSignalRemoteParams;
import com.onesignal.influence.model.OSInfluenceType;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * Setter and Getter of Notifications received
 */
class OSInfluenceDataRepository {

    // OUTCOMES KEYS
    // Outcomes Influence Ids
    protected static final String PREFS_OS_LAST_ATTRIBUTED_NOTIFICATION_OPEN = "PREFS_OS_LAST_ATTRIBUTED_NOTIFICATION_OPEN";
    protected static final String PREFS_OS_LAST_NOTIFICATIONS_RECEIVED = "PREFS_OS_LAST_NOTIFICATIONS_RECEIVED";
    protected static final String PREFS_OS_LAST_IAMS_RECEIVED = "PREFS_OS_LAST_IAMS_RECEIVED";
    // Outcomes Influence params
    protected static final String PREFS_OS_NOTIFICATION_LIMIT = "PREFS_OS_NOTIFICATION_LIMIT";
    protected static final String PREFS_OS_IAM_LIMIT = "PREFS_OS_IAM_LIMIT";
    protected static final String PREFS_OS_NOTIFICATION_INDIRECT_ATTRIBUTION_WINDOW = "PREFS_OS_INDIRECT_ATTRIBUTION_WINDOW";
    protected static final String PREFS_OS_IAM_INDIRECT_ATTRIBUTION_WINDOW = "PREFS_OS_IAM_INDIRECT_ATTRIBUTION_WINDOW";
    // Outcomes Influence enable params
    protected static final String PREFS_OS_DIRECT_ENABLED = "PREFS_OS_DIRECT_ENABLED";
    protected static final String PREFS_OS_INDIRECT_ENABLED = "PREFS_OS_INDIRECT_ENABLED";
    protected static final String PREFS_OS_UNATTRIBUTED_ENABLED = "PREFS_OS_UNATTRIBUTED_ENABLED";
    // Outcomes Channel Influence types
    protected static final String PREFS_OS_OUTCOMES_CURRENT_NOTIFICATION_INFLUENCE = "PREFS_OS_OUTCOMES_CURRENT_SESSION";
    protected static final String PREFS_OS_OUTCOMES_CURRENT_IAM_INFLUENCE = "PREFS_OS_OUTCOMES_CURRENT_IAM_INFLUENCE";

    private OSSharedPreferences preferences;

    public OSInfluenceDataRepository(OSSharedPreferences preferences) {
        this.preferences = preferences;
    }

    /**
     * Cache a influence type enum for Notification as a string
     */
    void cacheNotificationInfluenceType(@NonNull OSInfluenceType influenceType) {
        preferences.saveString(
                preferences.getPreferencesName(),
                PREFS_OS_OUTCOMES_CURRENT_NOTIFICATION_INFLUENCE,
                influenceType.toString()
        );
    }

    /**
     * Get the current cached influence type string, convert it to the influence type enum, and return it
     */
    @NonNull
    OSInfluenceType getNotificationCachedInfluenceType() {
        String influenceType = preferences.getString(
                preferences.getPreferencesName(),
                PREFS_OS_OUTCOMES_CURRENT_NOTIFICATION_INFLUENCE,
                OSInfluenceType.UNATTRIBUTED.toString()
        );
        return OSInfluenceType.fromString(influenceType);
    }

    /**
     * Cache a influence type enum for IAM as a string
     */
    void cacheIAMInfluenceType(@NonNull OSInfluenceType influenceType) {
        preferences.saveString(
                preferences.getPreferencesName(),
                PREFS_OS_OUTCOMES_CURRENT_IAM_INFLUENCE,
                influenceType.toString()
        );
    }

    /**
     * Get the current cached influence type string, convert it to the influence type enum, and return it
     */
    @NonNull
    OSInfluenceType getIAMCachedInfluenceType() {
        String influenceType = preferences.getString(
                preferences.getPreferencesName(),
                PREFS_OS_OUTCOMES_CURRENT_IAM_INFLUENCE,
                OSInfluenceType.UNATTRIBUTED.toString()
        );
        return OSInfluenceType.fromString(influenceType);
    }

    /**
     * Cache attributed notification opened
     */
    void cacheNotificationOpenId(@Nullable String id) {
        preferences.saveString(
                preferences.getPreferencesName(),
                PREFS_OS_LAST_ATTRIBUTED_NOTIFICATION_OPEN,
                id
        );
    }

    /**
     * Get the current cached notification id, null if not direct
     */
    @Nullable
    String getCachedNotificationOpenId() {
        return preferences.getString(
                preferences.getPreferencesName(),
                PREFS_OS_LAST_ATTRIBUTED_NOTIFICATION_OPEN,
                null
        );
    }

    void saveNotifications(@NonNull JSONArray notifications) {
        preferences.saveString(
                preferences.getPreferencesName(),
                PREFS_OS_LAST_NOTIFICATIONS_RECEIVED,
                notifications.toString());
    }

    void saveIAMs(@NonNull JSONArray iams) {
        preferences.saveString(
                preferences.getPreferencesName(),
                PREFS_OS_LAST_IAMS_RECEIVED,
                iams.toString());
    }

    JSONArray getLastNotificationsReceivedData() throws JSONException {
        String notificationsReceived = preferences.getString(
                preferences.getPreferencesName(),
                PREFS_OS_LAST_NOTIFICATIONS_RECEIVED,
                "[]");
        return notificationsReceived != null ? new JSONArray(notificationsReceived) : new JSONArray();
    }

    JSONArray getLastIAMsReceivedData() throws JSONException {
        String iamReceived = preferences.getString(
                preferences.getPreferencesName(),
                PREFS_OS_LAST_IAMS_RECEIVED,
                "[]");
        return iamReceived != null ? new JSONArray(iamReceived) : new JSONArray();
    }

    int getNotificationLimit() {
        return preferences.getInt(
                preferences.getPreferencesName(),
                PREFS_OS_NOTIFICATION_LIMIT,
                OneSignalRemoteParams.DEFAULT_NOTIFICATION_LIMIT
        );
    }

    int getIAMLimit() {
        return preferences.getInt(
                preferences.getPreferencesName(),
                PREFS_OS_IAM_LIMIT,
                OneSignalRemoteParams.DEFAULT_NOTIFICATION_LIMIT
        );
    }

    int getNotificationIndirectAttributionWindow() {
        return preferences.getInt(
                preferences.getPreferencesName(),
                PREFS_OS_NOTIFICATION_INDIRECT_ATTRIBUTION_WINDOW,
                OneSignalRemoteParams.DEFAULT_INDIRECT_ATTRIBUTION_WINDOW
        );
    }

    int getIAMIndirectAttributionWindow() {
        return preferences.getInt(
                preferences.getPreferencesName(),
                PREFS_OS_IAM_INDIRECT_ATTRIBUTION_WINDOW,
                OneSignalRemoteParams.DEFAULT_INDIRECT_ATTRIBUTION_WINDOW
        );
    }

    boolean isDirectInfluenceEnabled() {
        return preferences.getBool(
                preferences.getPreferencesName(),
                PREFS_OS_DIRECT_ENABLED,
                false
        );
    }

    boolean isIndirectInfluenceEnabled() {
        return preferences.getBool(
                preferences.getPreferencesName(),
                PREFS_OS_INDIRECT_ENABLED,
                false
        );
    }

    boolean isUnattributedInfluenceEnabled() {
        return preferences.getBool(
                preferences.getPreferencesName(),
                PREFS_OS_UNATTRIBUTED_ENABLED,
                false
        );
    }

    void saveInfluenceParams(OneSignalRemoteParams.InfluenceParams influenceParams) {
        preferences.saveBool(
                preferences.getPreferencesName(),
                PREFS_OS_DIRECT_ENABLED,
                influenceParams.isDirectEnabled()
        );
        preferences.saveBool(
                preferences.getPreferencesName(),
                PREFS_OS_INDIRECT_ENABLED,
                influenceParams.isIndirectEnabled()
        );
        preferences.saveBool(
                preferences.getPreferencesName(),
                PREFS_OS_UNATTRIBUTED_ENABLED,
                influenceParams.isUnattributedEnabled()
        );
        preferences.saveInt(
                preferences.getPreferencesName(),
                PREFS_OS_NOTIFICATION_LIMIT,
                influenceParams.getNotificationLimit()
        );
        preferences.saveInt(
                preferences.getPreferencesName(),
                PREFS_OS_NOTIFICATION_INDIRECT_ATTRIBUTION_WINDOW,
                influenceParams.getIndirectNotificationAttributionWindow()
        );
        preferences.saveInt(
                preferences.getPreferencesName(),
                PREFS_OS_IAM_LIMIT,
                influenceParams.getIamLimit()
        );
        preferences.saveInt(
                preferences.getPreferencesName(),
                PREFS_OS_IAM_INDIRECT_ATTRIBUTION_WINDOW,
                influenceParams.getIndirectIAMAttributionWindow()
        );
    }

}
