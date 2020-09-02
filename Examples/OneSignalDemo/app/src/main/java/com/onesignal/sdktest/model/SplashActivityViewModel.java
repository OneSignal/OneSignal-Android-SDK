package com.onesignal.sdktest.model;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.onesignal.OSEmailSubscriptionStateChanges;
import com.onesignal.OSPermissionStateChanges;
import com.onesignal.OSSubscriptionStateChanges;
import com.onesignal.OneSignal;
import com.onesignal.sdktest.callback.EmailUpdateCallback;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.constant.Text;
import com.onesignal.sdktest.user.CurrentUser;
import com.onesignal.sdktest.util.IntentTo;
import com.onesignal.sdktest.util.SharedPreferenceUtil;

public class SplashActivityViewModel implements ActivityViewModel {

    private CurrentUser currentUser;
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

        currentUser = CurrentUser.getInstance();
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

    @Override
    public void onOSEmailSubscriptionChanged(OSEmailSubscriptionStateChanges stateChanges) {

    }

    @Override
    public void onOSPermissionChanged(OSPermissionStateChanges stateChanges) {

    }

    @Override
    public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {

    }

    private void setupOneSignalSDK() {
        boolean privacyConsent = true;
        OneSignal.setRequiresUserPrivacyConsent(privacyConsent);

        boolean isLocationShared = SharedPreferenceUtil.getCachedLocationSharedStatus(context);
        OneSignal.setLocationShared(isLocationShared);

        boolean isInAppMessagingPaused = SharedPreferenceUtil.getCachedInAppMessagingPausedStatus(context);
        OneSignal.pauseInAppMessages(isInAppMessagingPaused);

        Log.d(Tag.DEBUG, Text.PRIVACY_CONSENT_REQUIRED_SET + ": " + privacyConsent);

        boolean isEmailCached = attemptSignIn(new EmailUpdateCallback() {
            @Override
            public void onSuccess() {
                tasks[0] = true;
                attemptEnterApplication();
            }

            @Override
            public void onFailure() {
                tasks[0] = true;
                attemptEnterApplication();
            }
        });
        if (!isEmailCached) {
            tasks[0] = true;
            attemptEnterApplication();
        }

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
                OneSignal.provideUserConsent(hasConsent);
                tasks[2] = true;

                attemptEnterApplication();
            }
        }.start();
    }

    public boolean attemptSignIn(EmailUpdateCallback callback) {
        boolean isEmailCached = SharedPreferenceUtil.exists(context, SharedPreferenceUtil.USER_EMAIL_SHARED_PREF);
        if (isEmailCached) {
            String email = SharedPreferenceUtil.getCachedUserEmail(context);
            currentUser.setEmail(email, callback);
        }
        return isEmailCached;
    }

    public boolean attemptExternalUserId() {
        boolean isExternalUserIdCached = SharedPreferenceUtil.exists(context, SharedPreferenceUtil.USER_EXTERNAL_USER_ID_SHARED_PREF);
        if (isExternalUserIdCached) {
            String externalUserId = SharedPreferenceUtil.getCachedUserExternalUserId(context);
            currentUser.setExternalUserId(context, externalUserId);
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

}
