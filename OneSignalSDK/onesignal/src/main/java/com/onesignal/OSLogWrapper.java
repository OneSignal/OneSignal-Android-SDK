package com.onesignal;

import androidx.annotation.NonNull;

class OSLogWrapper implements OSLogger {

    @Override
    public void verbose(@NonNull String message) {
        OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE, message);
    }

    @Override
    public void debug(@NonNull String message) {
        OneSignal.Log(OneSignal.LOG_LEVEL.DEBUG, message);
    }

    @Override
    public void info(@NonNull String message) {
        OneSignal.Log(OneSignal.LOG_LEVEL.INFO, message);
    }

    @Override
    public void warning(@NonNull String message) {
        OneSignal.Log(OneSignal.LOG_LEVEL.WARN, message);
    }

    @Override
    public void error(@NonNull String message) {
        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, message);
    }

    @Override
    public void error(@NonNull String message, @NonNull Throwable throwable) {
        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, message, throwable);
    }

}
