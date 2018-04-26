package com.onesignal;

/**
 * Created by bradhesse on 4/25/18.
 */

import android.content.Context;
import com.onesignal.OneSignal.NotificationOpenedHandler;
import com.onesignal.OneSignal.NotificationReceivedHandler;

class DelayedConsentInitializationParameters {
    public Context context;
    public String googleProjectNumber;
    public String appId;
    public NotificationOpenedHandler openedHandler;
    public NotificationReceivedHandler receivedHandler;

    DelayedConsentInitializationParameters(Context delayContext, String delayGoogleProjectNumber, String delayAppId, NotificationOpenedHandler delayOpenedHandler, NotificationReceivedHandler delayReceivedHandler) {
        this.context = delayContext;
        this.googleProjectNumber = delayGoogleProjectNumber;
        this.appId = delayAppId;
        this.openedHandler = delayOpenedHandler;
        this.receivedHandler = delayReceivedHandler;
    }
}
