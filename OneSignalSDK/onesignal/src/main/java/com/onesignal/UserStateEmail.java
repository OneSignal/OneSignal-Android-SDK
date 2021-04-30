package com.onesignal;

class UserStateEmail extends UserState {
    private static final String EMAIL = "email";

    UserStateEmail(String inPersistKey, boolean load) {
        super(EMAIL + inPersistKey, load);
    }

    @Override
    UserState newInstance(String persistKey) {
        return new UserStateEmail(persistKey, false);
    }

    @Override
    protected void addDependFields() {
        // No depended fields for email
    }

    @Override
    boolean isSubscribed() {
        // No subscription setting, always true
        return true;
    }
}
