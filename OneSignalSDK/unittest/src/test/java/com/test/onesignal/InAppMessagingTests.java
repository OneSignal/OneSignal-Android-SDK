package com.test.onesignal;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.Log;

import com.onesignal.BuildConfig;
import com.onesignal.InAppMessagingHelpers;
import com.onesignal.OSInAppMessageAction;
import com.onesignal.OneSignal;
import com.onesignal.ShadowAdvertisingIdProviderGPS;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowJobService;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorGCM;
import com.onesignal.ShadowDynamicTimer;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;
import com.onesignal.OneSignalPackagePrivateHelper.OSTestInAppMessage;
import com.onesignal.OneSignalPackagePrivateHelper.OSTestInAppMessageAction;
import com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.android.controller.ActivityController;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

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
                ShadowJobService.class,
                ShadowDynamicTimer.class
        },
        instrumentedPackages = {"com.onesignal"},
        constants = BuildConfig.class,
        sdk = 21)
@RunWith(RobolectricTestRunner.class)
public class InAppMessagingTests {

    private static final double REQUIRED_TIMER_ACCURACY = 1.25;
    private static OSTestInAppMessage message;
    private static final String testMessageId = "a4b3gj7f-d8cc-11e4-bed1-df8f05be55ba";
    private static final String testContentId = "d8cc-11e4-bed1-df8f05be55ba-a4b3gj7f";
    private static final String ONESIGNAL_APP_ID = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";

    @SuppressLint("StaticFieldLeak")
    private static Activity blankActivity;
    private static ActivityController<BlankActivity> blankActivityController;

    @BeforeClass
    public static void setupClass() throws Exception {
        ShadowLog.stream = System.out;

        try {
            message = buildTestMessageWithSingleTrigger("os_session_duration", OSTestTrigger.OSTriggerOperatorType.GREATER_THAN_OR_EQUAL_TO.toString(), 3);
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

    @After
    public void afterEachTest() {
        // reset back to the default
        ShadowDynamicTimer.shouldScheduleTimers = true;
    }

    // Convenience method that wraps an object in a JSON Array
    private static JSONArray wrap(final Object object) {
        return new JSONArray() {{
            put(object);
        }};
    }

    // Most tests build a test message using only one trigger.
    // This convenience method makes it easy to build such a message
    private static OSTestInAppMessage buildTestMessageWithSingleTrigger(final String key, final String operator, final Object value) throws JSONException {
        JSONObject triggerJson = new JSONObject() {{
            put("property", key);
            put("operator", operator);
            put("value", value);
            put("id", UUID.randomUUID().toString());
        }};

        JSONArray triggersJson = wrap(wrap(triggerJson));

        return buildTestMessage(triggersJson);
    }

    private static OSTestInAppMessage buildTestMessage(final JSONArray triggerJson) throws JSONException {
        // builds a test message to test JSON parsing constructor of OSInAppMessage
        JSONObject json = new JSONObject() {{
            put("id", testMessageId);
            put("content_id", testContentId);
            put("max_display_time", 30);
            put("triggers", triggerJson);
            put("actions", new JSONArray() {{
                put(buildTestActionJson());
            }});
        }};

        return new OSTestInAppMessage(json);
    }

    private static OSTestTrigger buildTrigger(final String key, final String operator, final Object value) throws JSONException {
        JSONObject triggerJson = new JSONObject() {{
            put("property", key);
            put("operator", operator);
            put("value", value);
            put("id", UUID.randomUUID().toString());
        }};

        return new OSTestTrigger(triggerJson);
    }

    private static JSONObject buildTestActionJson() throws JSONException {
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

    /**
     * Convenience function that saves a local trigger (localValue) for the property name "test_property"
     * then creates an in-app message with a trigger (triggerValue) for the same property name. It
     * then evaluates the message for the given trigger conditions and returns the boolean, which
     * indicates whether or not the message should be shown.
     *
     * For example, we can set up a test where the app has a property value of 3 and we want to
     * test to make sure that if a message has a trigger value of 2 and an operator > that it
     * returns true when evaluated, because 3 > 2
     */
    private boolean comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType operator, Object triggerValue, Object localValue) throws JSONException {
        if (localValue != null)
            OneSignal.addTrigger("test_property", localValue);
        else
            OneSignal.removeTriggerforKey("test_property");

        OSTestInAppMessage testMessage = buildTestMessageWithSingleTrigger("test_property", operator.toString(), triggerValue);

        return InAppMessagingHelpers.evaluateMessage(testMessage);
    }

    @Test
    public void testBuiltMessage() {
        assertEquals(message.messageId, testMessageId);
        assertEquals(message.contentId, testContentId);
        assertEquals(message.maxDisplayTime, 30.0);
        assertEquals(message.actions.size(), 1);
    }

    @Test
    public void testBuiltMessageTrigger() {
        OSTestTrigger trigger = (OSTestTrigger)message.triggers.get(0).get(0);

        assertEquals(trigger.operatorType, OSTestTrigger.OSTriggerOperatorType.GREATER_THAN_OR_EQUAL_TO);
        assertEquals(trigger.property, "os_session_duration");
        assertEquals(trigger.value, 3);
    }

    @Test
    public void testParsesMessageActions() throws JSONException {
        OSTestInAppMessageAction action = new OSTestInAppMessageAction(buildTestActionJson());

        assertEquals(action.actionId, "Test_action_id");
        assertEquals(action.actionUrl.toString(), "https://www.onesignal.com");
        assertEquals(action.closes(), true);
        assertEquals(action.urlTarget, OSInAppMessageAction.OSInAppMessageActionUrlType.IN_APP_WEBVIEW);
        assertEquals(action.additionalData.getString("test"), "value");
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
        assertTrue(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.GREATER_THAN, 1, 2));
        assertFalse(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.GREATER_THAN, 5, 3));
    }

