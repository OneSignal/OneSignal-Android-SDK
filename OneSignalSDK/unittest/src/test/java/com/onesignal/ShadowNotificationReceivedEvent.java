package com.onesignal;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(OSNotificationReceivedEvent.class)
public class ShadowNotificationReceivedEvent {

    private static boolean runningOnMainThreadCheck = false;

    @Implementation
    public static boolean isRunningOnMainThread() {
        // Remove Main thread check and throw
        runningOnMainThreadCheck = true;
        return false;
    }

    public static boolean isRunningOnMainThreadCheckCalled() {
        return runningOnMainThreadCheck;
    }

    public static void resetStatics() {
        runningOnMainThreadCheck = false;
    }
}
