package com.test.onesignal;

import android.annotation.SuppressLint;
import android.app.Activity;

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

import static com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger.OSTriggerOperatorType;

import org.json.JSONException;
import org.junit.After;
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
import java.util.Date;
import java.util.HashMap;

import static junit.framework.Assert.*;

@Config(
   packageName = "com.onesignal.example",
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
    instrumentedPackages = { "com.onesignal" },
    constants = BuildConfig.class,
    sdk = 26
)
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
            message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger("os_session_duration", OSTriggerOperatorType.GREATER_THAN_OR_EQUAL_TO.toString(), 3);
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
            OneSignal.removeTriggerForKey(key);
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
    private static boolean comparativeOperatorTest(OSTriggerOperatorType operator, Object triggerValue, Object localValue) throws JSONException {
        setLocalTriggerValue("test_property", localValue);
        OSTestInAppMessage testMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger("test_property", operator.toString(), triggerValue);
        return InAppMessagingHelpers.evaluateMessage(testMessage);
    }

    @Test
    public void testBuiltMessage() {
        assertEquals(message.messageId, InAppMessagingHelpers.TEST_MESSAGE_ID);
        assertNotNull(message.variants);
    }

    @Test
    public void testBuiltMessageVariants() {
        assertEquals(message.variants.get("android").get("es"), InAppMessagingHelpers.TEST_SPANISH_ANDROID_VARIANT_ID);
        assertEquals(message.variants.get("android").get("en"), InAppMessagingHelpers.TEST_ENGLISH_ANDROID_VARIANT_ID);
    }

    @Test
    public void testBuiltMessageTrigger() {
        OSTestTrigger trigger = (OSTestTrigger)message.triggers.get(0).get(0);

        assertEquals(trigger.operatorType, OSTriggerOperatorType.GREATER_THAN_OR_EQUAL_TO);
        assertEquals(trigger.property, "os_session_duration");
        assertEquals(trigger.value, 3);
    }

    @Test
    public void testParsesMessageActions() throws JSONException {
        OSTestInAppMessageAction action = new OSTestInAppMessageAction(InAppMessagingHelpers.buildTestActionJson());

        assertEquals(action.clickType, OSInAppMessageAction.ClickType.BUTTON);
        assertEquals(action.clickId, "Test_click_id");
        assertEquals(action.actionUrl, "https://www.onesignal.com");
        assertTrue(action.closes());
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

        OneSignal.removeTriggerForKey("test1");
        assertNull(OneSignal.getTriggerValueForKey("test1"));
    }

    @Test
    public void testGreaterThanOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTriggerOperatorType.GREATER_THAN, 1, 2));
        assertFalse(comparativeOperatorTest(OSTriggerOperatorType.GREATER_THAN, 5, 3));
    }

    @Test
    public void testGreaterThanOrEqualToOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTriggerOperatorType.GREATER_THAN_OR_EQUAL_TO, 2, 2.9));
        assertFalse(comparativeOperatorTest(OSTriggerOperatorType.GREATER_THAN_OR_EQUAL_TO, 4, 3));
    }

    @Test
    public void testLessThanOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTriggerOperatorType.LESS_THAN, 32, 2));
        assertFalse(comparativeOperatorTest(OSTriggerOperatorType.LESS_THAN, 2, 3));
    }

    @Test
    public void testLessThanOrEqualToOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTriggerOperatorType.LESS_THAN_OR_EQUAL_TO, 5, 4));
        assertFalse(comparativeOperatorTest(OSTriggerOperatorType.LESS_THAN_OR_EQUAL_TO, 3, 4));
    }

    @Test
    public void testEqualityOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTriggerOperatorType.EQUAL_TO, 0.1, 0.1));
        assertFalse(comparativeOperatorTest(OSTriggerOperatorType.EQUAL_TO, 0.0, 2));
    }

    @Test
    public void testNotEqualOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTriggerOperatorType.NOT_EQUAL_TO, 3, 3.01));
        assertFalse(comparativeOperatorTest(OSTriggerOperatorType.NOT_EQUAL_TO, 3.1, 3.1));
    }

    @Test
    public void testContainsOperator() throws JSONException {
        ArrayList localValue = new ArrayList<String>() {{
            add("test string 1");
        }};

        assertTrue(comparativeOperatorTest(OSTriggerOperatorType.CONTAINS, "test string 1", localValue));
        assertFalse(comparativeOperatorTest(OSTriggerOperatorType.CONTAINS, "test string 2", localValue));
    }

    @Test
    public void testExistsOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTriggerOperatorType.EXISTS, null, "test"));
        assertFalse(comparativeOperatorTest(OSTriggerOperatorType.EXISTS, null, null));
    }

    @Test
    public void testNotExistsOperator() throws JSONException {
        assertTrue(comparativeOperatorTest(OSTriggerOperatorType.NOT_EXISTS, null, null));
        assertFalse(comparativeOperatorTest(OSTriggerOperatorType.NOT_EXISTS, null, "test"));
    }

    @Test
    public void testMessageSchedulesSessionDurationTimer() throws JSONException {
        OSTestTrigger trigger = InAppMessagingHelpers.buildTrigger(InAppMessagingHelpers.DYNAMIC_TRIGGER_SESSION_DURATION, OSTriggerOperatorType.EQUAL_TO.toString(), 10);

        InAppMessagingHelpers.resetSessionLaunchTime();

        // this evaluates the message and should schedule a timer for 10 seconds into the session
        assertFalse(InAppMessagingHelpers.dynamicTriggerShouldFire(trigger));
        // verify that the timer was scheduled ~10 seconds
        assertTrue(roughlyEqualTimerValues(10.0, ShadowDynamicTimer.mostRecentTimerDelaySeconds()));
    }

    @Test
    public void testMessageSchedulesExactTimeTimer() throws JSONException {
        OSTestTrigger trigger = InAppMessagingHelpers.buildTrigger(
           InAppMessagingHelpers.DYNAMIC_TRIGGER_EXACT_TIME,
           OSTriggerOperatorType.GREATER_THAN.toString(), (((double)(new Date()).getTime() / 1_000.0f) + 13)
        );

        InAppMessagingHelpers.resetSessionLaunchTime();
        assertFalse(InAppMessagingHelpers.dynamicTriggerShouldFire(trigger));
        assertTrue(roughlyEqualTimerValues(13.0, ShadowDynamicTimer.mostRecentTimerDelaySeconds()));
    }

    @Test
    public void testMessageTriggersWithExactTimeTimerTypeWhenPastTime() throws JSONException {
        OSTestInAppMessage testMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
           InAppMessagingHelpers.DYNAMIC_TRIGGER_EXACT_TIME,
           OSTriggerOperatorType.GREATER_THAN.toString(),
           (((double)(new Date()).getTime() / 1_000.0f) - 10)
        );

        assertTrue(InAppMessagingHelpers.evaluateMessage(testMessage));
    }

    // This test makes sure that time-based triggers are considered once all non-time-based
    // triggers evaluate to true and will set up a timer if needed
    @Test
    public void testMixedTriggersScheduleTimer() throws JSONException {
        final OSTestTrigger timeBasedTrigger = InAppMessagingHelpers.buildTrigger(InAppMessagingHelpers.DYNAMIC_TRIGGER_SESSION_DURATION, OSTriggerOperatorType.GREATER_THAN.toString(), 5.0);
        final OSTestTrigger normalTrigger = InAppMessagingHelpers.buildTrigger("prop1", OSTriggerOperatorType.LESS_THAN_OR_EQUAL_TO.toString(), 3);

        // the time based trigger will be false (but should schedule a timer)
        // while the normal trigger should evaluate to true
        setLocalTriggerValue("prop1", 3);

        ArrayList<ArrayList<OSTestTrigger>> triggers = new ArrayList<ArrayList<OSTestTrigger>>() {{
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

    @Test
    public void testShouldTriggerWhen1OutOf3OrsAreMeet() throws JSONException {
        setLocalTriggerValue("prop1", 3);

        ArrayList<ArrayList<OSTestTrigger>> triggers = new ArrayList<ArrayList<OSTestTrigger>>() {{
            add(new ArrayList<OSTestTrigger>() {{
                add(InAppMessagingHelpers.buildTrigger("prop1", OSTriggerOperatorType.EQUAL_TO.toString(), 1));
            }});
            add(new ArrayList<OSTestTrigger>() {{
                add(InAppMessagingHelpers.buildTrigger("prop1", OSTriggerOperatorType.EQUAL_TO.toString(), 2));
            }});
            add(new ArrayList<OSTestTrigger>() {{
                add(InAppMessagingHelpers.buildTrigger("prop1", OSTriggerOperatorType.EQUAL_TO.toString(), 3));
            }});
        }};

        OSTestInAppMessage testMessage = InAppMessagingHelpers.buildTestMessageWithMultipleTriggers(triggers);
        assertTrue(InAppMessagingHelpers.evaluateMessage(testMessage));
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
