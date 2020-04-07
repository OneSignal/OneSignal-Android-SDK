package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public interface OSLogger {

    void log(@NonNull OneSignal.LOG_LEVEL level, @NonNull String message);

    void log(@NonNull final OneSignal.LOG_LEVEL level, @NonNull String message, @Nullable Throwable throwable);

}
