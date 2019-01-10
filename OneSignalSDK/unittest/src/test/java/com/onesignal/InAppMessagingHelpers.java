package com.onesignal;

import com.onesignal.OSTriggerController.OSDynamicTriggerType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

public class InAppMessagingHelpers {
    public static String DYNAMIC_TRIGGER_SESSION_DURATION = OSDynamicTriggerType.SESSION_DURATION.toString();
    public static String DYNAMIC_TRIGGER_EXACT_TIME = OSDynamicTriggerType.TIME.toString();

    public static final String testSpanishAndroidVariantId = "d8cc-11e4-bed1-df8f05be55ba-a4b3gj7f";
    public static final String testEnglishAndroidVariantId = "11e4-bed1-df8f05be55ba-a4b3gj7f-d8cc";
    public static final String testMessageId = "a4b3gj7f-d8cc-11e4-bed1-df8f05be55ba";
    public static final String ONESIGNAL_APP_ID = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";

    public static boolean evaluateMessage(OSInAppMessage message) {
        return OSInAppMessageController.getController().triggerController.evaluateMessageTriggers(message);
    }

    public static boolean dynamicTriggerShouldFire(OSTrigger trigger, String messageId) {
        return OSInAppMessageController.getController().triggerController.dynamicTriggerController.dynamicTriggerShouldFire(trigger, messageId);
    }

    public static void resetSessionLaunchTime() {
        OSDynamicTriggerController.sessionLaunchTime = new Date();
    }

    // Convenience method that wraps an object in a JSON Array
    public static JSONArray wrap(final Object object) {
        return new JSONArray() {{
            put(object);
        }};
    }

    // Most tests build a test message using only one trigger.
    // This convenience method makes it easy to build such a message
    public static OneSignalPackagePrivateHelper.OSTestInAppMessage buildTestMessageWithSingleTrigger(final String key, final String operator, final Object value) throws JSONException {
        JSONObject triggerJson = new JSONObject() {{
            put("property", key);
            put("operator", operator);
            put("value", value);
            put("id", UUID.randomUUID().toString());
        }};

        JSONArray triggersJson = wrap(wrap(triggerJson));

        return buildTestMessage(triggersJson);
    }

    public static OneSignalPackagePrivateHelper.OSTestInAppMessage buildTestMessage(final JSONArray triggerJson) throws JSONException {
        // builds a test message to test JSON parsing constructor of OSInAppMessage
        JSONObject json = new JSONObject() {{
            put("id", testMessageId);
            put("variants", new JSONObject() {{
                put("android", new JSONObject() {{
                    put("es", testSpanishAndroidVariantId);
                    put("en", testEnglishAndroidVariantId);
                }});
            }});
            put("max_display_time", 30);
            put("triggers", triggerJson);
            put("actions", new JSONArray() {{
                put(buildTestActionJson());
            }});
        }};

        return new OneSignalPackagePrivateHelper.OSTestInAppMessage(json);
    }

    public static OneSignalPackagePrivateHelper.OSTestInAppMessage buildTestMessageWithMultipleTriggers(ArrayList<ArrayList<OneSignalPackagePrivateHelper.OSTestTrigger>> triggers) throws JSONException {
        JSONArray ors = new JSONArray();

        for (ArrayList<OneSignalPackagePrivateHelper.OSTestTrigger> andBlock : triggers) {
            JSONArray ands = new JSONArray();

            for (final OneSignalPackagePrivateHelper.OSTestTrigger trigger : andBlock) {
                ands.put(new JSONObject() {{
                    put("property", trigger.property);
                    put("operator", trigger.operatorType.toString());
                    put("value", trigger.value);
                    put("id", UUID.randomUUID().toString());
                }});
            }

            ors.put(ands);
        }

        return buildTestMessage(ors);
    }

    public static OneSignalPackagePrivateHelper.OSTestTrigger buildTrigger(final String key, final String operator, final Object value) throws JSONException {
        JSONObject triggerJson = new JSONObject() {{
            put("property", key);
            put("operator", operator);
            put("value", value);
            put("id", UUID.randomUUID().toString());
        }};

        return new OneSignalPackagePrivateHelper.OSTestTrigger(triggerJson);
    }

    public static JSONObject buildTestActionJson() throws JSONException {
        return new JSONObject() {{
            put("action_id", "Test_action_id");
            put("url", "https://www.onesignal.com");
            put("url_target", "webview");
            put("close", true);
            put("data", new JSONObject() {{
                put("test", "value");
            }});
        }};
    }
}
