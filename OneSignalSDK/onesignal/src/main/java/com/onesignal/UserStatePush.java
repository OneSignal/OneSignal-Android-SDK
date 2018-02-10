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
            syncValues.put("notification_types", getNotificationTypes());
        } catch (JSONException e) {}
    }

    private int getNotificationTypes() {
        int subscribableStatus = dependValues.optInt("subscribableStatus", 1);
        if (subscribableStatus < NOTIFICATION_TYPES_UNSUBSCRIBE)
            return subscribableStatus;

        boolean androidPermission = dependValues.optBoolean("androidPermission", true);
        if (!androidPermission)
            return NOTIFICATION_TYPES_NO_PERMISSION;

        boolean userSubscribePref = dependValues.optBoolean("userSubscribePref", true);
        if (!userSubscribePref)
            return NOTIFICATION_TYPES_UNSUBSCRIBE;

        return NOTIFICATION_TYPES_SUBSCRIBED;
    }

    boolean isSubscribed() {
        return getNotificationTypes() > 0;
    }
}
