package com.onesignal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.HashMap;
import java.util.UUID;

class OSNotificationProcessingManager {

    private static final String NOTIFICATION_RECEIVED_WORKER_KEY = "NOTIFICATION_RECEIVED_WORKER_KEY";

    /**
     * Singleton manager class containing workable {@link OSNotificationReceived}'s
     * <br/><br/>
     * @see OSNotificationReceived
     */
    static class NotificationProcessingManager {

        private static NotificationProcessingManager mManager;

        public static NotificationProcessingManager getInstance() {
            if (mManager == null)
                mManager = new NotificationProcessingManager();

            return mManager;
        }

        private HashMap<String, OSNotificationReceived> workableJobs;

        NotificationProcessingManager() {
            this.workableJobs = new HashMap<>();
        }

        /**
         * Add a job to the workableJobs HashMap
         */
        void addJob(String key, OSNotificationReceived job) {
            workableJobs.put(key, job);
        }

        /**
         * Get a job from the workableJobs HashMap
         */
        OSNotificationReceived getJob(String key) {
            return workableJobs.remove(key);
        }

        /**
         * Validate a job key exists with a job in the workableJobs HashMap
         */
        boolean hasJob(String key) {
            return workableJobs.containsKey(key) && workableJobs.get(key) != null;
        }

        /**
         * Enqueue work for a OSNotificationReceived so that it can be passed into the NotificationProcessingHandler
         *  if it exists
         * <br/><br/>
         * @see NotificationProcessingWorker
         */
        static void beginEnqueueingWork(Context context, OSNotificationReceived notificationReceived) {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "Beginning to enqueue NotificationProcessingWorker work for OS notification id: " + notificationReceived.payload.notificationID);

            // Generate a random UUID to attach to the notificationReceived in the Worker job manager
            String notificationReceivedKey = UUID.randomUUID().toString();
            NotificationProcessingManager.getInstance().addJob(notificationReceivedKey, notificationReceived);

            Data workData = new Data.Builder()
                    .putString(NOTIFICATION_RECEIVED_WORKER_KEY, notificationReceivedKey)
                    .build();

            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(NotificationProcessingWorker.class)
                    .setInputData(workData)
                    .addTag(notificationReceivedKey)
                    .build();

            WorkManager.getInstance(context)
                    .enqueue(workRequest);
        }
    }

    public static class NotificationProcessingWorker extends Worker {

        public NotificationProcessingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "Beginning to enqueue work for NotificationProcessingHandler");
            Context context = getApplicationContext();

            // Make sure the key we need to to obtain our notificationReceived exists in the data input of the Worker
            if (!getInputData().hasKeyWithValueOfType(NOTIFICATION_RECEIVED_WORKER_KEY, String.class)) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "getInputData() does not have the key: " + NOTIFICATION_RECEIVED_WORKER_KEY + " with class type: String, returning Result.failure for Worker");
                return Result.failure();
            }

            // The job key must not be null and a job should exist for the key
            String notificationReceivedKey = getInputData().getString(NOTIFICATION_RECEIVED_WORKER_KEY);
            NotificationProcessingManager manager = NotificationProcessingManager.getInstance();
            if (notificationReceivedKey == null || !manager.hasJob(notificationReceivedKey)) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "notificationReceivedKey is null or manager does not have a job with key: " + notificationReceivedKey + ", returning Result.failure for Worker");
                return Result.failure();
            }

            final OSNotificationReceived notificationReceived = manager.getJob(notificationReceivedKey);
            if (notificationReceived == null) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.ERROR, "notificationReceived is null for key: " + notificationReceivedKey + ", returning Result.failure for Worker");
                return Result.failure();
            }
            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "Beginning to enqueue work for OS notification id: " + notificationReceived.payload.notificationID);

            NotificationExtender notifExtender = notificationReceived.getNotificationExtender();
            // Has a notificationProcessingHandler been setup
            boolean useNotificationProcessor = OneSignal.notificationProcessingHandler != null;
            try {
                if (useNotificationProcessor) {
                    notificationReceived.startTimeout(new Runnable() {
                        @Override
                        public void run() {
                            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "notificationProcessingHandler complete never called, using default post delayed timeout handler's timeout runnable");
                            notificationReceived.complete();
                        }
                    });
                    OneSignal.notificationProcessingHandler.onNotificationProcessing(context, notificationReceived);
                }
            } catch (Throwable t) {
                //noinspection ConstantConditions - displayNotification might have been called by the developer
                if (notifExtender.notificationReceivedResult == null)
                    OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "onNotificationProcessing throw an exception. Displaying normal OneSignal notification.", t);
                else
                    OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "onNotificationProcessing throw an exception. Extended notification displayed but custom processing did not finish.", t);
            }

            // If the developer did not call displayNotification from onNotificationProcessing
            if (notifExtender.notificationReceivedResult == null) {
                // Save as processed to prevent possible duplicate calls from canonical ids.
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "notificationReceivedResult is null, so setModifiedContent from the OSNotificationReceived class has not been called for notification id: " + notificationReceived.payload.notificationID);

                boolean display = !useNotificationProcessor &&
                        NotificationBundleProcessor.shouldDisplay(notifExtender.currentJsonPayload.optString("alert"));

                OSNotificationGenerationJob notifJob = notifExtender.createNotifJobFromCurrent(context);
                if (!display) {
                    if (!notificationReceived.isRestoring()) {
                        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "Notification not for display and not restoring notification with notification id: " + notificationReceived.payload.notificationID);

                        if (notifExtender.currentBaseOverrideSettings == null)
                            notifExtender.currentBaseOverrideSettings = new NotificationExtender.OverrideSettings();

                        notifJob.overrideSettings = notifExtender.currentBaseOverrideSettings;
                        notifJob.overrideSettings.androidNotificationId = -1;

                        NotificationBundleProcessor.processNotification(notifJob, true);
                        OneSignal.handleNotificationReceived(notifJob, false);
                    }
                    // If are are not displaying a restored notification make sure we mark it as dismissed
                    //   This will prevent it from being restored again.
                    else if (notifExtender.currentBaseOverrideSettings != null)
                        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "Notification not for display, but is restoring notification; storing as dismissed with notification id: " + notificationReceived.payload.notificationID);
                        NotificationBundleProcessor.markRestoredNotificationAsDismissed(notifJob);
                }
                else {
                    OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "Notification displayable with notification id: " + notificationReceived.payload.notificationID);
                    NotificationBundleProcessor.ProcessJobForDisplay(notifJob);
                }
            }

            return Result.success();
        }

    }
}
