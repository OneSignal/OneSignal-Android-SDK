package com.onesignal;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(OSReceiveReceiptController.class)
public class ShadowReceiveReceiptController {

    @Implementation
    public boolean isReceiveReceiptEnabled() {
        return true;
    }
}
