package com.onesignal;

public class OSInAppMessageController {
    private static OSInAppMessageController sharedInstance;

    public static OSInAppMessageController getController() {
        if (sharedInstance == null) {
            sharedInstance = new OSInAppMessageController();
        }

        return sharedInstance;
    }
}
