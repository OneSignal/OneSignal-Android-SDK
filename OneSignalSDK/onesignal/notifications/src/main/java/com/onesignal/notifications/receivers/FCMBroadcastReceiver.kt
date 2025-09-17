package com.onesignal.notifications.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onesignal.OneSignal
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.notifications.internal.bundle.INotificationBundleProcessor

// This is the entry point when a FCM payload is received from the Google Play services app
// OneSignal does not use FirebaseMessagingService.onMessageReceived as it does not allow multiple
//   to be setup in an app. See the following issue for context on why this this important:
//    - https://github.com/OneSignal/OneSignal-Android-SDK/issues/1355
class FCMBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        // Do not process token update messages here.
        // They are also non-ordered broadcasts.
        val bundle = intent.extras
        if (bundle == null || "google.com/iid" == bundle.getString("from")) {
            return
        }

        // OneSignal will process the bundle in background
        suspendifyOnThread {
            if (!OneSignal.initWithContext(context.applicationContext)) {
                return@suspendifyOnThread
            }

            val bundleProcessor = OneSignal.getService<INotificationBundleProcessor>()

            if (!isFCMMessage(intent)) {
                setSuccessfulResultCode()
                return@suspendifyOnThread
            }

            val processedResult = bundleProcessor.processBundleFromReceiver(context, bundle)

            // Prevent other FCM receivers from firing if work manager is processing the notification
            if (processedResult!!.isWorkManagerProcessing) {
                setAbort()
                return@suspendifyOnThread
            }

            setSuccessfulResultCode()
        }
    }

    private fun setSuccessfulResultCode() {
        if (isOrderedBroadcast) {
            resultCode = Activity.RESULT_OK
        }
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
            //    pkg=com.onesignal.sdktest (has extras)
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
}
