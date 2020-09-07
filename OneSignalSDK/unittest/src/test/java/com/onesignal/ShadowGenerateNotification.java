package com.onesignal;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(GenerateNotification.class)
public class ShadowGenerateNotification {

    private static boolean runningOnMainThreadCheck = false;

    @Implementation
    public static void isRunningOnMainThreadCheck() {
        // Remove Main thread check and throw
        runningOnMainThreadCheck = true;
    }

    public static boolean isRunningOnMainThreadCheckCalled() {
        return runningOnMainThreadCheck;
    }

    public static void resetStatics() {
        runningOnMainThreadCheck = false;
    }
}