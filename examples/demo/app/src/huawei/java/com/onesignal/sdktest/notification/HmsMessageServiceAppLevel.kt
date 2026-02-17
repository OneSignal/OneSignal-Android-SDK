package com.onesignal.sdktest.notification

import android.os.Bundle
import android.util.Log
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import com.onesignal.notifications.bridges.OneSignalHmsEventBridge

/**
 * HMS Message Service for handling Huawei Push notifications.
 * This service forwards all push events to the OneSignal SDK.
 * 
 * Note: This is the Huawei flavor-specific implementation.
 */
class HmsMessageServiceAppLevel : HmsMessageService() {

    companion object {
        private const val TAG = "OneSignalHMS"
    }

    /**
     * When an app calls the getToken method to apply for a token from the server,
     * if the server does not return the token during current method calling,
     * the server can return the token through this method later.
     * This method callback must be completed in 10 seconds.
     * Otherwise, you need to start a new Job for callback processing.
     */
    override fun onNewToken(token: String, bundle: Bundle) {
        Log.d(TAG, "HmsMessageServiceAppLevel onNewToken refresh token: $token bundle: $bundle")

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.onNewToken(this, token, bundle)
    }

    @Deprecated("Deprecated in Java")
    override fun onNewToken(token: String) {
        Log.d(TAG, "HmsMessageServiceAppLevel onNewToken refresh token: $token")

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.onNewToken(this, token)
    }

    /**
     * This method is called in the following cases:
     * 1. "Data messages" - App process is alive when received.
     * 2. "Notification Message" - foreground_show = false and app is in focus
     * This method callback must be completed in 10 seconds.
     * Start a new Job if more time is needed.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "HMS onMessageReceived: $message")
        Log.d(TAG, "HMS onMessageReceived.ttl: ${message.ttl}")
        Log.d(TAG, "HMS onMessageReceived.data: ${message.data}")
        
        message.notification?.let { notification ->
            Log.d(TAG, "HMS onMessageReceived.title: ${notification.title}")
            Log.d(TAG, "HMS onMessageReceived.body: ${notification.body}")
            Log.d(TAG, "HMS onMessageReceived.icon: ${notification.icon}")
            Log.d(TAG, "HMS onMessageReceived.color: ${notification.color}")
            Log.d(TAG, "HMS onMessageReceived.channelId: ${notification.channelId}")
            Log.d(TAG, "HMS onMessageReceived.imageURL: ${notification.imageUrl}")
            Log.d(TAG, "HMS onMessageReceived.tag: ${notification.tag}")
        }

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.onMessageReceived(this, message)
    }
}
