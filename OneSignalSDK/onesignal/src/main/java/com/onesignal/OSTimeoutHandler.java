package com.onesignal;

import android.os.Handler;

class OSTimeoutHandler {

    private long timeout = 0;
    private long startTime = 0;

    // Handler used for post delayed timeout
    private Handler timeoutHandler;
    // Runnable used to execute code after the handler timeout is executed
    private Runnable timeoutRunnable;

    void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    void startTimeout(Runnable runnable) {
        // If the handler or runnable isn't null we do not want to start another
        if (timeoutHandler != null || timeoutRunnable != null)
            return;

        timeoutRunnable = runnable;
        startTime = System.currentTimeMillis();
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "Starting post delayed timeoutHandler with timeoutRunnable with a " + timeout / 1_000L + " second delay.");

        // Use UI thread so that anything requiring it within the runnable will not have issues
        // After this UI thread most work will be placed on a new Thread anyway, so this will be very temporary
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                timeoutHandler = new Handler();
                timeoutHandler.postDelayed(timeoutRunnable, timeout);
            }
        });
    }

    void destroyTimeout() {
        long timeDiff = System.currentTimeMillis() - startTime;
        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.INFO, "Destroying timeoutHandler and timeoutRunnable, " + timeDiff / 1_000L + " seconds passed out of " + timeout / 1_000L + " seconds.");

        if (timeoutHandler != null)
            timeoutHandler.removeCallbacks(timeoutRunnable);

        timeoutHandler = null;
        timeoutRunnable = null;
    }

}
