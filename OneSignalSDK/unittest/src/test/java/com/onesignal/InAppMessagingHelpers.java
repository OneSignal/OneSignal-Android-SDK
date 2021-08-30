package com.onesignal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.UUID;

import static com.onesignal.OneSignalPackagePrivateHelper.OSTestInAppMessageInternal;
import static com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger;
import static com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger.OSTriggerKind;

public class InAppMessagingHelpers {
    public static final String TEST_SPANISH_ANDROID_VARIANT_ID = "d8cc-11e4-bed1-df8f05be55ba-a4b3gj7f";
    public static final String TEST_ENGLISH_ANDROID_VARIANT_ID = "11e4-bed1-df8f05be55ba-a4b3gj7f-d8cc";
    public static final String ONESIGNAL_APP_ID = "b4f7f966-d8cc-11e4-bed1-df8f05be55ba";
    public static final String IAM_CLICK_ID = "12345678-1234-1234-1234-123456789012";
    public static final String IAM_PAGE_ID = "12345678-1234-ABCD-1234-123456789012";
    public static final String IAM_HAS_LIQUID = "has_liquid";

    public static boolean evaluateMessage(OSInAppMessageInternal message) {
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

    public static OSTestInAppMessageInternal buildTestMessageWitRedisplay(final int limit, final long delay) throws JSONException {
        return buildTestMessageWithMultipleDisplays(null, limit, delay);
    }

    // Most tests build a test message using only one trigger.
    // This convenience method makes it easy to build such a message
    public static OSTestInAppMessageInternal buildTestMessageWithSingleTrigger(final OSTriggerKind kind, final String key, final String operator, final Object value) throws JSONException {
        JSONArray triggersJson = basicTrigger(kind, key, operator, value);

        return buildTestMessage(triggersJson);
    }

    public static OSTestInAppMessageInternal buildTestMessageWithSingleTriggerAndRedisplay(final OSTriggerKind kind, final String key, final String operator,
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

    public static OSTestInAppMessageInternal buildTestMessageWithLiquid(final JSONArray triggerJson) throws JSONException {
        JSONObject json = basicIAMJSONObject(triggerJson);
        json.put(IAM_HAS_LIQUID, true);
        return new OSTestInAppMessageInternal(json);
    }

    public static OSTestInAppMessageInternal buildTestMessageWithSingleTriggerAndLiquid(final OSTriggerKind kind, final String key, final String operator, final Object value) throws JSONException {
        JSONArray triggersJson = basicTrigger(kind, key, operator, value);
        return buildTestMessageWithLiquid(triggersJson);
    }

    private static OSTestInAppMessageInternal buildTestMessageWithMultipleDisplays(final JSONArray triggerJson, final int limit, final long delay) throws JSONException {
        JSONObject json = basicIAMJSONObject(triggerJson);
        json.put("redisplay",  new JSONObject() {{
            put("limit", limit);
            put("delay", delay);//in seconds
        }});

        return new OSTestInAppMessageInternal(json);
    }

    public static OSTestInAppMessageInternal buildTestMessage(final JSONArray triggerJson) throws JSONException {
        return new OSTestInAppMessageInternal(basicIAMJSONObject(triggerJson));
    }

    public static OSTestInAppMessageInternal buildTestMessageWithEndTime(final OSTriggerKind kind, final String key, final String operator, final Object value, final boolean pastEndTime) throws JSONException {
        JSONArray triggerJson = basicTrigger(kind, key, operator, value);
        JSONObject json = basicIAMJSONObject(triggerJson);
        if (pastEndTime) {
            json.put("end_time", "1960-01-01T00:00:00.000Z");
        } else {
            json.put("end_time", "2200-01-01T00:00:00.000Z");
        }
        return new OSTestInAppMessageInternal(json);
    }

    public static OSTestInAppMessageInternal buildTestMessageWithMultipleTriggers(ArrayList<ArrayList<OSTestTrigger>> triggers) throws JSONException {
        JSONArray ors = buildTriggers(triggers);
        return buildTestMessage(ors);
    }

    public static OSTestInAppMessageInternal buildTestMessageWithMultipleTriggersAndRedisplay(ArrayList<ArrayList<OSTestTrigger>> triggers, int limit, long delay) throws JSONException {
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
            put("pageId", IAM_PAGE_ID);
            put("data", new JSONObject() {{
                put("test", "value");
            }});
        }};
    }

    public static JSONObject buildTestPageJson() throws JSONException {
        return new JSONObject() {{
            put("pageIndex", 1);
            put("pageId", IAM_PAGE_ID);
        }};
    }
}
