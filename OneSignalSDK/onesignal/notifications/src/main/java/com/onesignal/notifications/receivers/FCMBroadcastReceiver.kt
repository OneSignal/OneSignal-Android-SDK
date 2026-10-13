package com.onesignal.notifications.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onesignal.notifications.internal.ingress.NotificationIngress

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

        runOrderedIngressHandoff(
            "FCMBroadcastReceiver",
            BroadcastCompletion.RECONSTRUCTIBLE_WORK_TIMEOUT_MS,
        ) { ordered, pendingResult ->
            if (!isFCMMessage(intent)) {
                setSuccessfulResultCode(pendingResult, ordered)
                return@runOrderedIngressHandoff
            }

            if (NotificationIngress.persistFcm(context, intent, bundle)) {
                setAbort(pendingResult, ordered)
            } else {
                setSuccessfulResultCode(pendingResult, ordered)
            }
        }
    }

    private fun setSuccessfulResultCode(
        pendingResult: BroadcastReceiver.PendingResult?,
        ordered: Boolean,
    ) {
        if (ordered && pendingResult != null) {
            pendingResult.resultCode = Activity.RESULT_OK
        }
    }

    private fun setAbort(
        pendingResult: BroadcastReceiver.PendingResult?,
        ordered: Boolean,
    ) {
        if (ordered && pendingResult != null) {
            // Stops the other FCM receivers. RESULT_OK avoids the GCM result=CANCELLED log.
            pendingResult.abortBroadcast()
            pendingResult.resultCode = Activity.RESULT_OK
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
