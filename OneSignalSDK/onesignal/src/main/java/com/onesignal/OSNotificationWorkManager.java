package com.onesignal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
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

    static void beginEnqueueingWork(Context context, int androidNotificationId, String jsonPayload, boolean isRestoring, long timestamp, boolean isHighPriority) {
        // TODO: Do we need isHighPriority only more?

        Data inputData = new Data.Builder()
                .putInt(ANDROID_NOTIF_ID_WORKER_DATA_PARAM, androidNotificationId)
                .putString(JSON_PAYLOAD_WORKER_DATA_PARAM, jsonPayload)
                .putLong(TIMESTAMP_WORKER_DATA_PARAM, timestamp)
                .putBoolean(IS_RESTORING_WORKER_DATA_PARAM, isRestoring)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(NotificationWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(context).enqueue(workRequest);
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
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Ending the current notification worker");
                e.printStackTrace();
                return Result.failure();
            }

            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Ending the current notification worker");
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
                OneSignal.notificationProcessingHandler.notificationProcessing(context, notificationReceived);
            else if (!notificationReceived.notificationExtender.developerProcessed && notificationReceived.notificationExtender.notificationDisplayedResult == null) {
                // noinspection ConstantConditions - displayNotification might have been called by the developer
                OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "notificationProcessingHandler not setup, displaying normal OneSignal notification");
                notificationReceived.complete();
            }
        }
    }
}
