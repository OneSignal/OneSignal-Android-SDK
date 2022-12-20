package com.onesignal.sdktest.notification;

import android.os.Bundle;
import android.util.Log;

import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;
import com.onesignal.notifications.bridges.OneSignalHmsEventBridge;

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
        Log.d("MainApplication", "HmsMessageServiceAppLevel onNewToken refresh token:" + token + " bundle: " + bundle);

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.INSTANCE.onNewToken(this, token, bundle);
    }

    @Deprecated
    @Override
    public void onNewToken(String token) {
        Log.d("MainApplication", "HmsMessageServiceAppLevel onNewToken refresh token:" + token);

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.INSTANCE.onNewToken(this, token);
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
        Log.d("MainApplication", "HMS onMessageReceived: " + message);
        Log.d("MainApplication", "HMS onMessageReceived.ttl:" + message.getTtl());
        Log.d("MainApplication", "HMS onMessageReceived.data:" + message.getData());
        Log.d("MainApplication", "HMS onMessageReceived.title: " + message.getNotification().getTitle());
        Log.d("MainApplication", "HMS onMessageReceived.body: " + message.getNotification().getBody());
        Log.d("MainApplication", "HMS onMessageReceived.icon: " + message.getNotification().getIcon());
        Log.d("MainApplication", "HMS onMessageReceived.color: " + message.getNotification().getColor());
        Log.d("MainApplication", "HMS onMessageReceived.channelId: " + message.getNotification().getChannelId());
        Log.d("MainApplication", "HMS onMessageReceived.imageURL: " + message.getNotification().getImageUrl());
        Log.d("MainApplication", "HMS onMessageReceived.tag: " + message.getNotification().getTag());

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.INSTANCE.onMessageReceived(this, message);
    }
}
