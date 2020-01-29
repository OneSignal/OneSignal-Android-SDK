package com.onesignal;

import org.json.JSONException;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;

import java.util.ArrayList;

@Implements(OSInAppMessageController.class)
public class ShadowOSInAppMessageController {

    @RealObject private OSInAppMessageController realObject;

    public static ArrayList<String> displayedMessages = new ArrayList<>();
    public static ArrayList<OneSignalPackagePrivateHelper.OSTestInAppMessage> dismissedMessages = new ArrayList<>();

    @Implementation
    public void displayMessage(final OSInAppMessage message) throws JSONException {
        displayedMessages.add(message.messageId);

        // Call original method
        Shadow.directlyOn(realObject, OSInAppMessageController.class).displayMessage(message);
    }

    @Implementation
    public void messageWasDismissed(final OSInAppMessage message) throws JSONException {
        // Call original method
        Shadow.directlyOn(realObject, OSInAppMessageController.class).messageWasDismissed(message);

        OneSignalPackagePrivateHelper.OSTestInAppMessage inAppMessage = new OneSignalPackagePrivateHelper.OSTestInAppMessage(message);
        inAppMessage.getDisplayStats().setDisplayStats(message.getDisplayStats());

        dismissedMessages.add(inAppMessage);
    }
}
