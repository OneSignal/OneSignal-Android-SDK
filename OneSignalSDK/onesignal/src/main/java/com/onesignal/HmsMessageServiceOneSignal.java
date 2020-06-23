package com.onesignal;

import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;

public class HmsMessageServiceOneSignal extends HmsMessageService {

    /**
     * When an app calls the getToken method to apply for a token from the server,
     * if the server does not return the token during current method calling, the server can return the token through this method later.
     * This method callback must be completed in 10 seconds. Otherwise, you need to start a new Job for callback processing.
     * @param token token
     */
    @Override
    public void onNewToken(String token) {
        OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "HmsMessageServiceOneSignal.onNewToken - HMS token: " + token);
        PushRegistratorHMS.fireCallback(token);
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
        NotificationPayloadProcessorHMS.processDataMessageReceived(this, message.getData());
    }
}
