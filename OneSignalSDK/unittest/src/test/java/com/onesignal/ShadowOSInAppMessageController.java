package com.onesignal;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import java.util.ArrayList;

@Implements(OSInAppMessageController.class)
public class ShadowOSInAppMessageController {
    public static ArrayList<OSInAppMessage> displayedMessages = new ArrayList<>();

    @Implementation
    protected void displayMessage(OSInAppMessage message) {
        displayedMessages.add(message);
    }
}
