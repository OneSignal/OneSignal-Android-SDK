package com.onesignal.sdktest.application;

import android.util.Log;

import androidx.multidex.MultiDexApplication;

import com.onesignal.OSInAppMessage;
import com.onesignal.OSNotification;
import com.onesignal.OSInAppMessageLifecycleHandler;
import com.onesignal.OneSignal;
import com.onesignal.debug.OneSignalLogEvent;
import com.onesignal.debug.OneSignalLogListener;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.constant.Text;
import com.onesignal.sdktest.util.SharedPreferenceUtil;

import org.json.JSONObject;

public class MainApplication extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();

        OSInAppMessageLifecycleHandler handler = new OSInAppMessageLifecycleHandler() {
            @Override
            public void onWillDisplayInAppMessage(OSInAppMessage message) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "MainApplication onWillDisplayInAppMessage");
            }
            @Override
            public void onDidDisplayInAppMessage(OSInAppMessage message) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "MainApplication onDidDisplayInAppMessage");
            }
            @Override
            public void onWillDismissInAppMessage(OSInAppMessage message) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "MainApplication onWillDismissInAppMessage");
            }
            @Override
            public void onDidDismissInAppMessage(OSInAppMessage message) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "MainApplication onDidDismissInAppMessage");
            }
        };

        OneSignal.setInAppMessageLifecycleHandler(handler);

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

        OneSignal.setNotificationOpenedHandler(result ->
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "OSNotificationOpenedResult result: " + result.toString()));

        OneSignal.setNotificationWillShowInForegroundHandler(notificationReceivedEvent -> {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "NotificationWillShowInForegroundHandler fired!" +
                    " with notification event: " + notificationReceivedEvent.toString());

            OSNotification notification = notificationReceivedEvent.getNotification();
            JSONObject data = notification.getAdditionalData();

            notificationReceivedEvent.complete(notification);
        });

        OneSignal.unsubscribeWhenNotificationsAreDisabled(true);
        OneSignal.pauseInAppMessages(true);
        OneSignal.setLocationShared(false);

        Log.d(Tag.DEBUG, Text.ONESIGNAL_SDK_INIT);
    }

    private void setupLogListener() {
        OneSignalLogListener logListener = new OneSignalLogListener() {
            @Override
            public void onLogEvent(OneSignalLogEvent event) {
                // App developer can send these logs to their backend, the
                // println code here is just to show it works.
                System.out.println(event.getEntry());
            }
        };

        OneSignal.addLogListener(logListener);
        // Remove can be called if you need to stop collecting logs.
        // OneSignal.removeLogListener(logListener);
    }

}
