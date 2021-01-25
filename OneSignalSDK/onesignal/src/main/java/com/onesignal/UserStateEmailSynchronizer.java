package com.onesignal;

import com.onesignal.OneSignalStateSynchronizer.UserStateSynchronizerType;

import org.json.JSONException;
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
    void logoutEmail() {
        OneSignal.saveEmailId("");

        resetCurrentState();
        getToSyncUserState().removeFromSyncValues("identifier");
        List<String> keysToRemove = new ArrayList<>();
        keysToRemove.add("email_auth_hash");
        keysToRemove.add("device_player_id");
        keysToRemove.add("external_user_id");
        getToSyncUserState().removeFromSyncValues(keysToRemove);
        getToSyncUserState().persistState();

        OneSignal.getEmailSubscriptionState().clearEmailAndId();
    }

    @Override
    protected int getDeviceType() {
        return UserState.DEVICE_TYPE_EMAIL;
    }

    @Override
    void fireUpdateSuccess() {
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

        boolean noChange = email.equals(syncValues.optString("identifier")) &&
                syncValues.optString("email_auth_hash").equals(emailAuthHash == null ? "" : emailAuthHash);
        if (noChange) {
            fireUpdateSuccess();
            return;
        }

        String existingEmail = syncValues.optString("identifier", null);

        if (existingEmail == null)
            setNewSession();

        try {
            JSONObject emailJSON = new JSONObject();
            emailJSON.put("identifier", email);

            if (emailAuthHash != null)
                emailJSON.put("email_auth_hash", emailAuthHash);

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
