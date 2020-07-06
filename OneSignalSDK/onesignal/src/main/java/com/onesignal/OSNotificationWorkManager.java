package com.onesignal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

class OSNotificationWorkManager {

    private static final String NOTIFICATION_PAYLOAD_KEY = "NOTIF_JOB_PAYLOAD_KEY";

    static void beginEnqueueingWork(OSNotificationReceived notificationReceived) {
        Data inputData = new Data.Builder()
                .putString(NOTIFICATION_PAYLOAD_KEY, notificationReceived.toJSONObject().toString())
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(NotificationWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(OneSignal.appContext).enqueue(workRequest);
    }

    public static class NotificationWorker extends Worker {

        public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            Context appContext = getApplicationContext();

            NotificationExtender.processIntent(appContext, null);
            FCMBroadcastReceiver.completeWakefulIntent(intent);

            return Result.success();
        }
    }

}
