package com.onesignal;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import com.onesignal.OSTrigger.OSTriggerOperatorType;

public class OSTriggerController {
    private ConcurrentHashMap<String, Object> triggers;

    private static OSTriggerController sharedInstance;

    public static OSTriggerController getController() {
        if (sharedInstance == null) {
            sharedInstance = new OSTriggerController();
        }

        return sharedInstance;
    }

    public boolean evaluateMessageTriggers(OSInAppMessage message) {
        for (ArrayList<OSTrigger> conditions : message.triggers) {

            // only evaluate dynamic triggers after we've evaluated all others
            ArrayList<OSTrigger> dynamicTriggers = new ArrayList<>();

            for (int i = 0; i < conditions.size(); i++) {
                OSTrigger trigger = conditions.get(i);

                boolean lastElement = i == conditions.size() - 1;

                if (OSDynamicTriggerType.fromString(trigger.property) != null) {
                    dynamicTriggers.add(trigger);
                    continue;
                } else if (this.triggers.get(trigger.property == null) {
                    if (trigger.operatorType == OSTriggerOperatorType.NOT_EXISTS) {
                        if (lastElement) {
                            return true;
                        } else continue;
                    }

                    break;
                }

                if (trigger.operatorType == OSTriggerOperatorType.EXISTS) {
                    if (lastElement) {
                        return true;
                    } else continue;
                }

                Object realValue = this.triggers.get(trigger.property);

                // TODO: Continue adding evaluation logic
            }
        }

        return false;
    }

    public enum OSDynamicTriggerType {
        SESSION_DURATION("os_session_duration"),
        TIME("os_time")

        private String text;

        OSDynamicTriggerType(String type) {
            this.text = type;
        }

        @Override
        public String toString() {
            return this.text;
        }

        public static OSDynamicTriggerType fromString(String text) {
            for (OSDynamicTriggerType type : OSDynamicTriggerType.values()) {
                if (type.text.equalsIgnoreCase(text)) {
                    return type;
                }
            }

            return null;
        }
        }
}