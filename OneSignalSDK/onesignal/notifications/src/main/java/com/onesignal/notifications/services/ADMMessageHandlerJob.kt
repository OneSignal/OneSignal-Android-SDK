package com.onesignal.notifications.services

import android.content.Context
import android.content.Intent
import com.amazon.device.messaging.ADMMessageHandlerJobBase
import com.onesignal.OneSignal
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.bundle.INotificationBundleProcessor
import com.onesignal.notifications.internal.registration.impl.IPushRegistratorCallback

class ADMMessageHandlerJob : ADMMessageHandlerJobBase() {
    override fun onMessage(
        context: Context?,
        intent: Intent?,
    ) {
        if (context == null) {
            return
        }

        suspendifyOnThread {
            if (!OneSignal.initWithContext(context.applicationContext)) {
                return@suspendifyOnThread
            }
            val bundleProcessor = OneSignal.getService<INotificationBundleProcessor>()

            val bundle = intent?.extras

            bundleProcessor.processBundleFromReceiver(context, bundle!!)
        }
    }

    override fun onRegistered(
        context: Context?,
        newRegistrationId: String?,
    ) {
        Logging.info("ADM registration ID: $newRegistrationId")

        var registerer = OneSignal.getService<IPushRegistratorCallback>()
        suspendifyOnThread {
            registerer.fireCallback(newRegistrationId)
        }
    }

    override fun onUnregistered(
        context: Context?,
        registrationId: String?,
    ) {
        Logging.info("ADM:onUnregistered: $registrationId")
    }

    override fun onRegistrationError(
        context: Context?,
        error: String?,
    ) {
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
}
