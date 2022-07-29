package com.onesignal.usersdktest.application;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.multidex.MultiDexApplication;

import com.onesignal.onesignal.core.OneSignal;
import com.onesignal.onesignal.iam.IInAppMessage;
import com.onesignal.onesignal.iam.IInAppMessageLifecycleHandler;
import com.onesignal.onesignal.core.LogLevel;
import com.onesignal.onesignal.core.internal.logging.Logging;
import com.onesignal.onesignal.notification.INotification;
import com.onesignal.usersdktest.R;
import com.onesignal.usersdktest.constant.Tag;
import com.onesignal.usersdktest.constant.Text;
import com.onesignal.usersdktest.util.SharedPreferenceUtil;

import org.json.JSONObject;

public class MainApplication extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();

        Logging.setLogLevel(LogLevel.DEBUG);

        // OneSignal Initialization
        String appId = SharedPreferenceUtil.getOneSignalAppId(this);
        // If cached app id is null use the default, otherwise use cached.
        if (appId == null) {
            appId = getString(R.string.onesignal_app_id);
            SharedPreferenceUtil.cacheOneSignalAppId(this, appId);
        }

        OneSignal.setAppId(appId);
        OneSignal.initWithContext(this);

        OneSignal.getIam().setInAppMessageLifecycleHandler(new IInAppMessageLifecycleHandler() {
            @Override
            public void onWillDisplayInAppMessage(@NonNull IInAppMessage message) {
                Logging.log(LogLevel.VERBOSE, "MainApplication onWillDisplayInAppMessage");
            }

            @Override
            public void onDidDisplayInAppMessage(@NonNull IInAppMessage message) {
                Logging.log(LogLevel.VERBOSE, "MainApplication onDidDisplayInAppMessage");
            }

            @Override
            public void onWillDismissInAppMessage(@NonNull IInAppMessage message) {
                Logging.log(LogLevel.VERBOSE, "MainApplication onWillDismissInAppMessage");
            }

            @Override
            public void onDidDismissInAppMessage(@NonNull IInAppMessage message) {
                Logging.log(LogLevel.VERBOSE, "MainApplication onDidDismissInAppMessage");
            }
        });

        OneSignal.getNotifications().setNotificationOpenedHandler(result ->
                {
                    Logging.log(LogLevel.VERBOSE, "INotificationOpenedResult: " + result.toString());
                });

        OneSignal.getNotifications().setNotificationWillShowInForegroundHandler(notificationReceivedEvent ->
                {
                    Logging.log(LogLevel.VERBOSE, "NotificationWillShowInForegroundHandler fired!" +
                    " with notification event: " + notificationReceivedEvent.toString());

                    INotification notification = notificationReceivedEvent.getNotification();
                    JSONObject data = notification.getAdditionalData();

                    notificationReceivedEvent.complete(notification);
                });

        OneSignal.getNotifications().setUnsubscribeWhenNotificationsAreDisabled(true);
        OneSignal.getIam().setPaused(true);
        OneSignal.getLocation().setLocationShared(false);

        Log.d(Tag.DEBUG, Text.ONESIGNAL_SDK_INIT);
    }

}
