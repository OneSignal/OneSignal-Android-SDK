package com.onesignal.onesignal.device.services

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.onesignal.onesignal.OneSignal
import com.onesignal.onesignal.internal.notification.work.NotificationBundleProcessor

/**
 * Uses modified JobIntentService class that's part of the onesignal package
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class FCMIntentJobService : JobIntentService() {
    protected override fun onHandleWork(intent: Intent) {
        val bundle = intent.extras ?: return
        OneSignal.initWithContext(this)
        val bundleProcessor = OneSignal.getService<NotificationBundleProcessor>()
        bundleProcessor.processBundleFromReceiver(
            this,
            bundle,
            object : NotificationBundleProcessor.ProcessBundleReceiverCallback {
                override fun onBundleProcessed(processedResult: NotificationBundleProcessor.ProcessedBundleResult?) {}
            })
    }

    companion object {
        const val BUNDLE_EXTRA = "Bundle:Parcelable:Extras"
        private const val JOB_ID = 123890
        fun enqueueWork(context: Context?, intent: Intent?) {
            enqueueWork(context!!, FCMIntentJobService::class.java, JOB_ID, intent!!, false)
        }
    }
}