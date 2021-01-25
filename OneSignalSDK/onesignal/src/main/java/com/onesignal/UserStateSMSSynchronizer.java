package com.onesignal;

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
        return null;
    }

    @Override
    void logoutEmail() {

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

    }

    void setSMSNumber(String smsNumber, String smsAuthHash) {
    }
}