    @Test
    public void testGreaterThanOrEqualToOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.GREATER_THAN_OR_EQUAL_TO, 2, 2.9));
        assertFalse(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.GREATER_THAN_OR_EQUAL_TO, 4, 3));
    }

    @Test
    public void testLessThanOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.LESS_THAN, 32, 2));
        assertFalse(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.LESS_THAN, 2, 3));
    }

    @Test
    public void testLessThanOrEqualToOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.LESS_THAN_OR_EQUAL_TO, 5, 4));
        assertFalse(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.LESS_THAN_OR_EQUAL_TO, 3, 4));
    }

    @Test
    public void testEqualityOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.EQUAL_TO, 0.1, 0.1));
        assertFalse(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.EQUAL_TO, 0.0, 2));
    }

    @Test
    public void testNotEqualOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.NOT_EQUAL_TO, 3, 3.01));
        assertFalse(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.NOT_EQUAL_TO, 3.1, 3.1));
    }

    @Test
    public void testContainsOperator() throws JSONException {
        ArrayList localValue = new ArrayList<String>() {{
            add("test string 1");
        }};

        assertTrue(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.CONTAINS, "test string 1", localValue));
        assertFalse(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.CONTAINS, "test string 2", localValue));
    }

    @Test
    public void testExistsOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.EXISTS, null, "test"));
        assertFalse(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.EXISTS, null, null));
    }

    @Test
    public void testNotExistsOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.NOT_EXISTS, null, null));
        assertFalse(comparativeOperatorTest(OSTestTrigger.OSTriggerOperatorType.NOT_EXISTS, null, "test"));
    }

    @Test
    public void testMessageSchedulesSessionDurationTimer() throws JSONException {
        OSTestTrigger trigger = buildTrigger(InAppMessagingHelpers.DYNAMIC_TRIGGER_SESSION_DURATION, OSTestTrigger.OSTriggerOperatorType.EQUAL_TO.toString(), 10);

        InAppMessagingHelpers.resetSessionLaunchTime();

        // this evaluates the message and should schedule a timer for 10 seconds into the session
        assertFalse(InAppMessagingHelpers.dynamicTriggerShouldFire(trigger, testMessageId));

        // verify that the timer was scheduled ~10 seconds
        assertTrue(roughlyEqualTimerValues(10.0, ShadowDynamicTimer.mostRecentTimerDelaySeconds()));
    }

    @Test
    public void testMessageSchedulesExactTimeTimer() throws JSONException {
        OSTestTrigger trigger = buildTrigger(InAppMessagingHelpers.DYNAMIC_TRIGGER_EXACT_TIME,
                OSTestTrigger.OSTriggerOperatorType.GREATER_THAN.toString(), (((double)(new Date()).getTime() / 1000.0f) + 13));

        InAppMessagingHelpers.resetSessionLaunchTime();

        assertFalse(InAppMessagingHelpers.dynamicTriggerShouldFire(trigger, testMessageId));

        assertTrue(roughlyEqualTimerValues(13.0, ShadowDynamicTimer.mostRecentTimerDelaySeconds()));
    }



    private boolean roughlyEqualTimerValues(double desired, double actual) {
        return Math.abs(desired - actual) < REQUIRED_TIMER_ACCURACY;
    }

    private void OneSignalInit() {
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.NONE);
        ShadowOSUtils.subscribableStatus = 1;
        OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID);
        blankActivityController.resume();
    }
}
