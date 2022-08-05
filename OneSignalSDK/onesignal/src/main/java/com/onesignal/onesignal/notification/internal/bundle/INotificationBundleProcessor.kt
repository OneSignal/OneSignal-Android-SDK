package com.onesignal.onesignal.notification.internal.bundle

import android.content.Context
import android.os.Bundle

internal interface INotificationBundleProcessor {

    /**
     * Process bundle passed from FCM / HMS / ADM broadcast receiver
     */
    fun processBundleFromReceiver(context: Context, bundle: Bundle) : ProcessedBundleResult?

    class ProcessedBundleResult {
        private var isOneSignalPayload = false
        private var inAppPreviewShown = false
        var isWorkManagerProcessing = false

        val isProcessed: Boolean
            get() = !isOneSignalPayload || inAppPreviewShown || isWorkManagerProcessing

        fun setOneSignalPayload(oneSignalPayload: Boolean) {
            isOneSignalPayload = oneSignalPayload
        }

        fun setInAppPreviewShown(inAppPreviewShown: Boolean) {
            this.inAppPreviewShown = inAppPreviewShown
        }
    }
}