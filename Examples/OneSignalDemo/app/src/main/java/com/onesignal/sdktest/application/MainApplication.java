package com.onesignal.sdktest.application;

import android.annotation.SuppressLint;
import android.os.StrictMode;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.multidex.MultiDexApplication;

import com.onesignal.Continue;
import com.onesignal.OneSignal;
import com.onesignal.inAppMessages.IInAppMessageClickListener;
import com.onesignal.inAppMessages.IInAppMessageClickEvent;
import com.onesignal.inAppMessages.IInAppMessageDidDismissEvent;
import com.onesignal.inAppMessages.IInAppMessageDidDisplayEvent;
import com.onesignal.inAppMessages.IInAppMessageLifecycleListener;
import com.onesignal.debug.LogLevel;
import com.onesignal.inAppMessages.IInAppMessageWillDismissEvent;
import com.onesignal.inAppMessages.IInAppMessageWillDisplayEvent;
import com.onesignal.notifications.IDisplayableNotification;
import com.onesignal.notifications.INotificationLifecycleListener;
import com.onesignal.notifications.INotificationWillDisplayEvent;
import com.onesignal.sdktest.BuildConfig;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.constant.Text;
import com.onesignal.sdktest.notification.OneSignalNotificationSender;
import com.onesignal.sdktest.util.SharedPreferenceUtil;
import com.onesignal.user.state.IUserStateObserver;
import com.onesignal.user.state.UserChangedState;
import com.onesignal.user.state.UserState;
import com.onesignal.user.subscriptions.IPushSubscriptionObserver;
import com.onesignal.user.subscriptions.PushSubscriptionChangedState;

import org.json.JSONObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainApplication extends MultiDexApplication {
    private static final int SLEEP_TIME_TO_MIMIC_ASYNC_OPERATION = 2000;

    public MainApplication() {
        StrictMode.allowThreadDiskWrites();
        StrictMode.allowThreadDiskReads();
    }

    @SuppressLint("NewApi")
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

        OneSignalNotificationSender.setAppId(appId);

        OneSignal.initWithContext(this, appId);


        Log.d(Tag.LOG_TAG, "❌ dev app adding push sub observer ");
        OneSignal.getUser().getPushSubscription().addObserver(new IPushSubscriptionObserver() {
            @Override
            public void onPushSubscriptionChange(@NonNull PushSubscriptionChangedState state) {
                Log.d(Tag.LOG_TAG, "❌ onPushSubscriptionChange " + state);
            }
        });
        Log.d(Tag.LOG_TAG, Text.ONESIGNAL_SDK_INIT);
    }

}
