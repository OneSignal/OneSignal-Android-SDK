package com.onesignal.example.notification

import android.os.Bundle
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import com.onesignal.notifications.bridges.OneSignalHmsEventBridge
import com.onesignal.example.util.DemoLog

/**
 * HMS Message Service for handling Huawei Push notifications.
 * This service forwards all push events to the OneSignal SDK.
 * 
 * Note: This is the Huawei flavor-specific implementation.
 */
class HmsMessageServiceAppLevel : HmsMessageService() {

    /**
     * When an app calls the getToken method to apply for a token from the server,
     * if the server does not return the token during current method calling,
     * the server can return the token through this method later.
     * This method callback must be completed in 10 seconds.
     * Otherwise, you need to start a new Job for callback processing.
     */
    override fun onNewToken(token: String, bundle: Bundle) {
        DemoLog.d("HmsMessageServiceAppLevel onNewToken refresh token: $token bundle: $bundle")

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.onNewToken(this, token, bundle)
    }

    @Deprecated("Deprecated in Java")
    override fun onNewToken(token: String) {
        DemoLog.d("HmsMessageServiceAppLevel onNewToken refresh token: $token")

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
        DemoLog.d("HMS onMessageReceived: $message")
        DemoLog.d("HMS onMessageReceived.ttl: ${message.ttl}")
        DemoLog.d("HMS onMessageReceived.data: ${message.data}")
        
        message.notification?.let { notification ->
            DemoLog.d("HMS onMessageReceived.title: ${notification.title}")
            DemoLog.d("HMS onMessageReceived.body: ${notification.body}")
            DemoLog.d("HMS onMessageReceived.icon: ${notification.icon}")
            DemoLog.d("HMS onMessageReceived.color: ${notification.color}")
            DemoLog.d("HMS onMessageReceived.channelId: ${notification.channelId}")
            DemoLog.d("HMS onMessageReceived.imageURL: ${notification.imageUrl}")
            DemoLog.d("HMS onMessageReceived.tag: ${notification.tag}")
        }

        // Forward event on to OneSignal SDK
        OneSignalHmsEventBridge.onMessageReceived(this, message)
    }
}
