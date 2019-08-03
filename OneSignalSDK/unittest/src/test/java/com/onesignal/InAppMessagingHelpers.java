package com.onesignal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import static com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger;
import static com.onesignal.OneSignalPackagePrivateHelper.OSTestInAppMessage;
import static com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger.OSTriggerKind;

public class InAppMessagingHelpers {
    public static final String TEST_SPANISH_ANDROID_VARIANT_ID = "d8cc-11e4-bed1-df8f05be55ba-a4b3gj7f";
    public static final String TEST_ENGLISH_ANDROID_VARIANT_ID = "11e4-bed1-df8f05be55ba-a4b3gj7f-d8cc";
    public static final String ONESIGNAL_APP_ID = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";
    public static final String IAM_CLICK_ID = "12345678-1234-1234-1234-123456789012";

    public static boolean evaluateMessage(OSInAppMessage message) {
        return OSInAppMessageController.getController().triggerController.evaluateMessageTriggers(message);
    }

    public static boolean dynamicTriggerShouldFire(OSTrigger trigger) {
        return OSInAppMessageController.getController().triggerController.dynamicTriggerController.dynamicTriggerShouldFire(trigger);
    }

    public static void resetSessionLaunchTime() {
        OSDynamicTriggerController.sessionLaunchTime = new Date();
    }

    public static void clearTestState() {
        OneSignal.pauseInAppMessages(false);
        ShadowOSInAppMessageController.displayedMessages.clear();
        OSInAppMessageController.getController().messageDisplayQueue.clear();
    }

    // Convenience method that wraps an object in a JSON Array
    public static JSONArray wrap(final Object object) {
        return new JSONArray() {{ put(object); }};
    }

    // Most tests build a test message using only one trigger.
    // This convenience method makes it easy to build such a message
    public static OSTestInAppMessage buildTestMessageWithSingleTrigger(final OSTriggerKind kind, final String key, final String operator, final Object value) throws JSONException {
        JSONObject triggerJson = new JSONObject() {{
            put("id", UUID.randomUUID().toString());
            put("kind", kind.toString());
            put("property", key);
            put("operator", operator);
            put("value", value);
        }};

        JSONArray triggersJson = wrap(wrap(triggerJson));

        return buildTestMessage(triggersJson);
    }

    public static OSTestInAppMessage buildTestMessage(final JSONArray triggerJson) throws JSONException {
        // builds a test message to test JSON parsing constructor of OSInAppMessage
        JSONObject json = new JSONObject() {{
            put("id", UUID.randomUUID().toString());
            put("variants", new JSONObject() {{
                put("android", new JSONObject() {{
                    put("es", TEST_SPANISH_ANDROID_VARIANT_ID);
                    put("en", TEST_ENGLISH_ANDROID_VARIANT_ID);
                }});
            }});
            put("max_display_time", 30);
            put("triggers", triggerJson);
            put("actions", new JSONArray() {{
                put(buildTestActionJson());
            }});
        }};

        return new OSTestInAppMessage(json);
    }

    public static OSTestInAppMessage buildTestMessageWithMultipleTriggers(ArrayList<ArrayList<OSTestTrigger>> triggers) throws JSONException {
        JSONArray ors = new JSONArray();

        for (ArrayList<OSTestTrigger> andBlock : triggers) {
            JSONArray ands = new JSONArray();

            for (final OSTestTrigger trigger : andBlock) {
                ands.put(new JSONObject() {{
                    put("id", UUID.randomUUID().toString());
                    put("kind", trigger.kind.toString());
                    put("property", trigger.property);
                    put("operator", trigger.operatorType.toString());
                    put("value", trigger.value);
                }});
            }

            ors.put(ands);
        }

        return buildTestMessage(ors);
    }

    public static OSTestTrigger buildTrigger(final OSTriggerKind kind, final String key, final String operator, final Object value) throws JSONException {
        JSONObject triggerJson = new JSONObject() {{
            put("id", UUID.randomUUID().toString());
            put("kind", kind.toString());
            put("property", key);
            put("operator", operator);
            put("value", value);
        }};

        return new OSTestTrigger(triggerJson);
    }

    public static JSONObject buildTestActionJson() throws JSONException {
        return new JSONObject() {{
            put("click_type", "button");
            put("id", IAM_CLICK_ID);
            put("name", "click_name");
            put("url", "https://www.onesignal.com");
            put("url_target", "webview");
            put("close", true);
            put("data", new JSONObject() {{
                put("test", "value");
            }});
        }};
    }
}
