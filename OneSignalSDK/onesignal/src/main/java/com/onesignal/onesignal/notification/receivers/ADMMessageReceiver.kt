package com.onesignal.onesignal.notification.receivers

import com.onesignal.onesignal.notification.services.ADMMessageHandler
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.notification.services.ADMMessageHandlerJob

// WARNING: Do not pass 'this' to any methods as it will cause proguard build errors
//             when "proguard-android-optimize.txt" is used.
class ADMMessageReceiver : com.amazon.device.messaging.ADMMessageReceiver(ADMMessageHandler::class.java) {
    init {
        var ADMLatestAvailable = false
        try {
            Class.forName("com.amazon.device.messaging.ADMMessageHandlerJobBase")
            ADMLatestAvailable = true
        } catch (e: ClassNotFoundException) {
            // Handle the exception.
        }
        if (ADMLatestAvailable) {
            registerJobServiceClass(ADMMessageHandlerJob::class.java, JOB_ID)
        }
        Logging.debug("ADM latest available: $ADMLatestAvailable")
    }

    companion object {
        private const val JOB_ID = 123891
    }
}
