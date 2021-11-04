package com.onesignal;

import com.onesignal.influence.data.OSTrackerFactory;

class OSRemoteParamController {

    private OneSignalRemoteParams.Params remoteParams = null;

    OSRemoteParamController() {
    }

    /**
     * Save RemoteParams result of android_params request
     */
    void saveRemoteParams(OneSignalRemoteParams.Params remoteParams,
                          OSTrackerFactory trackerFactory,
                          OSSharedPreferences preferences,
                          OSLogger logger) {
        this.remoteParams = remoteParams;

        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_GT_FIREBASE_TRACKING_ENABLED,
                remoteParams.firebaseAnalytics
        );
        saveRestoreTTLFilter(remoteParams.restoreTTLFilter);
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_CLEAR_GROUP_SUMMARY_CLICK,
                remoteParams.clearGroupOnSummaryClick
        );
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                preferences.getOutcomesV2KeyName(),
                remoteParams.influenceParams.outcomesV2ServiceEnabled
        );

        saveReceiveReceiptEnabled(remoteParams.receiveReceiptEnabled);

        logger.debug("OneSignal saveInfluenceParams: " + remoteParams.influenceParams.toString());
        trackerFactory.saveInfluenceParams(remoteParams.influenceParams);

        if (remoteParams.disableGMSMissingPrompt != null)
            saveGMSMissingPromptDisable(remoteParams.disableGMSMissingPrompt);
        if (remoteParams.unsubscribeWhenNotificationsDisabled != null)
            saveUnsubscribeWhenNotificationsAreDisabled(remoteParams.unsubscribeWhenNotificationsDisabled);
        if (remoteParams.locationShared != null)
            OneSignal.startLocationShared(remoteParams.locationShared);
        if (remoteParams.requiresUserPrivacyConsent != null)
            savePrivacyConsentRequired(remoteParams.requiresUserPrivacyConsent);
    }

    boolean isRemoteParamsCallDone() {
        return remoteParams != null;
    }

    OneSignalRemoteParams.Params getRemoteParams() {
        return remoteParams;
    }

    boolean hasDisableGMSMissingPromptKey() {
        return remoteParams != null && remoteParams.disableGMSMissingPrompt != null;
    }

    boolean hasLocationKey() {
        return remoteParams != null && remoteParams.locationShared != null;
    }

    boolean hasPrivacyConsentKey() {
        return remoteParams != null && remoteParams.requiresUserPrivacyConsent != null;
    }

    boolean hasUnsubscribeNotificationKey() {
        return remoteParams != null && remoteParams.unsubscribeWhenNotificationsDisabled != null;
    }

    void clearRemoteParams() {
        remoteParams = null;
    }

    private void saveRestoreTTLFilter(boolean restoreTTLFilter) {
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_RESTORE_TTL_FILTER,
                remoteParams.restoreTTLFilter
        );
    }

    boolean isRestoreTTLFilterActive() {
        return OneSignalPrefs.getBool(OneSignalPrefs.PREFS_ONESIGNAL, OneSignalPrefs.PREFS_OS_RESTORE_TTL_FILTER, true);
    }

    private void saveReceiveReceiptEnabled(boolean receiveReceiptEnabled) {
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_RECEIVE_RECEIPTS_ENABLED,
                receiveReceiptEnabled
        );
    }

    boolean isReceiveReceiptEnabled() {
        return OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_RECEIVE_RECEIPTS_ENABLED,
                false
        );
    }

    boolean getFirebaseAnalyticsEnabled() {
        return OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_GT_FIREBASE_TRACKING_ENABLED,
                false);
    }

    boolean getClearGroupSummaryClick() {
        return OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_CLEAR_GROUP_SUMMARY_CLICK,
                true);
    }

    boolean unsubscribeWhenNotificationsAreDisabled() {
        return OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_UNSUBSCRIBE_WHEN_NOTIFICATIONS_DISABLED,
                true);
    }

    void saveUnsubscribeWhenNotificationsAreDisabled(boolean unsubscribe) {
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_UNSUBSCRIBE_WHEN_NOTIFICATIONS_DISABLED,
                unsubscribe
        );
    }

    boolean isGMSMissingPromptDisable() {
        return OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_DISABLE_GMS_MISSING_PROMPT,
                false);
    }

    void saveGMSMissingPromptDisable(boolean promptDisable) {
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_DISABLE_GMS_MISSING_PROMPT,
                promptDisable
        );
    }

    boolean isLocationShared() {
        return OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_LOCATION_SHARED,
                true);
    }

    void saveLocationShared(boolean shared) {
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_LOCATION_SHARED,
                shared);
    }

    boolean isPrivacyConsentRequired() {
        return OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_REQUIRES_USER_PRIVACY_CONSENT,
                false);
    }

    void savePrivacyConsentRequired(boolean required) {
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_REQUIRES_USER_PRIVACY_CONSENT,
                required);
    }

    boolean getSavedUserConsentStatus() {
        return OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_ONESIGNAL_USER_PROVIDED_CONSENT,
                false);
    }

    void saveUserConsentStatus(boolean consent) {
        OneSignalPrefs.saveBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_ONESIGNAL_USER_PROVIDED_CONSENT,
                consent);
    }
}
