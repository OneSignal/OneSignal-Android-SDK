package com.test.onesignal;


import android.annotation.SuppressLint;
import android.app.Activity;

import com.onesignal.BuildConfig;
import com.onesignal.InAppMessagingHelpers;
import com.onesignal.OSTrigger;
import com.onesignal.OneSignal;
import com.onesignal.ShadowAdvertisingIdProviderGPS;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowJobService;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorGCM;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;
import com.onesignal.OSInAppMessage;
import com.onesignal.OSTrigger.OSTriggerOperatorType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.android.controller.ActivityController;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import edu.emory.mathcs.backport.java.util.Arrays;

import static junit.framework.Assert.*;

@Config(packageName = "com.onesignal.example",
        shadows = {
                ShadowOneSignalRestClient.class,
                ShadowPushRegistratorGCM.class,
                ShadowOSUtils.class,
                ShadowAdvertisingIdProviderGPS.class,
                ShadowCustomTabsClient.class,
                ShadowCustomTabsSession.class,
                ShadowNotificationManagerCompat.class,
                ShadowJobService.class
        },
        instrumentedPackages = {"com.onesignal"},
        constants = BuildConfig.class,
        sdk = 21)
@RunWith(RobolectricTestRunner.class)
public class InAppMessagingTests {
    public static OSInAppMessage message;

    public static final String testMessageId = "a4b3gj7f-d8cc-11e4-bed1-df8f05be55ba";
    public static final String testContentId = "d8cc-11e4-bed1-df8f05be55ba-a4b3gj7f";
    private static final String ONESIGNAL_APP_ID = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";
    @SuppressLint("StaticFieldLeak")
    private static Activity blankActivity;
    private static ActivityController<BlankActivity> blankActivityController;

