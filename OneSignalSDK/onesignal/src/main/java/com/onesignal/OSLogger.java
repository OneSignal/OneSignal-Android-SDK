package com.onesignal;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public interface OSLogger {

    void verbose(@NonNull String message);

    void debug(@NonNull String message);

    void warning(@NonNull String message);

    void error(@NonNull String message, @Nullable Throwable throwable);

}
