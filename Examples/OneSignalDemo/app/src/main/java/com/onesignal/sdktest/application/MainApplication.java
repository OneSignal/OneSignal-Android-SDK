package com.onesignal.sdktest.application;

import android.app.Application;

import com.onesignal.NotificationGenerationJob;
import com.onesignal.OSNotificationOpenedResult;
import com.onesignal.OneSignal;
import com.onesignal.sdktest.constant.Text;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);

        OneSignal.setAppContext(this);
        OneSignal.setNotificationWillShowInForegroundHandler(new OneSignal.NotificationWillShowInForegroundHandler() {
            @Override
            public void notificationWillShowInForeground(NotificationGenerationJob notifJob) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "App notification received!!!");

                notifJob.setNotificationDisplayType(OneSignal.OSNotificationDisplayOption.NOTIFICATION);
                notifJob.complete(false);
            }
        });

        OneSignal.setNotificationOpenedHandler(new OneSignal.NotificationOpenedHandler() {
            @Override
            public void notificationOpened(OSNotificationOpenedResult result) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Notification opened!!!");
            }
        });

        OneSignal.unsubscribeWhenNotificationsAreDisabled(true);
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, Text.ONESIGNAL_SDK_INIT);
    }

}
