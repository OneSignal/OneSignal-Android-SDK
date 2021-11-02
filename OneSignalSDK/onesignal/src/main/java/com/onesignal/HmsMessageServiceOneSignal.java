package com.onesignal;

import android.os.Bundle;

import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;

/**
 * The hms:push library creates an instance of this service based on the
 * intent-filter action "com.huawei.push.action.MESSAGING_EVENT".
 *
 * WARNING: HMS only creates one {@link HmsMessageService} instance.
 * This means this class will not be used by the app in the following cases:
 *    1. Another push provider library / SDK is in the app.
 *       - Due to ordering in the AndroidManifest.xml OneSignal may or may not be the winner.
 *       - App needs to it's own {@link HmsMessageService} and call bridging / forwarding APIs
 *         on each SDK / library for both to work.
 *    2. The app has it's own  {@link HmsMessageService} class.
 * If either of these are true the app must have it's own {@link HmsMessageService} with AndroidManifest.xml
 * entries and call {@link OneSignalHmsEventBridge} for these methods. This is noted in the OneSignal SDK
 * Huawei setup guide.
 */
public class HmsMessageServiceOneSignal extends HmsMessageService {

    /**
     * When an app calls the getToken method to apply for a token from the server,
     * if the server does not return the token during current method calling, the server can return the token through this method later.
     * This method callback must be completed in 10 seconds. Otherwise, you need to start a new Job for callback processing.
     *
     * @param token token
     * @param bundle bundle
     */
    @Override
    public void onNewToken(String token, Bundle bundle) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "HmsMessageServiceOneSignal onNewToken refresh token:" + token);

        OneSignalHmsEventBridge.onNewToken(this, token, bundle);
    }

    @Deprecated
    @Override
    public void onNewToken(String token) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "HmsMessageServiceOneSignal onNewToken refresh token:" + token);

        OneSignalHmsEventBridge.onNewToken(this, token);
    }

    /**
     * This method is called in the following cases:
     *   1. "Data messages" - App process is alive when received.
     *   2. "Notification Message" - foreground_show = false and app is in focus
     * This method callback must be completed in 10 seconds, start a new Job if more time is needed.
     *   - This may only be a restriction for #1
     *   - 10 sec limit didn't seem to hit for #2, even when backgrounding the app after the method starts
     * @param message RemoteMessage
     */
    @Override
    public void onMessageReceived(RemoteMessage message) {
        OneSignalHmsEventBridge.onMessageReceived(this, message);
    }
}
