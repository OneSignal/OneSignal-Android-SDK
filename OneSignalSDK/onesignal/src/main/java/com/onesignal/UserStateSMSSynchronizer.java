package com.onesignal;

import androidx.annotation.Nullable;

import org.json.JSONObject;

public class UserStateSMSSynchronizer extends UserStateSynchronizer {

    UserStateSMSSynchronizer(OneSignalStateSynchronizer.UserStateSynchronizerType channel) {
        super(OneSignalStateSynchronizer.UserStateSynchronizerType.SMS);
    }

    @Override
    boolean getSubscribed() {
        return false;
    }

    @Override
    GetTagsResult getTags(boolean fromServer) {
        return null;
    }

    @Nullable
    @Override
    String getExternalId(boolean fromServer) {
        return null;
    }

    @Override
    protected UserState newUserState(String inPersistKey, boolean load) {
        return null;
    }

    @Override
    protected OneSignal.LOG_LEVEL getLogLevel() {
        return null;
    }

    @Override
    protected String getId() {
        return null;
    }

    @Override
    protected void onSuccessfulSync(JSONObject jsonField) {

    }

    @Override
    protected void fireEventsForUpdateFailure(JSONObject jsonFields) {

    }

    @Override
    protected void addOnSessionOrCreateExtras(JSONObject jsonBody) {

    }

    @Override
    protected void scheduleSyncToServer() {

    }

    @Override
    void updateState(JSONObject state) {

    }

    @Override
    void setSubscription(boolean enable) {

    }

    @Override
    public boolean getUserSubscribePreference() {
        return false;
    }

    @Override
    public void setPermission(boolean enable) {

    }

    @Override
    void updateIdDependents(String id) {

    }

    @Override
    void logoutEmail() {

    }
}