    @BeforeClass
    public static void setupClass() throws Exception {
        ShadowLog.stream = System.out;

        try {
            message = buildTestMessageWithSingleTrigger("os_session_duration", ">=", 3);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        TestHelpers.beforeTestSuite();

        Field OneSignal_CurrentSubscription = OneSignal.class.getDeclaredField("subscribableStatus");
        OneSignal_CurrentSubscription.setAccessible(true);

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        StaticResetHelper.saveStaticValues();
    }

    @Before
    public void beforeEachTest() throws Exception {
        blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
        blankActivity = blankActivityController.get();

        OneSignalInit();
    }

    // Convenience method that wraps an object in a JSON Array
    static JSONArray wrap(final Object object) {
        return new JSONArray() {{
            put(object);
        }};
    }

    // Most tests build a test message using only one trigger.
    // This convenience method makes it easy to build such a message
    static OSInAppMessage buildTestMessageWithSingleTrigger(final String key, final String operator, final Object value) throws JSONException {
        JSONObject triggerJson = new JSONObject() {{
            put("property", key);
            put("operator", operator);
            put("value", value);
        }};

        JSONArray triggersJson = wrap(wrap(triggerJson));

        return buildTestMessage(triggersJson);
    }

    static OSInAppMessage buildTestMessage(final JSONArray triggerJson) throws JSONException {
        // builds a test message to test JSON parsing constructor of OSInAppMessage
        JSONObject json = new JSONObject() {{
            put("id", testMessageId);
            put("content_id", testContentId);
            put("max_display_time", 30);
            put("triggers", triggerJson);
        }};

        return new OSInAppMessage(json);
    }

    @Test
    public void testBuiltMessage() {
        assertEquals(message.messageId, testMessageId);
        assertEquals(message.contentId, testContentId);
        assertEquals(message.maxDisplayTime, 30.0);
    }

    @Test
    public void testBuiltMessageTrigger() {
        OSTrigger trigger = message.triggers.get(0).get(0);

        assertEquals(trigger.operatorType, OSTriggerOperatorType.GREATER_THAN_OR_EQUAL_TO);
        assertEquals(trigger.property, "os_session_duration");
        assertEquals(trigger.value, 3);
    }

    @Test
    public void testSaveMultipleTriggerValues() {
        HashMap<String, Object> testTriggers = new HashMap<>();
        testTriggers.put("test1", "value1");
        testTriggers.put("test2", "value2");

        OneSignal.addTriggers(testTriggers);

        assertEquals(OneSignal.getTriggerValueForKey("test1"), "value1");
        assertEquals(OneSignal.getTriggerValueForKey("test2"), "value2");
    }

    @Test
    public void testDeleteSavedTriggerValue() {
        OneSignal.addTrigger("test1", "value1");

        assertEquals(OneSignal.getTriggerValueForKey("test1"), "value1");

        OneSignal.removeTriggerforKey("test1");

        assertNull(OneSignal.getTriggerValueForKey("test1"));
    }

    @Test
    public void testGreaterThanOperator() throws JSONException {
        OneSignal.addTrigger("test1", 2);

        OSInAppMessage trueMessage = buildTestMessageWithSingleTrigger("test1", ">", 1);
        OSInAppMessage falseMessage = buildTestMessageWithSingleTrigger("test1", ">", 3);

        assertTrue(InAppMessagingHelpers.evaluateMessage(trueMessage));
        assertFalse(InAppMessagingHelpers.evaluateMessage(falseMessage));
    }

    @Test
    public void testGreaterThanOrEqualToOperator() throws JSONException {
        OneSignal.addTrigger("test1", 3);

        OSInAppMessage trueMessage = buildTestMessageWithSingleTrigger("test1", ">=", 2);
        OSInAppMessage falseMessage = buildTestMessageWithSingleTrigger("test1", ">=", 4);

        assertTrue(InAppMessagingHelpers.evaluateMessage(trueMessage));
        assertFalse(InAppMessagingHelpers.evaluateMessage(falseMessage));
    }

    @Test
    public void testLessThanOperator() throws JSONException {
        OneSignal.addTrigger("test2", 3);

        OSInAppMessage trueMessage = buildTestMessageWithSingleTrigger("test2", "<", 4);
        OSInAppMessage falseMessage = buildTestMessageWithSingleTrigger("test2", "<", -23);

        assertTrue(InAppMessagingHelpers.evaluateMessage(trueMessage));
        assertFalse(InAppMessagingHelpers.evaluateMessage(falseMessage));
    }

    @Test
    public void testLessThanOrEqualToOperator() throws JSONException {
        OneSignal.addTrigger("test2", 512);

        OSInAppMessage trueMessage = buildTestMessageWithSingleTrigger("test2", "<=", 1024);
        OSInAppMessage falseMessage = buildTestMessageWithSingleTrigger("test2", "<=", 511);

        assertTrue(InAppMessagingHelpers.evaluateMessage(trueMessage));
        assertFalse(InAppMessagingHelpers.evaluateMessage(falseMessage));
    }

    @Test
    public void testEqualityOperator() throws JSONException {
        OneSignal.addTrigger("test3", 42);

        OSInAppMessage trueMessage = buildTestMessageWithSingleTrigger("test3", "==", 42);
        OSInAppMessage falseMessage = buildTestMessageWithSingleTrigger("test3", "==", 1);

        assertTrue(InAppMessagingHelpers.evaluateMessage(trueMessage));
        assertFalse(InAppMessagingHelpers.evaluateMessage(falseMessage));
    }

    @Test
    public void testNotEqualOperator() throws JSONException {
        OneSignal.addTrigger("test4", 1);

        OSInAppMessage trueMessage = buildTestMessageWithSingleTrigger("test4", "!=", 3);
        OSInAppMessage falseMessage = buildTestMessageWithSingleTrigger("test4", "!=", 1);

        assertTrue(InAppMessagingHelpers.evaluateMessage(trueMessage));
        assertFalse(InAppMessagingHelpers.evaluateMessage(falseMessage));
    }

    @Test
    public void testContainsOperator() throws JSONException {
        OneSignal.addTrigger("test5", new ArrayList<String>() {{
            add("test string 1");
        }});

        OSInAppMessage trueMessage = buildTestMessageWithSingleTrigger("test5", "contains", "test string 1");
        OSInAppMessage falseMessage = buildTestMessageWithSingleTrigger("test5", "contains", "test string 2");

        assertTrue(InAppMessagingHelpers.evaluateMessage(trueMessage));
        assertFalse(InAppMessagingHelpers.evaluateMessage(falseMessage));
    }

    @Test
    public void testExistsOperator() throws JSONException {
        OneSignal.addTrigger("test6", "test trig");

        OSInAppMessage trueMessage = buildTestMessageWithSingleTrigger("test6", "exists", null);
        OSInAppMessage falseMessage = buildTestMessageWithSingleTrigger("test_6", "exists", null);

        assertTrue(InAppMessagingHelpers.evaluateMessage(trueMessage));
        assertFalse(InAppMessagingHelpers.evaluateMessage(falseMessage));
    }

    @Test
    public void testNotExistsOperator() throws JSONException {
        OneSignal.addTrigger("test7", "test trig");

        OSInAppMessage trueMessage = buildTestMessageWithSingleTrigger("test_7", "not_exists", null);
        OSInAppMessage falseMessage = buildTestMessageWithSingleTrigger("test7", "not_exists", null);

        assertTrue(InAppMessagingHelpers.evaluateMessage(trueMessage));
        assertFalse(InAppMessagingHelpers.evaluateMessage(falseMessage));
    }

    private void OneSignalInit() {
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.NONE);
        ShadowOSUtils.subscribableStatus = 1;
        OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID);
        blankActivityController.resume();
    }
}
