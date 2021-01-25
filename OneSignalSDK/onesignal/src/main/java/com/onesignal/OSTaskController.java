package com.onesignal;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

class OSTaskController {

    static final String OS_PENDING_EXECUTOR = "OS_PENDING_EXECUTOR_";

    // Available task for delay
    static final String GET_TAGS = "getTags()";
    static final String SET_SMS_NUMBER = "setSMSNumber()";
    static final String SET_EMAIL = "setEmail()";
    static final String LOGOUT_EMAIL = "logoutEmail()";
    static final String SYNC_HASHED_EMAIL = "syncHashedEmail()";
    static final String SET_EXTERNAL_USER_ID = "setExternalUserId()";
    static final String SET_SUBSCRIPTION = "setSubscription()";
    static final String PROMPT_LOCATION = "promptLocation()";
    static final String IDS_AVAILABLE = "idsAvailable()";
    static final String SEND_TAG = "sendTag()";
    static final String SEND_TAGS = "sendTags()";
    static final String SET_LOCATION_SHARED = "setLocationShared()";
    static final String SET_REQUIRES_USER_PRIVACY_CONSENT = "setRequiresUserPrivacyConsent()";
    static final String UNSUBSCRIBE_WHEN_NOTIFICATION_ARE_DISABLED = "unsubscribeWhenNotificationsAreDisabled()";
    static final String HANDLE_NOTIFICATION_OPEN = "handleNotificationOpen()";
    static final String CANCEL_GROUPED_NOTIFICATIONS = "cancelGroupedNotifications()";
    static final String PAUSE_IN_APP_MESSAGES = "pauseInAppMessages()";
    static final String APP_LOST_FOCUS = "onAppLostFocus()";
    static final String SEND_OUTCOME = "sendOutcome()";
    static final String SEND_UNIQUE_OUTCOME = "sendUniqueOutcome()";
    static final String SEND_OUTCOME_WITH_VALUE = "sendOutcomeWithValue()";
    static final HashSet<String> METHODS_AVAILABLE_FOR_DELAY = new HashSet<>(Arrays.asList(
            GET_TAGS,
            SET_SMS_NUMBER,
            SET_EMAIL,
            LOGOUT_EMAIL,
            SYNC_HASHED_EMAIL,
            SET_EXTERNAL_USER_ID,
            SET_SUBSCRIPTION,
            PROMPT_LOCATION,
            IDS_AVAILABLE,
            SEND_TAG,
            SEND_TAGS,
            SET_LOCATION_SHARED,
            SET_REQUIRES_USER_PRIVACY_CONSENT,
            UNSUBSCRIBE_WHEN_NOTIFICATION_ARE_DISABLED,
            HANDLE_NOTIFICATION_OPEN,
            APP_LOST_FOCUS,
            SEND_OUTCOME,
            SEND_UNIQUE_OUTCOME,
            SEND_OUTCOME_WITH_VALUE
    ));

    // The concurrent queue in which we pin pending tasks upon finishing initialization
    private final ConcurrentLinkedQueue<Runnable> taskQueueWaitingForInit = new ConcurrentLinkedQueue<>();
    private final AtomicLong lastTaskId = new AtomicLong();
    private ExecutorService pendingTaskExecutor;

    private final OSLogger logger;
    private final OSRemoteParamController paramController;

    OSTaskController(OSRemoteParamController paramController, OSLogger logger) {
        this.paramController = paramController;
        this.logger = logger;
    }

    /**
     * Check if task should be queue
     *
     * @return true if remote params aren't available and current method needs them otherwise false
     * */
    boolean shouldQueueTaskForInit(String task) {
        return !paramController.isRemoteParamsCallDone() && METHODS_AVAILABLE_FOR_DELAY.contains(task);
    }

    boolean shouldRunTaskThroughQueue() {
        // Don't schedule again a running pending task
        if (Thread.currentThread().getName().contains(OS_PENDING_EXECUTOR))
            return false;

        if (OneSignal.isInitDone() && pendingTaskExecutor == null) {
            // There never were any waiting tasks
            return false;
        }

        // If init isn't finished and the pending executor hasn't been defined yet...
        if (!OneSignal.isInitDone() && pendingTaskExecutor == null)
            return true;

        // or if the pending executor is alive and hasn't been shutdown yet...
        return !pendingTaskExecutor.isShutdown();
    }

    void addTaskToQueue(Runnable runnable) {
        addTaskToQueue(new PendingTaskRunnable(this, runnable));
    }

    private void addTaskToQueue(PendingTaskRunnable task) {
        task.taskId = lastTaskId.incrementAndGet();

        if (pendingTaskExecutor == null) {
            logger.debug("Adding a task to the pending queue with ID: " + task.taskId);
            // The tasks haven't been executed yet...add them to the waiting queue
            taskQueueWaitingForInit.add(task);
        } else if (!pendingTaskExecutor.isShutdown()) {
            logger.debug("Executor is still running, add to the executor with ID: " + task.taskId);
            try {
                // If the executor isn't done with tasks, submit the task to the executor
                pendingTaskExecutor.submit(task);
            } catch (RejectedExecutionException e) {
                logger.info("Executor is shutdown, running task manually with ID: " + task.taskId);
                // Run task manually when RejectedExecutionException occurs due to the ThreadPoolExecutor.AbortPolicy
                // The pendingTaskExecutor is already shutdown by the time it tries to run the task
                // Issue #669
                // https://github.com/OneSignal/OneSignal-Android-SDK/issues/669
                task.run();
                e.printStackTrace();
            }
        }
    }

    /**
     * Called by OneSignal.init as last step on the init
     * Run available pending tasks on an Executor
     */
    void startPendingTasks() {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, "startPendingTasks with task queue quantity: " + taskQueueWaitingForInit.size());
        if (!taskQueueWaitingForInit.isEmpty()) {
            pendingTaskExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(@NonNull Runnable runnable) {
                    Thread newThread = new Thread(runnable);
                    newThread.setName(OS_PENDING_EXECUTOR + newThread.getId());
                    return newThread;
                }
            });

            while (!taskQueueWaitingForInit.isEmpty()) {
                pendingTaskExecutor.submit(taskQueueWaitingForInit.poll());
            }
        }
    }

    private void onTaskRan(long taskId) {
        if (lastTaskId.get() == taskId) {
            OneSignal.Log(OneSignal.LOG_LEVEL.INFO, "Last Pending Task has ran, shutting down");
            pendingTaskExecutor.shutdown();
        }
    }

    ConcurrentLinkedQueue<Runnable> getTaskQueueWaitingForInit() {
        return taskQueueWaitingForInit;
    }

    private static class PendingTaskRunnable implements Runnable {
        private OSTaskController controller;
        private Runnable innerTask;

        private long taskId;

        PendingTaskRunnable(OSTaskController controller, Runnable innerTask) {
            this.controller = controller;
            this.innerTask = innerTask;
        }

        @Override
        public void run() {
            innerTask.run();
            controller.onTaskRan(taskId);
        }

        @Override
        public String toString() {
            return "PendingTaskRunnable{" +
                    "innerTask=" + innerTask +
                    ", taskId=" + taskId +
                    '}';
        }
    }
}
