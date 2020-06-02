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
     * Controls the enqueueing of {@link androidx.work.WorkRequest}'s by checking which instance of
     *  the {@link NotificationGenerationJob} is being passed in, {@link ExtNotificationGenerationJob}
     *  or {@link AppNotificationGenerationJob}
     * <br/><br/>
     * @see NotificationGenerationJob
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
         * Add a job to the workableJobs HashMap
         */
        void addJob(String key, NotificationGenerationJob job) {
            workableJobs.put(key, job);
        }

        /**
         * Get a job from the workableJobs HashMap
         */
        NotificationGenerationJob getJob(String key) {
            return workableJobs.remove(key);
        }

        /**
         * Validate a job key exists with a job in the workableJobs HashMap
         */
        boolean hasJob(String key) {
            return workableJobs.containsKey(key) && workableJobs.get(key) != null;
        }

        /**
         * Enqueue work for a NotificationGenerationJob, which will figure out and decide the correct
         *  Worker to "doWork" with the notifJob and fire the correct handler
         * <br/><br/>
         * @see ExtNotificationWillShowInForegroundWorker
         * @see AppNotificationWillShowInForegroundWorker
         */
        static void beginEnqueueingWork(NotificationGenerationJob notifJob) {
            boolean isExtNotifJob = notifJob instanceof ExtNotificationGenerationJob;
            String jobKey = isExtNotifJob ? EXT_NOTIF_JOB_WORKER_KEY : APP_NOTIF_JOB_WORKER_KEY;
            Class<? extends ListenableWorker> jobClazz = isExtNotifJob ?
                    ExtNotificationWillShowInForegroundWorker.class :
                    AppNotificationWillShowInForegroundWorker.class;

            // Generate a random UUID to attach to the notifJob in the Worker job manager
            String notifJobKey = UUID.randomUUID().toString();
            NotificationWillShowInForegroundManager.getInstance().addJob(notifJobKey, notifJob);

            Data workData = new Data.Builder()
                    .putString(jobKey, notifJobKey)
                    .build();

            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(jobClazz)
                    .setInputData(workData)
                    .addTag(notifJobKey)
                    .build();

            WorkManager.getInstance(OneSignal.appContext)
                    .enqueue(workRequest);
        }
    }

    public static class ExtNotificationWillShowInForegroundWorker extends Worker {

        public ExtNotificationWillShowInForegroundWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            // appContext my have been used to begin "doWork"
            // Make sure the key we need to to obtain our notifJob exists in the data input of the Worker
            if (OneSignal.appContext == null || !getInputData().hasKeyWithValueOfType(EXT_NOTIF_JOB_WORKER_KEY, String.class))
                return Result.failure();

            // The job key must not be null
            // A job should exist for the key
            String extNotifJobKey = getInputData().getString(EXT_NOTIF_JOB_WORKER_KEY);
            NotificationWillShowInForegroundManager mManager = NotificationWillShowInForegroundManager.getInstance();
            if (extNotifJobKey == null || !mManager.hasJob(extNotifJobKey))
                return Result.failure();

            // Make sure current job obtained is not null
            ExtNotificationGenerationJob currentNotifJob = (ExtNotificationGenerationJob) mManager.getJob(extNotifJobKey);
            if (currentNotifJob == null)
                return Result.failure();

            // Make sure extNotificationWillShowInForegroundHandler is not null
            if (OneSignal.extNotificationWillShowInForegroundHandler == null)
                return Result.failure();

            // Fire the ext handler and report success
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
            // appContext my have been used to begin "doWork"
            // Make sure the key we need to to obtain our notifJob exists in the data input of the Worker
            if (OneSignal.appContext == null || !getInputData().hasKeyWithValueOfType(APP_NOTIF_JOB_WORKER_KEY, String.class))
                return Result.failure();

            String appNotifJobKey = getInputData().getString(APP_NOTIF_JOB_WORKER_KEY);
            NotificationWillShowInForegroundManager mManager = NotificationWillShowInForegroundManager.getInstance();
            // The job key must not be null
            // A job should exist for the key
            if (appNotifJobKey == null || !mManager.hasJob(appNotifJobKey))
                return Result.failure();

            // Make sure current job obtained is not null
            AppNotificationGenerationJob currentNotifJob = (AppNotificationGenerationJob) mManager.getJob(appNotifJobKey);
            if (currentNotifJob == null)
                return Result.failure();

            // Make sure appNotificationWillShowInForegroundHandler is not null
            if (OneSignal.appNotificationWillShowInForegroundHandler == null)
                return Result.failure();

            // Fire the app handler and report success
            OneSignal.appNotificationWillShowInForegroundHandler.notificationWillShowInForeground(currentNotifJob);

            return Result.success();
        }
    }
}
