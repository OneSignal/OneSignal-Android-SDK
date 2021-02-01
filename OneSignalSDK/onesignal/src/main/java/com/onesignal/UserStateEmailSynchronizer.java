package com.onesignal;

import com.onesignal.OneSignalStateSynchronizer.UserStateSynchronizerType;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class UserStateEmailSynchronizer extends UserStateSecondaryChannelSynchronizer {

    UserStateEmailSynchronizer() {
        super(UserStateSynchronizerType.EMAIL);
    }

    @Override
    protected UserState newUserState(String inPersistKey, boolean load) {
        return new UserStateEmail(inPersistKey, load);
    }

    @Override
    protected String getId() {
        return OneSignal.getEmailId();
    }

    @Override
    void saveChannelId(String id) {
        OneSignal.saveEmailId(id);
    }

    @Override
    void logoutEmail() {
        OneSignal.saveEmailId("");

        resetCurrentState();
        getToSyncUserState().removeFromSyncValues(IDENTIFIER);
        List<String> keysToRemove = new ArrayList<>();
        keysToRemove.add(EMAIL_AUTH_HASH_KEY);
        keysToRemove.add(DEVICE_PLAYER_ID);
        keysToRemove.add(EXTERNAL_USER_ID);
        getToSyncUserState().removeFromSyncValues(keysToRemove);
        getToSyncUserState().persistState();

        OneSignal.getEmailSubscriptionState().clearEmailAndId();
    }

    @Override
    void logoutSMS() {

    }

    @Override
    protected String getChannelKey() {
        return EMAIL_KEY;
    }

    @Override
    protected String getAuthHashKey() {
        return EMAIL_AUTH_HASH_KEY;
    }

    @Override
    protected int getDeviceType() {
        return UserState.DEVICE_TYPE_EMAIL;
    }

    @Override
    void fireUpdateSuccess(JSONObject result) {
        OneSignal.fireEmailUpdateSuccess();
    }

    @Override
    void fireUpdateFailure() {
        OneSignal.fireEmailUpdateFailure();
    }

    @Override
    void updateIdDependents(String id) {
        OneSignal.updateEmailIdDependents(id);
    }

}
