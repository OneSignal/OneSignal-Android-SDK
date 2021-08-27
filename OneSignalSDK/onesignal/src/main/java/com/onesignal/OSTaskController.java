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

    // The concurrent queue in which we pin pending tasks upon finishing initialization
    private final ConcurrentLinkedQueue<Runnable> taskQueueWaitingForInit = new ConcurrentLinkedQueue<>();
    private final AtomicLong lastTaskId = new AtomicLong();
    private ExecutorService pendingTaskExecutor;

    protected final OSLogger logger;

    OSTaskController(OSLogger logger) {
        this.logger = logger;
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

    void shutdownNow() {
        if (pendingTaskExecutor != null) {
            pendingTaskExecutor.shutdownNow();
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

        @Override
        public String toString() {
            return "PendingTaskRunnable{" +
                    "innerTask=" + innerTask +
                    ", taskId=" + taskId +
                    '}';
        }
    }
}
