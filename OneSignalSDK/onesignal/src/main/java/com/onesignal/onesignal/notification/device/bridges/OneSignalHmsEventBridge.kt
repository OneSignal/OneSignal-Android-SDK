package com.onesignal.onesignal.notification.device.bridges

import android.content.Context
import android.os.Bundle
import com.huawei.hms.push.RemoteMessage
import com.onesignal.onesignal.core.OneSignal
import com.onesignal.onesignal.core.internal.common.time.ITime
import com.onesignal.onesignal.notification.internal.NotificationConstants
import com.onesignal.onesignal.notification.internal.work.NotificationPayloadProcessorHMS
import com.onesignal.onesignal.core.internal.logging.Logging
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * If you have your own [com.huawei.hms.push.HmsMessageService] defined in your app please also
 * call [OneSignalHmsEventBridge.onNewToken] and [OneSignalHmsEventBridge.onMessageReceived]
 * as this is required for some OneSignal features.
 * If you don't have a class that extends from [com.huawei.hms.push.HmsMessageService]
 * or anther SDK / Library that handles HMS push then you don't need to use this class.
 * OneSignal automatically gets these events.
 */
object OneSignalHmsEventBridge {
    const val HMS_TTL_KEY = "hms.ttl"
    const val HMS_SENT_TIME_KEY = "hms.sent_time"
    private val firstToken = AtomicBoolean(true)

    /**
     * Method used by last HMS push version 5.3.0.304 and upper
     */
    fun onNewToken(context: Context, token: String, bundle: Bundle?) {
        if (firstToken.compareAndSet(true, false)) {
            Logging.info("OneSignalHmsEventBridge onNewToken - HMS token: $token Bundle: $bundle")
            // TODO: Implement
//            PushRegistratorHMS.fireCallback(token)
        } else {
            Logging.info("OneSignalHmsEventBridge ignoring onNewToken - HMS token: $token Bundle: $bundle")
        }
    }

    /**
     * This method is being deprecated
     * @see OneSignalHmsEventBridge.onNewToken
     */
    @Deprecated("")
    fun onNewToken(context: Context, token: String) {
        onNewToken(context, token, null)
    }

    fun onMessageReceived(context: Context, message: RemoteMessage) {
        OneSignal.initWithContext(context)

        var time = OneSignal.getService<ITime>()
        var data = message.data
        try {
            val messageDataJSON = JSONObject(message.data)
            if (message.ttl == 0)
                messageDataJSON.put(HMS_TTL_KEY, NotificationConstants.DEFAULT_TTL_IF_NOT_IN_PAYLOAD)
            else
                messageDataJSON.put(HMS_TTL_KEY, message.ttl)

            if (message.sentTime == 0L)
                messageDataJSON.put(HMS_SENT_TIME_KEY, time.currentTimeMillis)
            else
                messageDataJSON.put(HMS_SENT_TIME_KEY, message.sentTime)

            data = messageDataJSON.toString()
        } catch (e: JSONException) {
            Logging.error("OneSignalHmsEventBridge error when trying to create RemoteMessage data JSON")
        }

        NotificationPayloadProcessorHMS.processDataMessageReceived(context, data)
    }
}