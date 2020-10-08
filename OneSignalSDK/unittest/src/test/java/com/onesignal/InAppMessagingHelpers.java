package com.onesignal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import static com.onesignal.OneSignalPackagePrivateHelper.OSTestInAppMessage;
import static com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger;
import static com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger.OSTriggerKind;

public class InAppMessagingHelpers {
    public static final String TEST_SPANISH_ANDROID_VARIANT_ID = "d8cc-11e4-bed1-df8f05be55ba-a4b3gj7f";
    public static final String TEST_ENGLISH_ANDROID_VARIANT_ID = "11e4-bed1-df8f05be55ba-a4b3gj7f-d8cc";
    public static final String ONESIGNAL_APP_ID = "b4f7f966-d8cc-11e4-bed1-df8f05be55ba";
    public static final String IAM_CLICK_ID = "12345678-1234-1234-1234-123456789012";
    public static final String IAM_PAGE_ID = "12345678-1234-ABCD-1234-123456789012";

    public static boolean evaluateMessage(OSInAppMessage message) {
        return OneSignal.getInAppMessageController().triggerController.evaluateMessageTriggers(message);
    }

    public static boolean dynamicTriggerShouldFire(OSTrigger trigger) {
        return OneSignal.getInAppMessageController().triggerController.dynamicTriggerController.dynamicTriggerShouldFire(trigger);
    }

    public static void resetSessionLaunchTime() {
        OSDynamicTriggerController.resetSessionLaunchTime();
    }

    public static void clearTestState() {
        OneSignal.pauseInAppMessages(false);
        OneSignal.getInAppMessageController().getInAppMessageDisplayQueue().clear();
    }

    // Convenience method that wraps an object in a JSON Array
    public static JSONArray wrap(final Object object) {
        return new JSONArray() {{ put(object); }};
    }

    private static JSONArray basicTrigger(final OSTriggerKind kind, final String key, final String operator, final Object value) throws JSONException {
        JSONObject triggerJson = new JSONObject() {{
            put("id", UUID.randomUUID().toString());
            put("kind", kind.toString());
            put("property", key);
            put("operator", operator);
            put("value", value);
        }};

        return wrap(wrap(triggerJson));
    }

    public static OSTestInAppMessage buildTestMessageWitRedisplay(final int limit, final long delay) throws JSONException {
        return buildTestMessageWithMultipleDisplays(null, limit, delay);
    }

    // Most tests build a test message using only one trigger.
    // This convenience method makes it easy to build such a message
    public static OSTestInAppMessage buildTestMessageWithSingleTrigger(final OSTriggerKind kind, final String key, final String operator, final Object value) throws JSONException {
        JSONArray triggersJson = basicTrigger(kind, key, operator, value);

        return buildTestMessage(triggersJson);
    }

    public static OSTestInAppMessage buildTestMessageWithSingleTriggerAndRedisplay(final OSTriggerKind kind, final String key, final String operator,
                                                                                   final Object value, int limit, long delay) throws JSONException {
        JSONArray triggersJson = basicTrigger(kind, key, operator, value);

        return buildTestMessageWithMultipleDisplays(triggersJson, limit, delay);
    }

    private static JSONObject basicIAMJSONObject(final JSONArray triggerJson) throws JSONException {
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
            if (triggerJson != null)
                put("triggers", triggerJson);
            else
                put("triggers", new JSONArray());
            put("actions", new JSONArray() {{
                put(buildTestActionJson());
            }});
        }};

        return json;
    }

    private static OSTestInAppMessage buildTestMessageWithMultipleDisplays(final JSONArray triggerJson, final int limit, final long delay) throws JSONException {
        JSONObject json = basicIAMJSONObject(triggerJson);
        json.put("redisplay",  new JSONObject() {{
            put("limit", limit);
            put("delay", delay);//in seconds
        }});

        return new OSTestInAppMessage(json);
    }

    public static OSTestInAppMessage buildTestMessage(final JSONArray triggerJson) throws JSONException {
        return new OSTestInAppMessage(basicIAMJSONObject(triggerJson));
    }

    public static OSTestInAppMessage buildTestMessageWithMultipleTriggers(ArrayList<ArrayList<OSTestTrigger>> triggers) throws JSONException {
        JSONArray ors = buildTriggers(triggers);
        return buildTestMessage(ors);
    }

    public static OSTestInAppMessage buildTestMessageWithMultipleTriggersAndRedisplay(ArrayList<ArrayList<OSTestTrigger>> triggers, int limit, long delay) throws JSONException {
        JSONArray ors = buildTriggers(triggers);
        return buildTestMessageWithMultipleDisplays(ors, limit, delay);
    }

    private static JSONArray buildTriggers(ArrayList<ArrayList<OSTestTrigger>> triggers) throws JSONException {
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

        return ors;
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
            put("page_id", IAM_PAGE_ID);
            put("data", new JSONObject() {{
                put("test", "value");
            }});
        }};
    }

    public static JSONObject buildTestPageJson() throws JSONException {
        return new JSONObject() {{
            put("page_index", 1);
            put("page_id", IAM_PAGE_ID);
        }};
    }
}
