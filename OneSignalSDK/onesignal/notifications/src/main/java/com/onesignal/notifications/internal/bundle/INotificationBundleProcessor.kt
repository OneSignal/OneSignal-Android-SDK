package com.onesignal.notifications.internal.bundle

import android.content.Context
import android.os.Bundle

internal interface INotificationBundleProcessor {

    /**
     * Process bundle passed from FCM / HMS / ADM broadcast receiver
     */
    fun processBundleFromReceiver(context: Context, bundle: Bundle): ProcessedBundleResult?

    class ProcessedBundleResult {
        private var isOneSignalPayload = false
        private var isDeniedByLifecycleCallback = false
        var isWorkManagerProcessing = false

        val isProcessed: Boolean
            get() = !isOneSignalPayload || isDeniedByLifecycleCallback || isWorkManagerProcessing

        fun setOneSignalPayload(oneSignalPayload: Boolean) {
            isOneSignalPayload = oneSignalPayload
        }

        fun setDeniedByLifecycleCallback(deniedByLifecycleCallback: Boolean) {
            this.isDeniedByLifecycleCallback = deniedByLifecycleCallback
        }
    }
}
