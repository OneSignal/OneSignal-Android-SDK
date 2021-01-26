package com.onesignal;

import java.util.ArrayList;
import java.util.List;

public class UserStateSMSSynchronizer extends UserStateSecondaryChannelSynchronizer {

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
    void logoutEmail() {
    }

    @Override
    void logoutSMS() {
        OneSignal.saveSMSId("");

        resetCurrentState();
        getToSyncUserState().removeFromSyncValues("identifier");
        List<String> keysToRemove = new ArrayList<>();
        keysToRemove.add("sms_auth_hash");
        keysToRemove.add("device_player_id");
        keysToRemove.add("external_user_id");
        getToSyncUserState().removeFromSyncValues(keysToRemove);
        getToSyncUserState().persistState();

        OneSignal.getSMSSubscriptionState().clearSMSAndId();
    }

    @Override
    protected int getDeviceType() {
        return UserState.DEVICE_TYPE_SMS;
    }

    @Override
    void fireUpdateSuccess() {

    }

    @Override
    void fireUpdateFailure() {

    }

    @Override
    void updateIdDependents(String id) {
        OneSignal.updateSMSIdDependents(id);
    }

    void setSMSNumber(String smsNumber, String smsAuthHash) {
    }
}
