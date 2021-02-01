package com.onesignal;

import com.onesignal.OneSignalStateSynchronizer.UserStateSynchronizerType;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class UserStateEmailSynchronizer extends UserStateSecondaryChannelSynchronizer {

    private static final String EMAIL_KEY = "email";
    private static final String EMAIL_AUTH_HASH_KEY = "email_auth_hash";

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
    void logoutEmail() {
        OneSignal.saveEmailId("");

        resetCurrentState();
        getToSyncUserState().removeFromSyncValues(IDENTIFIER);
        List<String> keysToRemove = new ArrayList<>();
        keysToRemove.add(EMAIL_AUTH_HASH_KEY);
        keysToRemove.add("device_player_id");
        keysToRemove.add("external_user_id");
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

    void setEmail(String email, String emailAuthHash) {
        UserState userState = getUserStateForModification();
        ImmutableJSONObject syncValues = userState.getSyncValues();

        boolean noChange = email.equals(syncValues.optString(IDENTIFIER)) &&
                syncValues.optString(getAuthHashKey()).equals(emailAuthHash == null ? "" : emailAuthHash);
        if (noChange) {
            fireUpdateSuccess(null);
            return;
        }

        String existingEmail = syncValues.optString(IDENTIFIER, null);

        if (existingEmail == null)
            setNewSession();

        try {
            JSONObject emailJSON = new JSONObject();
            emailJSON.put(IDENTIFIER, email);

            if (emailAuthHash != null)
                emailJSON.put(getAuthHashKey(), emailAuthHash);

            if (emailAuthHash == null) {
                if (existingEmail != null && !existingEmail.equals(email)) {
                    OneSignal.saveEmailId("");
                    resetCurrentState();
                    setNewSession();
                }
            }

            userState.generateJsonDiffFromIntoSyncValued(emailJSON, null);
            scheduleSyncToServer();
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
    }

}
