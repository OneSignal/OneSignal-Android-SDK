package com.onesignal.sdktest.notification;

import android.os.Bundle;

import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalHmsEventBridge;

public class HmsMessageServiceAppLevel extends HmsMessageService {

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
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "HmsMessageServiceAppLevel onNewToken refresh token:" + token + " bundle: " + bundle);

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.onNewToken(this, token, bundle);
    }

    @Deprecated
    @Override
    public void onNewToken(String token) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "HmsMessageServiceAppLevel onNewToken refresh token:" + token);

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.onNewToken(this, token);
    }

    /**
     * This method is called in the following cases:
     * 1. "Data messages" - App process is alive when received.
     * 2. "Notification Message" - foreground_show = false and app is in focus
     * This method callback must be completed in 10 seconds. Start a new Job if more time is needed.
     *
     * @param message RemoteMessage
     */
    @Override
    public void onMessageReceived(RemoteMessage message) {
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "HMS onMessageReceived: " + message);
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "HMS onMessageReceived.ttl:" + message.getTtl());
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "HMS onMessageReceived.data:" + message.getData());
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "HMS onMessageReceived.title: " + message.getNotification().getTitle());
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "HMS onMessageReceived.body: " + message.getNotification().getBody());
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "HMS onMessageReceived.icon: " + message.getNotification().getIcon());
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "HMS onMessageReceived.color: " + message.getNotification().getColor());
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "HMS onMessageReceived.channelId: " + message.getNotification().getChannelId());
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "HMS onMessageReceived.imageURL: " + message.getNotification().getImageUrl());
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "HMS onMessageReceived.tag: " + message.getNotification().getTag());

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.onMessageReceived(this, message);
    }
}
