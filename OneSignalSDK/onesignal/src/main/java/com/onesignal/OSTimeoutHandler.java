package com.onesignal;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

class OSTimeoutHandler {

    private long timeout;

    private Handler timeoutHandler;
    private Runnable timeoutRunnable;

    void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    synchronized void startTimeout(@NonNull final Runnable runnable) {
        // If the handler or runnable isn't null we do not want to start another
        if (this.timeoutHandler != null && this.timeoutRunnable != null)
            return;

        this.timeoutRunnable = runnable;

        if (Looper.myLooper() == null)
            Looper.prepare();

        timeoutHandler = new Handler();
        timeoutHandler.postDelayed(timeoutRunnable, timeout);
    }

    synchronized void destroyTimeout() {
        if (timeoutHandler != null)
            timeoutHandler.removeCallbacks(timeoutRunnable);

        timeoutHandler = null;
        timeoutRunnable = null;
    }

}
