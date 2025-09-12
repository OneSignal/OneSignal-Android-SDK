package com.onesignal.notifications.services

import android.content.Intent
import com.amazon.device.messaging.ADMMessageHandlerBase
import com.onesignal.OneSignal
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.bundle.INotificationBundleProcessor
import com.onesignal.notifications.internal.registration.impl.IPushRegistratorCallback

// WARNING: Do not pass 'this' to any methods as it will cause proguard build errors
//             when "proguard-android-optimize.txt" is used.
class ADMMessageHandler : ADMMessageHandlerBase("ADMMessageHandler") {
    override fun onMessage(intent: Intent) {
        val context = applicationContext
        OneSignal.initWithContext(context) { ok: Boolean ->
            if (!ok) {
                return@initWithContext
            }

            val bundle = intent.extras

            val bundleProcessor = OneSignal.getService<INotificationBundleProcessor>()

            bundleProcessor.processBundleFromReceiver(context, bundle!!)
        }
    }

    override fun onRegistered(newRegistrationId: String) {
        Logging.info("ADM registration ID: $newRegistrationId")

        var registerer = OneSignal.getService<IPushRegistratorCallback>()
        suspendifyOnThread {
            registerer.fireCallback(newRegistrationId)
        }
    }

    override fun onRegistrationError(error: String) {
        Logging.error("ADM:onRegistrationError: $error")

        if ("INVALID_SENDER" == error) {
            Logging.error(
                "Please double check that you have a matching package name (NOTE: Case Sensitive), api_key.txt, and the apk was signed with the same Keystore and Alias.",
            )
        }

        var registerer = OneSignal.getService<IPushRegistratorCallback>()
        suspendifyOnThread {
            registerer.fireCallback(null)
        }
    }

    override fun onUnregistered(info: String) {
        Logging.info("ADM:onUnregistered: $info")
    }
}
