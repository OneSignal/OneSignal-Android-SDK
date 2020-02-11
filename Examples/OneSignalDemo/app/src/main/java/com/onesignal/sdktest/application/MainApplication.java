package com.onesignal.sdktest.application;

import android.app.Application;

import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.constant.Text;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);

        // OneSignal init
        String appId = getString(R.string.onesignal_app_id);
        OneSignal.setAppContext(this);
//        OneSignal.setNotificationWillShowInForegroundHandler(new OneSignal.NotificationWillShowInForegroundHandler() {
//            @Override
//            public void notificationWillShowInForeground(NotificationGenerationJob notifJob) {
//                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Notification received!!!");
//
//                notifJob.showNotification(OneSignal.OSInFocusDisplay.NOTIFICATION);
//            }
//        });
//
//        OneSignal.setNotificationOpenedHandler(new OneSignal.NotificationOpenedHandler() {
//            @Override
//            public void notificationOpened(OSNotificationOpenResult result) {
//                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Notification opened!!!");
//            }
//        });

        OneSignal.unsubscribeWhenNotificationsAreDisabled(true);
        OneSignal.pauseInAppMessages(true);
        OneSignal.setLocationShared(false);

        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, Text.ONESIGNAL_SDK_INIT);
    }
}
