package com.onesignal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class OSLogWrapper implements OSLogger {

    public void verbose(@NonNull String message) {
        OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, message);
    }

    public void debug(@NonNull String message) {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, message);
    }

    @Override
    public void info(@NonNull String message) {
        OneSignal.Log(OneSignal.LOG_LEVEL.INFO, message);
    }

    public void warning(@NonNull String message) {
        OneSignal.Log(OneSignal.LOG_LEVEL.WARN, message);
    }

    @Override
    public void error(@NonNull String message) {
        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, message);
    }

    public void error(@NonNull String message, @Nullable Throwable throwable) {
        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, message, throwable);
    }

}
