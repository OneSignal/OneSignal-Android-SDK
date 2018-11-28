package com.onesignal;

import com.onesignal.OSTriggerController.OSDynamicTriggerType;

import java.util.Date;

public class InAppMessagingHelpers {
    public static String DYNAMIC_TRIGGER_SESSION_DURATION = OSDynamicTriggerType.SESSION_DURATION.toString();
    public static String DYNAMIC_TRIGGER_EXACT_TIME = OSDynamicTriggerType.TIME.toString();

    public static boolean evaluateMessage(OSInAppMessage message) {
        return OSInAppMessageController.getController().triggerController.evaluateMessageTriggers(message);
    }

    public static boolean dynamicTriggerShouldFire(OSTrigger trigger, String messageId) {
        return OSInAppMessageController.getController().triggerController.dynamicTriggerController.dynamicTriggerShouldFire(trigger, messageId);
    }

    public static void resetSessionLaunchTime() {
        OSDynamicTriggerController.sessionLaunchTime = new Date();
    }
}
