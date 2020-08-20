package com.onesignal;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(GenerateNotification.class)
public class ShadowGenerateNotification {

   @Implementation
    public static void isRunningOnMainThreadCheck() {
      // Remove Main thread check and throw
    }
}