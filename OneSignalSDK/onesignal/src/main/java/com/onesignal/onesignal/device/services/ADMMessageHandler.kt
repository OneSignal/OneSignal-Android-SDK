package com.onesignal.onesignal.device.services

import com.amazon.device.messaging.ADMMessageHandlerBase
import android.content.Intent
import com.onesignal.onesignal.OneSignal
import com.onesignal.onesignal.internal.notification.work.INotificationBundleProcessor
import com.onesignal.onesignal.logging.Logging

// WARNING: Do not pass 'this' to any methods as it will cause proguard build errors
//             when "proguard-android-optimize.txt" is used.
class ADMMessageHandler : ADMMessageHandlerBase("ADMMessageHandler") {

    override fun onMessage(intent: Intent) {
        val context = applicationContext
        val bundle = intent.extras

        val bundleProcessor = OneSignal.getService<INotificationBundleProcessor>()

        bundleProcessor.processBundleFromReceiver(context, bundle!!)
    }

    override fun onRegistered(newRegistrationId: String) {
        Logging.info("ADM registration ID: $newRegistrationId")

        // TODO: Implement
        //PushRegistratorADM.fireCallback(newRegistrationId)
    }

    override fun onRegistrationError(error: String) {
        Logging.error("ADM:onRegistrationError: $error")

        if ("INVALID_SENDER" == error)
            Logging.error("Please double check that you have a matching package name (NOTE: Case Sensitive), api_key.txt, and the apk was signed with the same Keystore and Alias.")

        // TODO: Implement
        //PushRegistratorADM.fireCallback(null)
    }

    override fun onUnregistered(info: String) {
        Logging.info("ADM:onUnregistered: $info")
    }
}