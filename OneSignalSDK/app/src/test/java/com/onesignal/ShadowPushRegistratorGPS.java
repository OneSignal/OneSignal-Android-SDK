package com.onesignal;

import android.content.Context;

import org.robolectric.annotation.Implements;

@Implements(PushRegistratorGPS.class)
public class ShadowPushRegistratorGPS {

    public static final String regId = "aspdfoh0fhj02hr-2h";

    public void registerForPush(Context context, String googleProjectNumber, PushRegistrator.RegisteredHandler callback) {
        callback.complete(regId);
    }
}
