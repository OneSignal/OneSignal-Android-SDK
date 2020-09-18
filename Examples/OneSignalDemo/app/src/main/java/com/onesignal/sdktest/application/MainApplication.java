package com.onesignal.sdktest.application;

import android.app.Application;
import android.util.Log;

import com.onesignal.OSNotification;
import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.constant.Text;
import com.onesignal.sdktest.util.SharedPreferenceUtil;

import org.json.JSONException;
import org.json.JSONObject;

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
        OneSignal.initWithContext(this);

        OneSignal.setNotificationWillShowInForegroundHandler(notificationReceivedEvent -> {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "NotificationWillShowInForegroundHandler fired!" +
                    " with notification event: " + notificationReceivedEvent.toString());

            OSNotification notification = notificationReceivedEvent.getNotification();
            JSONObject data = notification.getAdditionalData();

            try {
                if (data.has("show") && data.getBoolean("show")) {
                    // Complete with a notification means it will show
                    notificationReceivedEvent.complete(notification);
                } else {
                    // Complete with null means don't show a notification.
                    notificationReceivedEvent.complete(null);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        });

        OneSignal.unsubscribeWhenNotificationsAreDisabled(true);
        OneSignal.pauseInAppMessages(true);
        OneSignal.setLocationShared(false);

        Log.d(Tag.DEBUG, Text.ONESIGNAL_SDK_INIT);
    }

}
