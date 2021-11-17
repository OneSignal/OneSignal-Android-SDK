package com.onesignal;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class UserStateSMSSynchronizer extends UserStateSecondaryChannelSynchronizer {

    UserStateSMSSynchronizer() {
        super(OneSignalStateSynchronizer.UserStateSynchronizerType.SMS);
    }

    @Override
    protected UserState newUserState(String inPersistKey, boolean load) {
        return new UserStateSMS(inPersistKey, load);
    }

    @Override
    protected String getId() {
        return OneSignal.getSMSId();
    }

    @Override
    void saveChannelId(String id) {
        OneSignal.saveSMSId(id);
    }

    @Override
    void logoutChannel() {
        saveChannelId("");

        resetCurrentState();
        getToSyncUserState().removeFromSyncValues(IDENTIFIER);
        List<String> keysToRemove = new ArrayList<>();
        keysToRemove.add(SMS_AUTH_HASH_KEY);
        keysToRemove.add(DEVICE_PLAYER_ID);
        keysToRemove.add(EXTERNAL_USER_ID);
        getToSyncUserState().removeFromSyncValues(keysToRemove);
        getToSyncUserState().persistState();

        OneSignal.getSMSSubscriptionState().clearSMSAndId();
    }

    @Override
    protected String getChannelKey() {
        return SMS_NUMBER_KEY;
    }

    @Override
    protected String getAuthHashKey() {
        return SMS_AUTH_HASH_KEY;
    }

    @Override
    protected int getDeviceType() {
        return UserState.DEVICE_TYPE_SMS;
    }

    @Override
    void fireUpdateSuccess(JSONObject result) {
        OneSignal.fireSMSUpdateSuccess(result);
    }

    @Override
    void fireUpdateFailure() {
        OneSignal.fireSMSUpdateFailure();
    }

    @Override
    void updateIdDependents(String id) {
        OneSignal.updateSMSIdDependents(id);
    }

}
