package com.onesignal;

import androidx.annotation.Nullable;

import com.huawei.hms.push.RemoteMessage;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(RemoteMessage.class)
public class ShadowHmsRemoteMessage {

    @Nullable
    public static String data;
    public static int ttl;
    public static long sentTime;

    @Implementation
    public String getData() {
        return data;
    }

    @Implementation
    public int getTtl() {
        return ttl;
    }

    public long getSentTime() {
        return sentTime;
    }

}
