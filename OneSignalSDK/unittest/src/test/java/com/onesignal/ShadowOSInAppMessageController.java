package com.onesignal;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;

import java.util.ArrayList;

@Implements(OSInAppMessageController.class)
public class ShadowOSInAppMessageController {

    @RealObject private OSInAppMessageController realObject;

    public static ArrayList<String> displayedMessages = new ArrayList<>();

    @Implementation
    public void displayMessage(final OSInAppMessage message) {
        displayedMessages.add(message.messageId);
        // Call original method
        Shadow.directlyOn(realObject, OSInAppMessageController.class).displayMessage(message);
    }
}
