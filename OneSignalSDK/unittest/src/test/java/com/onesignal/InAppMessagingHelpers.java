package com.onesignal;


import java.util.ArrayList;
import java.util.List;

import com.onesignal.OSTrigger.OSTriggerOperatorType;

public class InAppMessagingHelpers {
    public static boolean evaluateMessage(OSInAppMessage message) {
        return OSInAppMessageController.getController().triggerController.evaluateMessageTriggers(message);
    }
}
