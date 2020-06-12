package com.onesignal;

import com.huawei.hms.push.HmsMessageService;

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

}
