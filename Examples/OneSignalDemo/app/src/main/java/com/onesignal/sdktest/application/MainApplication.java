package com.onesignal.sdktest.application;

import android.app.Application;
import android.util.Log;

import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.constant.Text;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);

        // OneSignal Initialization
        String appId = getString(R.string.onesignal_app_id);
        OneSignal.init(this, "REMOTE", appId);
        OneSignal.getCurrentOrNewInitBuilder().unsubscribeWhenNotificationsAreDisabled(true);

        OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);

        Log.d(Tag.DEBUG, Text.ONESIGNAL_SDK_INIT);
    }

}
