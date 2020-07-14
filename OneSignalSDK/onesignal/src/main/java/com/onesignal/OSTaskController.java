package com.onesignal;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

class OSTaskController {

    // the concurrent queue in which we pin pending tasks upon finishing initialization
    static final ConcurrentLinkedQueue<Runnable> taskQueueWaitingForInit = new ConcurrentLinkedQueue<>();
    private static final AtomicLong lastTaskId = new AtomicLong();
    private static ExecutorService pendingTaskExecutor;

    private final OSLogger logger;
    private final OSRemoteParamController paramController;

    private List<String> methodsAvailableForDelay;

    OSTaskController(OSRemoteParamController paramController, OSLogger logger) {
        this.paramController = paramController;
        this.logger = logger;

        methodsAvailableForDelay = new ArrayList<>(
                Arrays.asList("getTags()", "SyncHashedEmail()", "setExternalUserId()", "setSubscription()",
                        "promptLocation()", "idsAvailable()", "sendTag()", "sendTags()", "handleNotificationOpen()",
                        "setEmail()"));
    }

    boolean shouldQueueTaskForInit(String task) {
        return !paramController.isRemoteParamsCallDone() && methodsAvailableForDelay.contains(task);
    }

    boolean shouldRunTaskThroughQueue() {
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

    void addTaskWaitingForInit(Runnable runnable) {
        taskQueueWaitingForInit.add(runnable);
    }

    void addTaskToQueue(Runnable runnable) {
        addTaskToQueue(new PendingTaskRunnable(this, runnable));
    }

    private void addTaskToQueue(PendingTaskRunnable task) {
        task.taskId = lastTaskId.incrementAndGet();

        if (pendingTaskExecutor == null) {
            logger.debug("Adding a task to the pending queue with ID: " + task.taskId);
            // The tasks haven't been executed yet...add them to the waiting queue
            addTaskWaitingForInit(task);
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

    void startPendingTasks() {
        if (!taskQueueWaitingForInit.isEmpty()) {
            pendingTaskExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(@NonNull Runnable runnable) {
                    Thread newThread = new Thread(runnable);
                    newThread.setName("OS_PENDING_EXECUTOR_" + newThread.getId());
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
    }
}
