package com.onesignal.sdktest.application;

import android.os.StrictMode;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.multidex.MultiDexApplication;

import com.onesignal.OneSignal;
import com.onesignal.inAppMessages.IInAppMessage;
import com.onesignal.inAppMessages.IInAppMessageAction;
import com.onesignal.inAppMessages.IInAppMessageClickHandler;
import com.onesignal.inAppMessages.IInAppMessageLifecycleHandler;
import com.onesignal.debug.LogLevel;
import com.onesignal.notifications.INotification;
import com.onesignal.sdktest.BuildConfig;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.constant.Text;
import com.onesignal.sdktest.util.SharedPreferenceUtil;

import org.json.JSONObject;

public class MainApplication extends MultiDexApplication {

    public MainApplication() {
        // run strict mode default in debug mode to surface any potential issues easier
        if(BuildConfig.DEBUG)
            StrictMode.enableDefaults();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        OneSignal.getDebug().setLogLevel(LogLevel.DEBUG);

        // OneSignal Initialization
        String appId = SharedPreferenceUtil.getOneSignalAppId(this);
        // If cached app id is null use the default, otherwise use cached.
        if (appId == null) {
            appId = getString(R.string.onesignal_app_id);
            SharedPreferenceUtil.cacheOneSignalAppId(this, appId);
        }

        OneSignal.initWithContext(this, appId);

        OneSignal.getInAppMessages().setInAppMessageLifecycleHandler(new IInAppMessageLifecycleHandler() {
            @Override
            public void onWillDisplayInAppMessage(@NonNull IInAppMessage message) {
                Log.v("MainApplication", "onWillDisplayInAppMessage");
            }

            @Override
            public void onDidDisplayInAppMessage(@NonNull IInAppMessage message) {
                Log.v("MainApplication", "onDidDisplayInAppMessage");
            }

            @Override
            public void onWillDismissInAppMessage(@NonNull IInAppMessage message) {
                Log.v("MainApplication", "onWillDismissInAppMessage");
            }

            @Override
            public void onDidDismissInAppMessage(@NonNull IInAppMessage message) {
                Log.v("MainApplication", "onDidDismissInAppMessage");
            }
        });

        OneSignal.getInAppMessages().setInAppMessageClickHandler(new IInAppMessageClickHandler() {
            @Override
            public void inAppMessageClicked(@Nullable IInAppMessageAction result) {
                Log.v("MainApplication", "inAppMessageClicked");
            }
        });

        OneSignal.getNotifications().setNotificationOpenedHandler(result ->
                {
                    Log.v("MainApplication", "INotificationOpenedResult: " + result);
                });

        OneSignal.getNotifications().setNotificationWillShowInForegroundHandler(notificationReceivedEvent ->
                {
                    Log.v("MainApplication", "NotificationWillShowInForegroundHandler fired!" +
                    " with notification event: " + notificationReceivedEvent.toString());

                    INotification notification = notificationReceivedEvent.getNotification();
                    JSONObject data = notification.getAdditionalData();

                    notificationReceivedEvent.complete(notification);
                });

        OneSignal.getNotifications().setUnsubscribeWhenNotificationsAreDisabled(true);
        OneSignal.getInAppMessages().setPaused(true);
        OneSignal.getLocation().setLocationShared(false);

        Log.d(Tag.DEBUG, Text.ONESIGNAL_SDK_INIT);
    }

}
