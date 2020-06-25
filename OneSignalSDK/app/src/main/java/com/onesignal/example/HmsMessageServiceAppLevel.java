package com.onesignal.example;

import android.util.Log;

import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;
import com.onesignal.OneSignalHmsEventBridge;

public class HmsMessageServiceAppLevel extends HmsMessageService {

    /**
     * When an app calls the getToken method to apply for a token from the server,
     * if the server does not return the token during current method calling, the server can return the token through this method later.
     * This method callback must be completed in 10 seconds. Otherwise, you need to start a new Job for callback processing.
     * @param token token
     */
    @Override
    public void onNewToken(String token) {
        Log.e("HMS", "onNewToken refresh token:" + token);

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.onNewToken(this, token);
    }

    /**
     * This method is called in the following cases:
     *   1. "Data messages" - App process is alive when received.
     *   2. "Notification Message" - foreground_show = false and app is in focus
     * This method callback must be completed in 10 seconds. Start a new Job if more time is needed.
     *
     * @param message RemoteMessage
     */
    @Override
    public void onMessageReceived(RemoteMessage message) {
        Log.e("HMS", "onMessageReceived: " + message);
        Log.e("HMS", "onMessageReceived:ttl:" + message.getTtl());
        Log.e("HMS", "onMessageReceived.data" + message.getData());

        Log.e("HMS", "onMessageReceived.notification.title: " + message.getNotification().getTitle());
        Log.e("HMS", "onMessageReceived.notification.body: " + message.getNotification().getBody());
        Log.e("HMS", "onMessageReceived.notification.icon: " + message.getNotification().getIcon());
        Log.e("HMS", "onMessageReceived.notification.color: " + message.getNotification().getColor());
        Log.e("HMS", "onMessageReceived.notification.channelId: " + message.getNotification().getChannelId());
        Log.e("HMS", "onMessageReceived.notification.imageURL: " + message.getNotification().getImageUrl());
        Log.e("HMS", "onMessageReceived.notification.tag: " + message.getNotification().getTag());

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.onMessageReceived(this, message);
    }
}
