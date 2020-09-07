package com.onesignal;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONException;
import org.json.JSONObject;

class OSNotificationWorkManager {

    private static final String ANDROID_NOTIF_ID_WORKER_DATA_PARAM = "android_notif_id";
    private static final String JSON_PAYLOAD_WORKER_DATA_PARAM = "json_payload";
    private static final String TIMESTAMP_WORKER_DATA_PARAM = "timestamp";
    private static final String IS_RESTORING_WORKER_DATA_PARAM = "is_restoring";

    static void beginEnqueueingWork(Context context, String osNotificationId, int androidNotificationId, String jsonPayload, boolean isRestoring, long timestamp, boolean isHighPriority) {
        // TODO: Need to figure out how to implement the isHighPriority param
        Data inputData = new Data.Builder()
                .putInt(ANDROID_NOTIF_ID_WORKER_DATA_PARAM, androidNotificationId)
                .putString(JSON_PAYLOAD_WORKER_DATA_PARAM, jsonPayload)
                .putLong(TIMESTAMP_WORKER_DATA_PARAM, timestamp)
                .putBoolean(IS_RESTORING_WORKER_DATA_PARAM, isRestoring)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(NotificationWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(osNotificationId, ExistingWorkPolicy.KEEP, workRequest);
    }

    public static class NotificationWorker extends Worker {

        public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            Data inputData = getInputData();
            try {
                int androidNotificationId = inputData.getInt(ANDROID_NOTIF_ID_WORKER_DATA_PARAM, 0);
                JSONObject jsonPayload = new JSONObject(inputData.getString(JSON_PAYLOAD_WORKER_DATA_PARAM));
                long timestamp = inputData.getLong(TIMESTAMP_WORKER_DATA_PARAM, System.currentTimeMillis() / 1000L);
                boolean isRestoring = inputData.getBoolean(IS_RESTORING_WORKER_DATA_PARAM, false);

                processNotificationData(
                        getApplicationContext(),
                        androidNotificationId,
                        jsonPayload,
                        isRestoring,
                        timestamp);

            } catch (JSONException e) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "Error occurred doing work for job with id: " + getId().toString());
                e.printStackTrace();
                return Result.failure();
            }
            return Result.success();
        }

        private void processNotificationData(Context context, int androidNotificationId, JSONObject jsonPayload, boolean isRestoring, Long timestamp) {
            OSNotificationReceived notificationReceived = new OSNotificationReceived(
                    context,
                    androidNotificationId,
                    jsonPayload,
                    isRestoring,
                    OneSignal.isAppActive(),
                    timestamp
            );

            if (OneSignal.notificationProcessingHandler != null)
                try {
                    OneSignal.notificationProcessingHandler.notificationProcessing(context, notificationReceived);
                } catch (Throwable t) {
                    if (!notificationReceived.displayed()) {
                        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "onNotificationProcessing throw an exception. Displaying normal OneSignal notification.", t);
                        notificationReceived.internalComplete();
                    }
                    else
                        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "onNotificationProcessing throw an exception. Extended notification displayed but custom processing did not finish.", t);

                    throw t;
                }
            else if (!notificationReceived.displayed()) {
                OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "notificationProcessingHandler not setup, displaying normal OneSignal notification");
                notificationReceived.internalComplete();
            }
        }
    }
}
