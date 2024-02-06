package com.onesignal.notifications.receivers

import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.services.ADMMessageHandler
import com.onesignal.notifications.services.ADMMessageHandlerJob

// WARNING: Do not pass 'this' to any methods as it will cause proguard build errors
//             when "proguard-android-optimize.txt" is used.
class ADMMessageReceiver : com.amazon.device.messaging.ADMMessageReceiver(ADMMessageHandler::class.java) {
    init {
        var admLatestAvailable = false
        try {
            Class.forName("com.amazon.device.messaging.ADMMessageHandlerJobBase")
            admLatestAvailable = true
        } catch (e: ClassNotFoundException) {
            // Handle the exception.
        }
        if (admLatestAvailable) {
            registerJobServiceClass(ADMMessageHandlerJob::class.java, JOB_ID)
        }
        Logging.debug("ADM latest available: $admLatestAvailable")
    }

    companion object {
        private const val JOB_ID = 123891
    }
}
