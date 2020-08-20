package com.onesignal;


import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Shadow class to emulate handler post delay work, Roboelectric runs everything under a custom MainThread
 * it also shadows its Loopers under a ShadowLooper.class, if we use looper outside Roboelectric custom thread
 * handler and looper will end disconnected and will end on a deadlock.
 * To solve this problem, for cases were we run Loopers under a custom thread, we shadow the TimeoutHandler
 * to emulate the same behaviour
 */
@Implements(OSTimeoutHandler.class)
public class ShadowTimeoutHandler {

    private static boolean mockDelay = false;
    private static long mockDelayMillis = 1;

    private HashMap<Runnable, ScheduledThreadPoolExecutor> executorHashMap = new HashMap<>();

    public static void setMockDelayMillis(long mockDelayMillis) {
        mockDelay = true;
        ShadowTimeoutHandler.mockDelayMillis = mockDelayMillis;
    }

    @Implementation
    public boolean postDelayed(final Runnable runnable, final long delayMillis) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.schedule(runnable, mockDelay ? mockDelayMillis : delayMillis, TimeUnit.MILLISECONDS);
        executorHashMap.put(runnable, executor);
        return true;
    }

    @Implementation
    public void removeCallbacks(Runnable runnable) {
        ScheduledThreadPoolExecutor executor = executorHashMap.get(runnable);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public static void resetStatics() {
        mockDelay = false;
        mockDelayMillis = 1;
    }
}