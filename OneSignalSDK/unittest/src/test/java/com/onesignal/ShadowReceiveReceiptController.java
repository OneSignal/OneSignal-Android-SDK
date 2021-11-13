package com.onesignal;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(OSReceiveReceiptController.class)
public class ShadowReceiveReceiptController {

    boolean isReceiveReceiptEnabled() {
        return OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_RECEIVE_RECEIPTS_ENABLED,
                false
        );
    }

    // Removes the constraint `setRequiredNetworkType(NetworkType.CONNECTED)` which was causing unit tests to fail
    @Implementation
    public void beginEnqueueingWork(Context context, String osNotificationId) {
        if (!isReceiveReceiptEnabled()) {
            OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "sendReceiveReceipt disabled");
            return;
        }

        Data inputData = new Data.Builder()
                .putString("os_notification_id", osNotificationId)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(OSReceiveReceiptController.ReceiveReceiptWorker.class)
                .setInputData(inputData)
                .build();

        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OSReceiveReceiptController enqueueing send receive receipt work with notificationId: " + osNotificationId + " and delay: 0 seconds");

        WorkManager.getInstance(context)
                .enqueueUniqueWork(osNotificationId + "_receive_receipt", ExistingWorkPolicy.KEEP, workRequest);
    }
}
