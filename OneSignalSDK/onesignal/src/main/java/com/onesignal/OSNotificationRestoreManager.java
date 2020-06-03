package com.onesignal;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.legacy.content.WakefulBroadcastReceiver;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static com.onesignal.NotificationRestorer.ANDROID_NOTIF_ID_RESTORE_INTENT_KEY;
import static com.onesignal.NotificationRestorer.IS_RESTORING_RESTORE_INTENT_KEY;
import static com.onesignal.NotificationRestorer.JSON_PAYLOAD_RESTORE_INTENT_KEY;
import static com.onesignal.NotificationRestorer.TIMESTAMP_RESTORE_INTENT_KEY;

class OSNotificationRestoreManager {

    /**
     *
     * <br/><br/>
     * @see Intent
     */
    static class NotificationRestoreManager {

        private static NotificationRestoreManager mManager;

        public static NotificationRestoreManager getInstance() {
            if (mManager == null)
                mManager = new NotificationRestoreManager();

            return mManager;
        }

        NotificationRestoreManager() {

        }

        /**
         *
         * <br/><br/>
         * @see
         */
        static void beginEnqueueingWorkRequests(ArrayList<OneTimeWorkRequest> workRequests) {
            WorkManager.getInstance(OneSignal.appContext)
                    .enqueue(workRequests);
        }
    }

    public static class NotificationRestoreWorker extends Worker {

        public NotificationRestoreWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            Context context = getApplicationContext();
            boolean useExtender = (NotificationExtenderService.getIntent(context) != null);

            Intent intent = new Intent();
            if (useExtender)
                intent = NotificationExtenderService.getIntent(context);

            addRestoreExtras(intent, getInputData());
            if (useExtender) {
                NotificationExtenderService.enqueueWork(
                        context,
                        intent.getComponent(),
                        NotificationExtenderService.EXTENDER_SERVICE_JOB_ID,
                        intent,
                        false);
            }
            else {
                // Null check for https://github.com/OneSignal/OneSignal-Android-SDK/issues/591
                final Bundle extras = intent.getExtras();
                if (extras == null)
                    return Result.failure();

                NotificationBundleProcessor.ProcessFromFCMIntentService(
                        getApplicationContext(),
                        new BundleCompatBundle(extras),
                        null
                );
            }

            return Result.success();
        }

        private static void addRestoreExtras(Intent intent, Data inputData) {
            String fullData = inputData.getString(JSON_PAYLOAD_RESTORE_INTENT_KEY);
            int existingId = inputData.getInt(ANDROID_NOTIF_ID_RESTORE_INTENT_KEY, -1);
            boolean isRestoring = inputData.getBoolean(IS_RESTORING_RESTORE_INTENT_KEY, true);
            long dateTime = inputData.getLong(TIMESTAMP_RESTORE_INTENT_KEY, 0);

            intent.putExtra(JSON_PAYLOAD_RESTORE_INTENT_KEY, fullData)
                  .putExtra(ANDROID_NOTIF_ID_RESTORE_INTENT_KEY, existingId)
                  .putExtra(IS_RESTORING_RESTORE_INTENT_KEY, isRestoring)
                  .putExtra(TIMESTAMP_RESTORE_INTENT_KEY, dateTime);
        }
    }
}
