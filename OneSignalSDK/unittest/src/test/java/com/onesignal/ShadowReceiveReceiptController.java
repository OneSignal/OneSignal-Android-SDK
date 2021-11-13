package com.onesignal;

import androidx.work.Constraints;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(OSReceiveReceiptController.class)
public class ShadowReceiveReceiptController {

    // Removes the constraint `setRequiredNetworkType(NetworkType.CONNECTED)` which was causing unit tests to fail
    @Implementation
    public Constraints buildConstraints() {
        return Constraints.NONE;
    }
}
