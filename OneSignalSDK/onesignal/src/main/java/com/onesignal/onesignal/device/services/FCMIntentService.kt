package com.onesignal.onesignal.device.services

import android.app.IntentService
import android.content.Intent
import com.onesignal.FCMBroadcastReceiver
import com.onesignal.onesignal.OneSignal
import com.onesignal.onesignal.internal.notification.work.NotificationBundleProcessor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * This `IntentService` does the actual handling of the FCM message.
 * `FCMBroadcastReceiver` (a `WakefulBroadcastReceiver`) holds a
 * partial wake lock for this service while the service does its work. When the
 * service is finished, it calls `completeWakefulIntent()` to release the
 * wake lock.
 */
class FCMIntentService : IntentService("FCMIntentService") {
    init {
        setIntentRedelivery(true)
    }

    /**
     * Called when FCM message is received from Google or a notification is being restored
     * Even for ADM messages
     * Expect if a NotificationExtenderService is setup
     */
    override fun onHandleIntent(intent: Intent?) {
        val bundle = intent!!.extras ?: return
        OneSignal.initWithContext(this)
        val notificationBundleProcessor = OneSignal.getService<NotificationBundleProcessor>()

        val bundleReceiverCallback: NotificationBundleProcessor.ProcessBundleReceiverCallback =
            object : NotificationBundleProcessor.ProcessBundleReceiverCallback {
                override fun onBundleProcessed(processedResult: NotificationBundleProcessor.ProcessedBundleResult?) {
                    // Release the wake lock provided by the WakefulBroadcastReceiver.
                    FCMBroadcastReceiver.completeWakefulIntent(intent)
                }
            }

        val context = this
        notificationBundleProcessor.processBundleFromReceiver(
            context,
            bundle,
            bundleReceiverCallback
        )
    }
}