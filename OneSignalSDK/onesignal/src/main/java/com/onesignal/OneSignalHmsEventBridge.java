package com.onesignal;

import android.content.Context;
import android.support.annotation.NonNull;

import com.huawei.hms.push.RemoteMessage;

/**
 * If you have your own {@link com.huawei.hms.push.HmsMessageService} defined in your app please also
 * call {@link OneSignalHmsEventBridge#onNewToken} and {@link OneSignalHmsEventBridge#onMessageReceived}
 * as this is required for some OneSignal features.
 * If you don't have a class that extends from {@link com.huawei.hms.push.HmsMessageService}
 * or anther SDK / Library that handles HMS push then you don't need to use this class.
 * OneSignal automatically gets these events.
 */
public class OneSignalHmsEventBridge {

    public static void onNewToken(@NonNull Context context, @NonNull String token) {
        OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "HmsMessageServiceOneSignal.onNewToken - HMS token: " + token);
        PushRegistratorHMS.fireCallback(token);
    }

    public static void onMessageReceived(@NonNull Context context, @NonNull RemoteMessage message) {
        NotificationPayloadProcessorHMS.processDataMessageReceived(context, message.getData());
    }
}
