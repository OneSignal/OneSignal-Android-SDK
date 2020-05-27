package com.onesignal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

abstract class NotificationWillShowInForegroundWorker extends Worker {

    NotificationWillShowInForegroundWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public class ExtNotificationWillShowInForegroundWorker extends NotificationWillShowInForegroundWorker {

        public ExtNotificationWillShowInForegroundWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            // TODO: Call ExtNotificationWillShowInForegroundHandler here
            return Result.success();
        }
    }

    public class AppNotificationWillShowInForegroundWorker extends NotificationWillShowInForegroundWorker {

        public AppNotificationWillShowInForegroundWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            // TODO: Call AppNotificationWillShowInForegroundHandler here
            return Result.success();
        }
    }
}
