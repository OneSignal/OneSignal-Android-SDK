package com.onesignal.sdktest.application;

import android.app.Application;
import android.util.Log;

import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.constant.Text;
import com.onesignal.sdktest.util.SharedPreferenceUtil;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);

        // OneSignal Initialization
        String appId = SharedPreferenceUtil.getOneSignalAppId(this);
        // If cached app id is null use the default, otherwise use cached.
        if (appId == null) {
            appId = getString(R.string.onesignal_app_id);
            SharedPreferenceUtil.cacheOneSignalAppId(this, appId);
        }
        OneSignal.setAppId(appId);
        OneSignal.setAppContext(this);

        OneSignal.setNotificationWillShowInForegroundHandler(notificationJob -> {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "AppNotificationWillShowInForeground fired!" +
                    " with AppNotificationWillShowInForegroundHandler: " + notificationJob.toString());

            notificationJob.setNotificationDisplayOption(OneSignal.OSNotificationDisplay.NOTIFICATION);
            notificationJob.complete();
        });

        OneSignal.unsubscribeWhenNotificationsAreDisabled(true);
        OneSignal.pauseInAppMessages(true);
        OneSignal.setLocationShared(false);

        Log.d(Tag.DEBUG, Text.ONESIGNAL_SDK_INIT);
    }

}
