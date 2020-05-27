package com.onesignal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.onesignal.OSNotificationGenerationJob.AppNotificationGenerationJob;
import com.onesignal.OSNotificationGenerationJob.ExtNotificationGenerationJob;
import com.onesignal.OSNotificationGenerationJob.NotificationGenerationJob;

import java.util.HashMap;
import java.util.UUID;

class OSNotificationWillShowInForegroundManager {

    private static final String EXT_NOTIF_JOB_WORKER_KEY = "EXT_NOTIF_JOB_WORKER_KEY";
    private static final String APP_NOTIF_JOB_WORKER_KEY = "APP_NOTIF_JOB_WORKER_KEY";

    /**
     * Singleton manager class containing workable {@link NotificationGenerationJob}'s
     */
    static class NotificationWillShowInForegroundManager {

        private static NotificationWillShowInForegroundManager mManager;

        public static NotificationWillShowInForegroundManager getInstance() {
            if (mManager == null)
                mManager = new NotificationWillShowInForegroundManager();

            return mManager;
        }

        private HashMap<String, NotificationGenerationJob> workableJobs;

        NotificationWillShowInForegroundManager() {
            this.workableJobs = new HashMap<>();
        }

        /**
         *
         */
        void addJob(String key, NotificationGenerationJob job) {
            workableJobs.put(key, job);
        }

        /**
         *
         */
        NotificationGenerationJob getJob(String key) {
            return workableJobs.remove(key);
        }

        /**
         *
         */
        boolean hasJob(String key) {
            return workableJobs.containsKey(key);
        }

        /**
         *
         */
        static void beginEnqueueingWork(NotificationGenerationJob notifJob) {
            boolean isExtNotifJob = notifJob instanceof ExtNotificationGenerationJob;
            String jobKey = isExtNotifJob ? EXT_NOTIF_JOB_WORKER_KEY : APP_NOTIF_JOB_WORKER_KEY;
            Class<? extends ListenableWorker> jobClazz = isExtNotifJob ?
                    ExtNotificationWillShowInForegroundWorker.class :
                    AppNotificationWillShowInForegroundWorker.class;

            String notifJobKey = UUID.randomUUID().toString();
            NotificationWillShowInForegroundManager.getInstance().addJob(notifJobKey, notifJob);

            Data appHandlerData = new Data.Builder()
                    .putString(jobKey, notifJobKey)
                    .build();

            OneTimeWorkRequest appHandlerWorkRequest = new OneTimeWorkRequest.Builder(jobClazz)
                    .setInputData(appHandlerData)
                    .addTag(notifJobKey)
                    .build();

            WorkManager.getInstance(OneSignal.appContext)
                    .enqueue(appHandlerWorkRequest);
        }
    }

    public static class ExtNotificationWillShowInForegroundWorker extends Worker {

        public ExtNotificationWillShowInForegroundWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            if (OneSignal.appContext == null || !getInputData().hasKeyWithValueOfType(EXT_NOTIF_JOB_WORKER_KEY, String.class))
                return Result.failure();

            String extNotifJobKey = getInputData().getString(EXT_NOTIF_JOB_WORKER_KEY);
            NotificationWillShowInForegroundManager mManager = NotificationWillShowInForegroundManager.getInstance();
            if (extNotifJobKey == null || !mManager.hasJob(extNotifJobKey))
                return Result.failure();

            ExtNotificationGenerationJob currentNotifJob = (ExtNotificationGenerationJob) mManager.getJob(extNotifJobKey);
            if (currentNotifJob == null)
                return Result.failure();

            if (OneSignal.extNotificationWillShowInForegroundHandler == null)
                return Result.failure();

            OneSignal.extNotificationWillShowInForegroundHandler.notificationWillShowInForeground(currentNotifJob);

            return Result.success();
        }
    }

    public static class AppNotificationWillShowInForegroundWorker extends Worker {

        public AppNotificationWillShowInForegroundWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            if (OneSignal.appContext == null || !getInputData().hasKeyWithValueOfType(APP_NOTIF_JOB_WORKER_KEY, String.class))
                return Result.failure();

            String appNotifJobKey = getInputData().getString(APP_NOTIF_JOB_WORKER_KEY);
            NotificationWillShowInForegroundManager mManager = NotificationWillShowInForegroundManager.getInstance();
            if (appNotifJobKey == null || !mManager.hasJob(appNotifJobKey))
                return Result.failure();

            AppNotificationGenerationJob currentNotifJob = (AppNotificationGenerationJob) mManager.getJob(appNotifJobKey);
            if (currentNotifJob == null)
                return Result.failure();

            OneSignal.appNotificationWillShowInForegroundHandler.notificationWillShowInForeground(currentNotifJob);

            return Result.success();
        }
    }
}
