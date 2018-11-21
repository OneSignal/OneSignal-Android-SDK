package com.onesignal;


import java.util.ArrayList;
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
}
