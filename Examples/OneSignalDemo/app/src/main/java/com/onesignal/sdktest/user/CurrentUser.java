package com.onesignal.sdktest.user;

import android.content.Context;
import android.util.Log;

import com.onesignal.OSDeviceState;
import com.onesignal.OneSignal;
import com.onesignal.sdktest.callback.EmailUpdateCallback;
import com.onesignal.sdktest.callback.SMSUpdateCallback;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.util.SharedPreferenceUtil;

import org.json.JSONObject;

public class CurrentUser {

    private static CurrentUser currentUser;

    public String getEmail() {
        OSDeviceState deviceState = OneSignal.getDeviceState();
        if (deviceState != null)
            return deviceState.getEmailAddress();
        return null;
    }

    public String getSMSNumber() {
        OSDeviceState deviceState = OneSignal.getDeviceState();
        if (deviceState != null)
            return deviceState.getSMSNumber();
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

    public void setSMSNumber(String smsNumber, final SMSUpdateCallback callback) {
        OneSignal.setSMSNumber(smsNumber, new OneSignal.OSSMSUpdateHandler() {
            @Override
            public void onSuccess(JSONObject result) {
                callback.onSuccess();
            }

            @Override
            public void onFailure(OneSignal.OSSMSUpdateError error) {
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
        OSDeviceState deviceState = OneSignal.getDeviceState();
        return deviceState != null && deviceState.getEmailAddress() != null;
    }

    public boolean isSMSNumberSet() {
        OSDeviceState deviceState = OneSignal.getDeviceState();
        return deviceState != null && deviceState.getSMSNumber() != null;
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
