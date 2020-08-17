package com.onesignal;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Implements(OSNotificationReceived.class)
public class ShadowNotificationReceived {

    private ScheduledThreadPoolExecutor executor = null;

    @Implementation
    public void startCompleteTimeOut(long timeout, Runnable runnable) {
        executor = new ScheduledThreadPoolExecutor(1);
        executor.schedule(runnable, timeout, TimeUnit.MILLISECONDS);
    }

    @Implementation
    public void destroyTimeout() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
