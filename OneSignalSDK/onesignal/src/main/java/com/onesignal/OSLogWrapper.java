package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

class OSLogWrapper implements OSLogger {


    public void log(@NonNull String message) {
        OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, message);
    }

    @Override
    public void log(@NonNull OneSignal.LOG_LEVEL level, @NonNull String message) {
        OneSignal.Log(level, message);
    }

    @Override
    public void log(@NonNull OneSignal.LOG_LEVEL level, @NonNull String message, @Nullable Throwable throwable) {
        OneSignal.Log(level, message, throwable);
    }
}
