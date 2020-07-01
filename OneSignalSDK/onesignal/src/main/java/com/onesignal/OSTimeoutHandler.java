package com.onesignal;

import android.os.Handler;

class OSTimeoutHandler {

    private long timeout = 0;

    private Handler timeoutHandler;
    private Runnable timeoutRunnable;

    void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    void startTimeout(final Runnable runnable) {
        // If the handler or runnable isn't null we do not want to start another
        if (this.timeoutHandler != null || this.timeoutRunnable != null)
            return;

        this.timeoutRunnable = runnable;
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                timeoutHandler = new Handler();
                timeoutHandler.postDelayed(timeoutRunnable, timeout);
            }
        });
    }

    void destroyTimeout() {
        if (timeoutHandler != null)
            timeoutHandler.removeCallbacks(timeoutRunnable);

        timeoutHandler = null;
        timeoutRunnable = null;
    }

}
