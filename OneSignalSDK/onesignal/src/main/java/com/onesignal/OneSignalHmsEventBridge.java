package com.onesignal;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.huawei.hms.push.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * If you have your own {@link com.huawei.hms.push.HmsMessageService} defined in your app please also
 * call {@link OneSignalHmsEventBridge#onNewToken} and {@link OneSignalHmsEventBridge#onMessageReceived}
 * as this is required for some OneSignal features.
 * If you don't have a class that extends from {@link com.huawei.hms.push.HmsMessageService}
 * or anther SDK / Library that handles HMS push then you don't need to use this class.
 * OneSignal automatically gets these events.
 */
public class OneSignalHmsEventBridge {

    public static final String HMS_TTL_KEY = "hms.ttl";
    public static final String HMS_SENT_TIME_KEY = "hms.sent_time";

    private static final AtomicBoolean firstToken = new AtomicBoolean(true);

    /**
     * Method used by last HMS push version 5.3.0.304 and upper
     */
    public static void onNewToken(@NonNull Context context, @NonNull String token, @Nullable Bundle bundle) {
        if (firstToken.compareAndSet(true, false)) {
            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "OneSignalHmsEventBridge onNewToken - HMS token: " + token + " Bundle: " + bundle);
            PushRegistratorHMS.fireCallback(token);
        } else {
            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "OneSignalHmsEventBridge ignoring onNewToken - HMS token: " + token + " Bundle: " + bundle);
        }
    }

    /**
     * This method is being deprecated
     * @see OneSignalHmsEventBridge#onNewToken(Context, String, Bundle)
     */
    @Deprecated
    public static void onNewToken(@NonNull Context context, @NonNull String token) {
        onNewToken(context, token, null);
    }

    public static void onMessageReceived(@NonNull Context context, @NonNull RemoteMessage message) {
        String data = message.getData();
        try {
            JSONObject messageDataJSON = new JSONObject(message.getData());
            if (message.getTtl() == 0)
                messageDataJSON.put(HMS_TTL_KEY, OSNotificationRestoreWorkManager.DEFAULT_TTL_IF_NOT_IN_PAYLOAD);
            else
                messageDataJSON.put(HMS_TTL_KEY, message.getTtl());
            if (message.getSentTime() == 0)
                messageDataJSON.put(HMS_SENT_TIME_KEY, OneSignal.getTime().getCurrentTimeMillis());
            else
                messageDataJSON.put(HMS_SENT_TIME_KEY, message.getSentTime());
            data = messageDataJSON.toString();
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "OneSignalHmsEventBridge error when trying to create RemoteMessage data JSON");
        }
        NotificationPayloadProcessorHMS.processDataMessageReceived(context, data);
    }
}
