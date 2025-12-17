package com.onesignal.sdktest.model;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.onesignal.OneSignal;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.constant.Text;
import com.onesignal.sdktest.util.IntentTo;
import com.onesignal.sdktest.util.SharedPreferenceUtil;

public class SplashActivityViewModel implements ActivityViewModel {

    private IntentTo intentTo;

    private Context context;

    private boolean[] tasks = {false, false, false};

    @Override
    public Activity getActivity() {
        return (Activity) context;
    }

    @Override
    public AppCompatActivity getAppCompatActivity() {
        return (AppCompatActivity) context;
    }

    @Override
    public ActivityViewModel onActivityCreated(Context context) {
        this.context = context;
        intentTo = new IntentTo(context);
        setupOneSignalSDK();
        return this;
    }

    @Override
    public ActivityViewModel setupInterfaceElements() {
        return this;
    }

    @Override
    public void setupToolbar() {

    }

    @Override
    public void networkConnected() {

    }

    @Override
    public void networkDisconnected() {

    }

    private void setupOneSignalSDK() {
        boolean privacyConsent = true;

        OneSignal.setConsentRequired(privacyConsent);

        boolean isLocationShared = SharedPreferenceUtil.getCachedLocationSharedStatus(context);
        OneSignal.getLocation().setShared(isLocationShared);

        boolean isInAppMessagingPaused = SharedPreferenceUtil.getCachedInAppMessagingPausedStatus(context);
//        OneSignal.getInAppMessages().setPaused(isInAppMessagingPaused);

        Log.d(Tag.LOG_TAG, Text.PRIVACY_CONSENT_REQUIRED_SET + ": " + privacyConsent);

//        boolean isEmailCached = attemptSignIn(new EmailUpdateCallback() {
//            @Override
//            public void onSuccess() {
//                tasks[0] = true;
//                attemptEnterApplication();
//            }
//
//            @Override
//            public void onFailure() {
//                tasks[0] = true;
//                attemptEnterApplication();
//            }
//        });
//        if (!isEmailCached) {
            tasks[0] = true;
            attemptEnterApplication();
//        }

        new Thread() {
            public void run() {
                boolean isExternalUserIdCached = attemptExternalUserId();
                tasks[1] = true;

                attemptEnterApplication();
            }
        }.start();

        new Thread() {
            public void run() {
                boolean hasConsent = SharedPreferenceUtil.getUserPrivacyConsent(context);
                // TODO()
//                OneSignal.provideUserConsent(hasConsent);
                tasks[2] = true;

                attemptEnterApplication();
            }
        }.start();
    }

//    public boolean attemptSignIn(EmailUpdateCallback callback) {
//        boolean isEmailCached = SharedPreferenceUtil.exists(context, SharedPreferenceUtil.USER_EMAIL_SHARED_PREF);
//        if (isEmailCached) {
//            String email = SharedPreferenceUtil.getCachedUserEmail(context);
//            currentUser.setEmail(email, callback);
//        }
//        return isEmailCached;
//        return false;
//    }

    public boolean attemptExternalUserId() {
        boolean isExternalUserIdCached = SharedPreferenceUtil.exists(context, SharedPreferenceUtil.USER_EXTERNAL_USER_ID_SHARED_PREF);
        if (isExternalUserIdCached) {
            String externalUserId = SharedPreferenceUtil.getCachedUserExternalUserId(context);
//            currentUser.setExternalUserId(context, externalUserId);
        }
        return isExternalUserIdCached;
    }

    private void attemptEnterApplication() {
        for (boolean task : tasks) {
            if (!task)
                return;
        }

        ((Activity) context).runOnUiThread(() -> new Handler().postDelayed(() -> intentTo.mainActivity(), 1000));
    }

    @Override
    public void onNotificationPermissionChange(@Nullable boolean permission) {

    }
}
