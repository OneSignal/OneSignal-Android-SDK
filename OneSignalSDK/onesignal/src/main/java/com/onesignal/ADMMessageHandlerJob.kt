package com.onesignal

import android.content.Context
import android.content.Intent
import com.amazon.device.messaging.ADMMessageHandlerJobBase

class ADMMessageHandlerJob : ADMMessageHandlerJobBase() {

    override fun onMessage(context: Context?, intent: Intent?) {
        val bundle = intent?.extras

        val bundleReceiverCallback = object : NotificationBundleProcessor.ProcessBundleReceiverCallback {
            override fun onBundleProcessed(processedResult: NotificationBundleProcessor.ProcessedBundleResult?) {
                // TODO: Figure out the correct replacement or usage of completeWakefulIntent method
                //      FCMBroadcastReceiver.completeWakefulIntent(intent);

                processedResult?.let {
                    if (it.processed()) return
                }

                val payload = NotificationBundleProcessor.bundleAsJSONObject(bundle)
                val notification = OSNotification(payload)

                val notificationJob = OSNotificationGenerationJob(context).apply {
                    this.jsonPayload = payload
                    this.context = context
                    this.notification = notification
                }

                NotificationBundleProcessor.processJobForDisplay(notificationJob, true)
            }
        }

        NotificationBundleProcessor.processBundleFromReceiver(context, bundle, bundleReceiverCallback)
    }

    override fun onRegistered(context: Context?, newRegistrationId: String?) {
        OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "ADM registration ID: $newRegistrationId")
        PushRegistratorADM.fireCallback(newRegistrationId)
    }

    override fun onUnregistered(context: Context?, registrationId: String?) {
        OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "ADM:onUnregistered: $registrationId")
    }

    override fun onRegistrationError(context: Context?, error: String?) {
        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "ADM:onRegistrationError: $error")
        if ("INVALID_SENDER" == error) OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Please double check that you have a matching package name (NOTE: Case Sensitive), api_key.txt, and the apk was signed with the same Keystore and Alias.")

        PushRegistratorADM.fireCallback(null)
    }
}