package com.onesignal;

import android.os.Looper;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * ONLY use to mock the timeout for old EMUI9 device.
 * ShadowHmsInstanceId takes cares of the normal flow
 */
@Implements(PushRegistratorHMS.class)
public class ShadowPushRegistratorHMS {

    public static boolean backgroundSuccessful;

    public static void resetStatics() {
        backgroundSuccessful = false;
    }

    @Implementation
    public static void doTimeOutWait() {
        if (backgroundSuccessful) {
            // prepare required since doTimeOutWait will be run from a new background thread.
            Looper.prepare();
            new HmsMessageServiceOneSignal().onNewToken(ShadowHmsInstanceId.DEFAULT_MOCK_HMS_TOKEN_VALUE, null);
        }
    }
}
