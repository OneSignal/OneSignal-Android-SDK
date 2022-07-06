package com.onesignal.onesignal.device.receivers

import androidx.legacy.content.WakefulBroadcastReceiver
import android.content.Intent
import android.os.Bundle
import android.app.Activity
import android.annotation.TargetApi
import android.app.Notification
import android.os.Parcelable
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.onesignal.onesignal.OneSignal
import com.onesignal.onesignal.device.services.FCMIntentJobService
import com.onesignal.onesignal.device.services.FCMIntentService
import com.onesignal.onesignal.internal.common.BundleCompat
import com.onesignal.onesignal.internal.common.BundleCompatBundle
import com.onesignal.onesignal.internal.common.BundleCompatFactory
import com.onesignal.onesignal.internal.common.time.ITime
import com.onesignal.onesignal.internal.notification.work.NotificationBundleProcessor
import com.onesignal.onesignal.logging.Logging
import java.lang.IllegalStateException

// This is the entry point when a FCM payload is received from the Google Play services app
// OneSignal does not use FirebaseMessagingService.onMessageReceived as it does not allow multiple
//   to be setup in an app. See the following issue for context on why this this important:
//    - https://github.com/OneSignal/OneSignal-Android-SDK/issues/1355
class FCMBroadcastReceiver : WakefulBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Do not process token update messages here.
        // They are also non-ordered broadcasts.
        val bundle = intent.extras
        if (bundle == null || "google.com/iid" == bundle.getString("from")) return
        OneSignal.initWithContext(context)

        val bundleProcessor = OneSignal.getService<NotificationBundleProcessor>()

        val bundleReceiverCallback: NotificationBundleProcessor.ProcessBundleReceiverCallback =
            object : NotificationBundleProcessor.ProcessBundleReceiverCallback {
                override fun onBundleProcessed(processedResult: NotificationBundleProcessor.ProcessedBundleResult?) {
                    // Null means this isn't a FCM message
                    if (processedResult == null) {
                        setSuccessfulResultCode()
                        return
                    }

                    // Prevent other FCM receivers from firing if:
                    //   1. This is a duplicated FCM message
                    //   2. OR work manager is processing the notification
                    if (processedResult.isDup || processedResult.isWorkManagerProcessing) {
                        // Abort to prevent other FCM receivers from process this Intent.
                        setAbort()
                        return
                    }
                    setSuccessfulResultCode()
                }
            }
        processOrderBroadcast(context, intent, bundle, bundleProcessor, bundleReceiverCallback)
    }

    private fun setSuccessfulResultCode() {
        if (isOrderedBroadcast) resultCode = Activity.RESULT_OK
    }

    private fun setAbort() {
        if (isOrderedBroadcast) {
            // Prevents other BroadcastReceivers from firing
            abortBroadcast()

            // TODO: Previous error and related to this Github issue ticket
            //    https://github.com/OneSignal/OneSignal-Android-SDK/issues/307
            // RESULT_OK prevents the following confusing logcat entry;
            // W/GCM: broadcast intent callback: result=CANCELLED forIntent {
            //    act=com.google.android.c2dm.intent.RECEIVE
            //    flg=0x10000000
            //    pkg=com.onesignal.example (has extras)
            // }
            resultCode = Activity.RESULT_OK
        }
    }

    companion object {
        private const val FCM_RECEIVE_ACTION = "com.google.android.c2dm.intent.RECEIVE"
        private const val FCM_TYPE = "gcm"
        private const val MESSAGE_TYPE_EXTRA_KEY = "message_type"
        private fun isFCMMessage(intent: Intent): Boolean {
            if (FCM_RECEIVE_ACTION == intent.action) {
                val messageType = intent.getStringExtra(MESSAGE_TYPE_EXTRA_KEY)
                return messageType == null || FCM_TYPE == messageType
            }
            return false
        }
    }

    private fun processOrderBroadcast(
        context: Context,
        intent: Intent,
        bundle: Bundle,
        bundleProcessor: NotificationBundleProcessor,
        fcmBundleReceiver: NotificationBundleProcessor.ProcessBundleReceiverCallback
    ) {
        if (!isFCMMessage(intent)) fcmBundleReceiver.onBundleProcessed(null)
        val bundleReceiverCallback: NotificationBundleProcessor.ProcessBundleReceiverCallback =
            object : NotificationBundleProcessor.ProcessBundleReceiverCallback {
                override fun onBundleProcessed(processedResult: NotificationBundleProcessor.ProcessedBundleResult?) {
                    // Return if the notification will NOT be handled by normal FCMIntentService display flow.
                    if (processedResult != null && processedResult.processed()) {
                        fcmBundleReceiver.onBundleProcessed(processedResult)
                        return
                    }

                    // TODO: It seems all (???) will have been added to the work manager, when/why do we need to fallback to the FCM Service?
                    startFCMService(context, bundle, bundleProcessor)
                    fcmBundleReceiver.onBundleProcessed(processedResult)
                }
            }

        bundleProcessor.processBundleFromReceiver(
            context,
            bundle,
            bundleReceiverCallback
        )
    }

    private fun startFCMService(context: Context, bundle: Bundle, bundleProcessor: NotificationBundleProcessor) {
        Logging.debug("startFCMService from: $context and bundle: $bundle")

        // If no remote resources have to be downloaded don't create a job which could add some delay.
        if (!bundleProcessor.hasRemoteResource(bundle)) {
            Logging.debug("startFCMService with no remote resources, no need for services")

            val taskExtras = setCompatBundleForServer(bundle, BundleCompatFactory.instance)
            bundleProcessor.processFromFCMIntentService(context, taskExtras)
            return
        }

        val isHighPriority = bundle.getString("pri", "0").toInt() > 9
        if (!isHighPriority && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startFCMServiceWithJobIntentService(context, bundle)
        else {
            try {
                startFCMServiceWithWakefulService(context, bundle)
            } catch (e: IllegalStateException) {
                // If the high priority FCM message failed to add this app to the temporary whitelist
                // https://github.com/OneSignal/OneSignal-Android-SDK/issues/498
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    startFCMServiceWithJobIntentService(context, bundle)
                else
                    throw e
            }
        }
    }

    /**
     * This function uses a com.OneSignal.JobIntentService in order to enqueue the jobs.
     * Some devices with Api O and upper can't schedule more than 100 distinct jobs,
     * this will process one notification sequentially like an IntentService.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startFCMServiceWithJobIntentService(context: Context, bundle: Bundle) {
        val taskExtras = setCompatBundleForServer(bundle, BundleCompatFactory.instance)
        val intent = Intent(context, FCMIntentJobService::class.java)
        intent.putExtra(FCMIntentJobService.BUNDLE_EXTRA, taskExtras.getBundle() as Parcelable)
        FCMIntentJobService.enqueueWork(context, intent)
    }

    private fun startFCMServiceWithWakefulService(context: Context, bundle: Bundle) {
        val componentName =
            ComponentName(context.packageName, FCMIntentService::class.java.name)
        val taskExtras = setCompatBundleForServer(bundle, BundleCompatBundle())
        val intentForService = Intent()
            .replaceExtras(taskExtras.getBundle() as Bundle)
            .setComponent(componentName)
        startWakefulService(context, intentForService)
    }

    private fun setCompatBundleForServer(
        bundle: Bundle,
        taskExtras: BundleCompat<*>
    ): BundleCompat<*> {
        taskExtras.putString(
            "json_payload",
            NotificationBundleProcessor.bundleAsJSONObject(bundle).toString()
        )
        taskExtras.putLong("timestamp", OneSignal.getService<ITime>().currentTimeMillis / 1000L)
        return taskExtras
    }
}