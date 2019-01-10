package com.test.onesignal;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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
import com.onesignal.ShadowOSInAppMessageController;
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


import static com.test.onesignal.TestHelpers.flushBufferedSharedPrefs;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
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
                ShadowDynamicTimer.class,
                ShadowOSInAppMessageController.class
        },
        instrumentedPackages = {"com.onesignal"},
        constants = BuildConfig.class,
        sdk = 21)
@RunWith(RobolectricTestRunner.class)
public class InAppMessagingUnitTests {

    private static final double REQUIRED_TIMER_ACCURACY = 1.25;
    private static OSTestInAppMessage message;


    @SuppressLint("StaticFieldLeak")
    private static Activity blankActivity;
    private static ActivityController<BlankActivity> blankActivityController;

    @BeforeClass
    public static void setupClass() throws Exception {
        ShadowLog.stream = System.out;

        try {
            message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger("os_session_duration", OSTestTrigger.OSTriggerOperatorType.GREATER_THAN_OR_EQUAL_TO.toString(), 3);
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

        TestHelpers.beforeTestInitAndCleanup();

        OneSignalInit();
    }

    @After
    public void afterEachTest() {
        // reset back to the default
        ShadowDynamicTimer.shouldScheduleTimers = true;
        ShadowDynamicTimer.hasScheduledTimer = false;
        TestHelpers.afterTestCleanup();

        InAppMessagingHelpers.clearTestState();
    }

    private static void setLocalTriggerValue(String key, Object localValue) {
        if (localValue != null)
            OneSignal.addTrigger(key, localValue);
        else
            OneSignal.removeTriggerforKey(key);
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
        setLocalTriggerValue("test_property", localValue);

        OSTestInAppMessage testMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger("test_property", operator.toString(), triggerValue);

        return InAppMessagingHelpers.evaluateMessage(testMessage);
    }

    @Test
    public void testBuiltMessage() {
        assertEquals(message.messageId, InAppMessagingHelpers.testMessageId);
        assertNotNull(message.variants);
        assertEquals(message.maxDisplayTime, 30.0);
        assertEquals(message.actions.size(), 1);
    }

    @Test
    public void testBuiltMessageVariants() {
        assertEquals(message.variants.get("android").get("es"), InAppMessagingHelpers.testSpanishAndroidVariantId);
        assertEquals(message.variants.get("android").get("en"), InAppMessagingHelpers.testEnglishAndroidVariantId);
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
        OSTestInAppMessageAction action = new OSTestInAppMessageAction(InAppMessagingHelpers.buildTestActionJson());

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
        OSTestTrigger trigger = InAppMessagingHelpers.buildTrigger(InAppMessagingHelpers.DYNAMIC_TRIGGER_SESSION_DURATION, OSTestTrigger.OSTriggerOperatorType.EQUAL_TO.toString(), 10);

        InAppMessagingHelpers.resetSessionLaunchTime();

        // this evaluates the message and should schedule a timer for 10 seconds into the session
        assertFalse(InAppMessagingHelpers.dynamicTriggerShouldFire(trigger, InAppMessagingHelpers.testMessageId));

        // verify that the timer was scheduled ~10 seconds
        assertTrue(roughlyEqualTimerValues(10.0, ShadowDynamicTimer.mostRecentTimerDelaySeconds()));
    }

    @Test
    public void testMessageSchedulesExactTimeTimer() throws JSONException {
        OSTestTrigger trigger = InAppMessagingHelpers.buildTrigger(InAppMessagingHelpers.DYNAMIC_TRIGGER_EXACT_TIME,
                OSTestTrigger.OSTriggerOperatorType.GREATER_THAN.toString(), (((double)(new Date()).getTime() / 1000.0f) + 13));

        InAppMessagingHelpers.resetSessionLaunchTime();

        assertFalse(InAppMessagingHelpers.dynamicTriggerShouldFire(trigger, InAppMessagingHelpers.testMessageId));

        assertTrue(roughlyEqualTimerValues(13.0, ShadowDynamicTimer.mostRecentTimerDelaySeconds()));
    }

    // This test makes sure that time-based triggers are considered once all non-time-based
    // triggers evaluate to true and will set up a timer if needed
    @Test
    public void testMixedTriggersScheduleTimer() throws JSONException {
        final OSTestTrigger timeBasedTrigger = InAppMessagingHelpers.buildTrigger(InAppMessagingHelpers.DYNAMIC_TRIGGER_SESSION_DURATION, OSTestTrigger.OSTriggerOperatorType.GREATER_THAN.toString(), 5.0);
        final OSTestTrigger normalTrigger = InAppMessagingHelpers.buildTrigger("prop1", OSTestTrigger.OSTriggerOperatorType.LESS_THAN_OR_EQUAL_TO.toString(), 3);

        // the time based trigger will be false (but should schedule a timer)
        // while the normal trigger should evaluate to true
        setLocalTriggerValue("prop1", 3);

        ArrayList triggers = new ArrayList<ArrayList<OSTestTrigger>>() {{
            add(new ArrayList<OSTestTrigger>() {{
                add(timeBasedTrigger);
                add(normalTrigger);
            }});
        }};

        OSTestInAppMessage testMessage = InAppMessagingHelpers.buildTestMessageWithMultipleTriggers(triggers);

        assertFalse(InAppMessagingHelpers.evaluateMessage(testMessage));

        assertTrue(ShadowDynamicTimer.hasScheduledTimer);

        assertTrue(roughlyEqualTimerValues(5.0, ShadowDynamicTimer.mostRecentTimerDelaySeconds()));
    }

    // When a normal (non-time-based) trigger is false, the time-based triggers should not even
    // be considered (and no timers should be scheduled as a result)
    @Test
    public void testShouldNotConsiderTimeBasedTrigger() throws JSONException {
        final OSTestTrigger timeBasedTrigger = InAppMessagingHelpers.buildTrigger(InAppMessagingHelpers.DYNAMIC_TRIGGER_SESSION_DURATION, OSTestTrigger.OSTriggerOperatorType.GREATER_THAN.toString(), 5.0);
        final OSTestTrigger normalTrigger = InAppMessagingHelpers.buildTrigger("prop1", OSTestTrigger.OSTriggerOperatorType.LESS_THAN_OR_EQUAL_TO.toString(), 3);

        setLocalTriggerValue("prop1", 4);

        ArrayList triggers = new ArrayList<ArrayList<OSTestTrigger>>() {{
            add(new ArrayList<OSTestTrigger>() {{
                add(timeBasedTrigger);
                add(normalTrigger);
            }});
        }};

        OSTestInAppMessage testMessage = InAppMessagingHelpers.buildTestMessageWithMultipleTriggers(triggers);

        assertFalse(InAppMessagingHelpers.evaluateMessage(testMessage));

        assertFalse(ShadowDynamicTimer.hasScheduledTimer);
    }

    private boolean roughlyEqualTimerValues(double desired, double actual) {
        return Math.abs(desired - actual) < REQUIRED_TIMER_ACCURACY;
    }

    private void OneSignalInit() {
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.NONE);
        ShadowOSUtils.subscribableStatus = 1;
        OneSignal.init(blankActivity, "123456789", InAppMessagingHelpers.ONESIGNAL_APP_ID);
        blankActivityController.resume();
    }
}
