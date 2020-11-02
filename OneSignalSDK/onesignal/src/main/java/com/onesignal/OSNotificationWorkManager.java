package com.onesignal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

import static com.onesignal.OSUtils.isStringNotEmpty;

class OSNotificationWorkManager {

    private static final String ANDROID_NOTIF_ID_WORKER_DATA_PARAM = "android_notif_id";
    private static final String JSON_PAYLOAD_WORKER_DATA_PARAM = "json_payload";
    private static final String TIMESTAMP_WORKER_DATA_PARAM = "timestamp";
    private static final String IS_RESTORING_WORKER_DATA_PARAM = "is_restoring";

    private static Set<String> notificationIds = OSUtils.newConcurrentSet();

    static boolean addNotificationIdProcessed(String osNotificationId) {
        // Duplicate control
        // Keep in memory on going processed notifications, to avoid fast duplicates that already finished work process but are not completed yet
        // enqueueUniqueWork might not be enough, if the work already finished then the duplicate notification work might be queued again
        if (isStringNotEmpty(osNotificationId)) {
            if (notificationIds.contains(osNotificationId)) {
                OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OSNotificationWorkManager notification with notificationId: " + osNotificationId + " already queued");
                return false;
            } else {
                notificationIds.add(osNotificationId);
            }
        }

        return true;
    }

    static void removeNotificationIdProcessed(String osNotificationId) {
        if (isStringNotEmpty(osNotificationId)) {
            notificationIds.remove(osNotificationId);
        }
    }

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

        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "OSNotificationWorkManager enqueueing notification work with notificationId: " + osNotificationId + " and jsonPayload: " + jsonPayload);
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
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "NotificationWorker running doWork with data: " + inputData);

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
            OSNotification notification = new OSNotification(null, jsonPayload, androidNotificationId);
            OSNotificationController controller = new OSNotificationController(context, jsonPayload, isRestoring, true, timestamp);
            OSNotificationReceivedEvent notificationReceived = new OSNotificationReceivedEvent(controller, notification);

            if (OneSignal.remoteNotificationReceivedHandler != null)
                try {
                    OneSignal.remoteNotificationReceivedHandler.remoteNotificationReceived(context, notificationReceived);
                } catch (Throwable t) {
                    OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "remoteNotificationReceived throw an exception. Displaying normal OneSignal notification.", t);
                    notificationReceived.complete(notification);

                    throw t;
                }
            else {
                OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "remoteNotificationReceivedHandler not setup, displaying normal OneSignal notification");
                notificationReceived.complete(notification);
            }
        }
    }
}
