package com.onesignal.sdktest.application;

import android.app.Application;
import android.util.Log;

import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.constant.Text;
import com.onesignal.sdktest.util.OneSignalPrefs;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);

        // OneSignal Initialization
        String appId = OneSignalPrefs.getOneSignalAppId(this);
        // If cached app id is null use the default, otherwise use cached.
        if (appId == null) {
            appId = "380dc082-5231-4cc2-ab51-a03da5a0e4c2";
            OneSignalPrefs.cacheOneSignalAppId(this, appId);
        }
        OneSignal.setAppId(appId);
        OneSignal.setAppContext(this);

        OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
        OneSignal.pauseInAppMessages(true);

        Log.d(Tag.DEBUG, Text.ONESIGNAL_SDK_INIT);
    }

}
