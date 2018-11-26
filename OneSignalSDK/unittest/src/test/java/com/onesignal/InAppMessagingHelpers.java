package com.onesignal;


import java.util.ArrayList;
import java.util.List;

import com.onesignal.OSTrigger.OSTriggerOperatorType;

public class InAppMessagingHelpers {
    public static OSInAppMessage buildMessage(String messageId, String contentId, ArrayList<ArrayList<OSTrigger>> triggers) {
        OSInAppMessage message = new OSInAppMessage();

        message.messageId = messageId;
        message.contentId = contentId;
        message.triggers = triggers;

        return message;
    }

    public static OSTrigger buildTrigger(String property, OSTriggerOperatorType operator, Object value) {
        OSTrigger trigger = new OSTrigger();

        trigger.property = property;
        trigger.operatorType = operator;
        trigger.value = value;

        return trigger;
    }

    /**
     * Used to build a test message for tests that don't look at the
     * message itself (ie. messages that test trigger evaluation)
     */
    public static OSInAppMessage buildDefaultTestMessageWithTrigger(OSTrigger trigger) {
        ArrayList inner = new ArrayList<OSTrigger>();
        inner.add(trigger);
        ArrayList triggers = new ArrayList<ArrayList<OSTrigger>>();
        triggers.add(inner);
        return buildMessage("test_id", "test_content_id", triggers);
    }

    public static boolean evaluateMessage(OSInAppMessage message) {
        return OSTriggerController.getController().evaluateMessageTriggers(message);
    }
}
