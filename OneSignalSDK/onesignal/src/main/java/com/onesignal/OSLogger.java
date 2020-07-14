package com.onesignal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface OSLogger {

    void verbose(@NonNull String message);

    void debug(@NonNull String message);

    void info(@NonNull String message);

    void warning(@NonNull String message);

    void error(@NonNull String message);

    void error(@NonNull String message, @Nullable Throwable throwable);

}
