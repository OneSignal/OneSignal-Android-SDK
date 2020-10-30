package com.onesignal;

import org.json.JSONException;

class UserStatePush extends UserState {

    UserStatePush(String inPersistKey, boolean load) {
        super(inPersistKey, load);
    }

    @Override
    UserState newInstance(String persistKey) {
        return new UserStatePush(persistKey, false);
    }

    @Override
    protected void addDependFields() {
        try {
            putOnSyncValues("notification_types", getNotificationTypes());
        } catch (JSONException e) {}
    }

    private int getNotificationTypes() {
        int subscribableStatus = getDependValues().optInt("subscribableStatus", 1);
        if (subscribableStatus < PUSH_STATUS_UNSUBSCRIBE)
            return subscribableStatus;

        boolean androidPermission = getDependValues().optBoolean("androidPermission", true);
        if (!androidPermission)
            return PUSH_STATUS_NO_PERMISSION;

        boolean userSubscribePref = getDependValues().optBoolean("userSubscribePref", true);
        if (!userSubscribePref)
            return PUSH_STATUS_UNSUBSCRIBE;

        return PUSH_STATUS_SUBSCRIBED;
    }

    boolean isSubscribed() {
        return getNotificationTypes() > 0;
    }
}
