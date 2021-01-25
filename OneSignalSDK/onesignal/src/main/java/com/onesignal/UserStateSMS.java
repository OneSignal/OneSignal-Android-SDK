package com.onesignal;

class UserStateSMS extends UserState {

    private static final String SMS = "sms";

    UserStateSMS(String inPersistKey, boolean load) {
        super(SMS + inPersistKey, load);
    }

    @Override
    UserState newInstance(String persistKey) {
        return new UserStateSMS(persistKey, false);
    }

    @Override
    protected void addDependFields() {
        // No depended fields for sms
    }

    @Override
    boolean isSubscribed() {
        // No subscription setting, always true
        return true;
    }
}
