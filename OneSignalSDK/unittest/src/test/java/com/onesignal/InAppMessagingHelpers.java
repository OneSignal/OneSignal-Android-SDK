package com.onesignal;


public class InAppMessagingHelpers {
    public static boolean evaluateMessage(OSInAppMessage message) {
        return OSInAppMessageController.getController().triggerController.evaluateMessageTriggers(message);
    }

    public static boolean dynamicTriggerShouldFire(OSTrigger trigger, String messageId) {
        return OSInAppMessageController.getController().triggerController.dynamicTriggerController.dynamicTriggerShouldFire(trigger, messageId);
    }
}
