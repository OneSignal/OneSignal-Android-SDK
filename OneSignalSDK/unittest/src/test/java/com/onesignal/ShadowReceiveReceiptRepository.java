package com.onesignal;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(OSReceiveReceiptRepository.class)
public class ShadowReceiveReceiptRepository {

    public static int callQuantity = 0;

    @Implementation
    public void sendReceiveReceipt(String appId, String playerId, String notificationId, OneSignalRestClient.ResponseHandler responseHandler) {
        callQuantity += 1;
    }
}
