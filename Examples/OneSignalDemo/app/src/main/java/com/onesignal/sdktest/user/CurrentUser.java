package com.onesignal.sdktest.user;

import android.content.Context;
import android.util.Log;

import com.onesignal.OneSignal;
import com.onesignal.sdktest.callback.EmailUpdateCallback;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.util.SharedPreferenceUtil;

public class CurrentUser {

    private static CurrentUser currentUser;

    public String getEmail() {
        if (OneSignal.getPermissionSubscriptionState() != null)
            return OneSignal
                .getPermissionSubscriptionState()
                .getEmailSubscriptionStatus()
                .getEmailAddress();
        return null;
    }

    public String getExternalUserId(Context context) {
        return SharedPreferenceUtil.getCachedUserExternalUserId(context);
    }

    public void setExternalUserId(Context context, String userId) {
        OneSignal.setExternalUserId(userId);
        SharedPreferenceUtil.cacheUserExternalUserId(context, userId);
    }

    public void setEmail(String email, final EmailUpdateCallback callback) {
        OneSignal.setEmail(email, new OneSignal.EmailUpdateHandler() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onFailure(OneSignal.EmailUpdateError error) {
                String errorMsg = error.getType() + ": " + error.getMessage();
                Log.e(Tag.ERROR, errorMsg);

                callback.onFailure();
            }
        });
    }

    public void removeEmail(final EmailUpdateCallback callback) {
        OneSignal.logoutEmail();
        callback.onSuccess();
    }

    public boolean isEmailSet() {
        return OneSignal
                .getPermissionSubscriptionState() != null && OneSignal
                .getPermissionSubscriptionState().getEmailSubscriptionStatus()
                .getEmailAddress() != null;
    }

    public boolean isExternalUserIdSet(Context context) {
        String userId = getExternalUserId(context);
        return userId != null && !userId.isEmpty();
    }

    public static CurrentUser getInstance() {
        if (currentUser == null) {
            currentUser = new CurrentUser();
        }
        return currentUser;
    }
}
