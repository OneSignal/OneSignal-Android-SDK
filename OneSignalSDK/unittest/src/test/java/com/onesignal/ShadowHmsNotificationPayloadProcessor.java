package com.onesignal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(NotificationPayloadProcessorHMS.class)
public class ShadowHmsNotificationPayloadProcessor {

    private static @Nullable
    String messageData;

    public static void resetStatics() {
        messageData = null;
    }

    @Implementation
    public static void processDataMessageReceived(@NonNull final Context context, @Nullable String data) {
        messageData = data;
    }

    @Nullable
    public static String getMessageData() {
        return messageData;
    }
}
