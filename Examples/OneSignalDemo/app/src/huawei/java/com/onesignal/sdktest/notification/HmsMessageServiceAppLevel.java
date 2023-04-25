package com.onesignal.sdktest.notification;

import android.os.Bundle;
import android.util.Log;

import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;
import com.onesignal.notifications.bridges.OneSignalHmsEventBridge;
import com.onesignal.sdktest.constant.Tag;

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
        Log.d(Tag.LOG_TAG, "HmsMessageServiceAppLevel onNewToken refresh token:" + token + " bundle: " + bundle);

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.INSTANCE.onNewToken(this, token, bundle);
    }

    @Deprecated
    @Override
    public void onNewToken(String token) {
        Log.d(Tag.LOG_TAG, "HmsMessageServiceAppLevel onNewToken refresh token:" + token);

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
        Log.d(Tag.LOG_TAG, "HMS onMessageReceived: " + message);
        Log.d(Tag.LOG_TAG, "HMS onMessageReceived.ttl:" + message.getTtl());
        Log.d(Tag.LOG_TAG, "HMS onMessageReceived.data:" + message.getData());
        Log.d(Tag.LOG_TAG, "HMS onMessageReceived.title: " + message.getNotification().getTitle());
        Log.d(Tag.LOG_TAG, "HMS onMessageReceived.body: " + message.getNotification().getBody());
        Log.d(Tag.LOG_TAG, "HMS onMessageReceived.icon: " + message.getNotification().getIcon());
        Log.d(Tag.LOG_TAG, "HMS onMessageReceived.color: " + message.getNotification().getColor());
        Log.d(Tag.LOG_TAG, "HMS onMessageReceived.channelId: " + message.getNotification().getChannelId());
        Log.d(Tag.LOG_TAG, "HMS onMessageReceived.imageURL: " + message.getNotification().getImageUrl());
        Log.d(Tag.LOG_TAG, "HMS onMessageReceived.tag: " + message.getNotification().getTag());

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.INSTANCE.onMessageReceived(this, message);
    }
}
