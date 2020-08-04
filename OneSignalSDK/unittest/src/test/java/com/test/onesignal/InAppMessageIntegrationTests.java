package com.test.onesignal;

import android.annotation.SuppressLint;
import android.app.Activity;

import androidx.test.core.app.ApplicationProvider;

import com.onesignal.InAppMessagingHelpers;
import com.onesignal.MockOSLog;
import com.onesignal.MockOSSharedPreferences;
import com.onesignal.MockOneSignalDBHelper;
import com.onesignal.MockSessionManager;
import com.onesignal.OSInAppMessageAction;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.OneSignalPackagePrivateHelper.OSTestInAppMessage;
import com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger;
import com.onesignal.OneSignalPackagePrivateHelper.TestOneSignalPrefs;
import com.onesignal.ShadowAdvertisingIdProviderGPS;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowDynamicTimer;
import com.onesignal.ShadowGMSLocationController;
import com.onesignal.ShadowJobService;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOSViewUtils;
import com.onesignal.ShadowOSWebView;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorFCM;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;
import com.onesignal.influence.OSTrackerFactory;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ThrowingRunnable;
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
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger.OSTriggerKind;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_getSessionListener;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setSessionManager;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setSharedPreferences;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setTrackerFactory;
import static com.onesignal.ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse;
import static com.test.onesignal.RestClientAsserts.assertMeasureOnV2AtIndex;
import static com.test.onesignal.TestHelpers.advanceSystemTimeBy;
import static com.test.onesignal.TestHelpers.assertMainThread;
import static com.test.onesignal.TestHelpers.fastColdRestartApp;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@Config(packageName = "com.onesignal.example",
        instrumentedPackages = {"com.onesignal"},
        shadows = {
                ShadowOneSignalRestClient.class,
                ShadowPushRegistratorFCM.class,
                ShadowOSUtils.class,
                ShadowGMSLocationController.class,
                ShadowAdvertisingIdProviderGPS.class,
                ShadowCustomTabsClient.class,
                ShadowCustomTabsSession.class,
                ShadowNotificationManagerCompat.class,
                ShadowJobService.class,
                ShadowDynamicTimer.class,
                ShadowOSWebView.class,
                ShadowOSViewUtils.class
        },
        sdk = 21
)
@RunWith(RobolectricTestRunner.class)
public class InAppMessageIntegrationTests {

    private static final String ONESIGNAL_APP_ID = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";
    private static final String IAM_CLICK_ID = "button_id_123";
    private static final String IAM_OUTCOME_NAME = "outcome_name";
    private static final String IAM_TAG_KEY = "test1";
    private static final float IAM_OUTCOME_WEIGHT = 5;
    private static final long SIX_MONTHS_TIME_SECONDS = 6 * 30 * 24 * 60 * 60;
    private static final int LIMIT = 5;
    private static final int DELAY = 60;
    private MockOSSharedPreferences preferences;
    private OSTrackerFactory trackerFactory;
    private MockSessionManager sessionManager;
    @SuppressLint("StaticFieldLeak")
    private static Activity blankActivity;
    private static ActivityController<BlankActivity> blankActivityController;

    private MockOneSignalDBHelper dbHelper;

    @BeforeClass
    public static void setupClass() throws Exception {
        ShadowLog.stream = System.out;

        TestHelpers.beforeTestSuite();

        Field OneSignal_CurrentSubscription = OneSignal.class.getDeclaredField("subscribableStatus");
        OneSignal_CurrentSubscription.setAccessible(true);

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        StaticResetHelper.saveStaticValues();
    }

    @Before
    public void beforeEachTest() throws Exception {
        ShadowDynamicTimer.shouldScheduleTimers = true;
        preferences = new MockOSSharedPreferences();
        trackerFactory = new OSTrackerFactory(preferences, new MockOSLog());
        sessionManager = new MockSessionManager(OneSignal_getSessionListener(), trackerFactory, new MockOSLog());
        blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
        blankActivity = blankActivityController.get();
        dbHelper = new MockOneSignalDBHelper(ApplicationProvider.getApplicationContext());
        TestHelpers.beforeTestInitAndCleanup();

        // Set remote_params GET response
        setRemoteParamsGetHtmlResponse();
    }

    @After
    public void afterEachTest() throws Exception {
        // reset back to the default
        ShadowDynamicTimer.shouldScheduleTimers = true;
        ShadowDynamicTimer.hasScheduledTimer = false;
        OneSignal.setInAppMessageClickHandler(null);
        TestHelpers.afterTestCleanup();

        InAppMessagingHelpers.clearTestState();
    }

    @Test
    public void testDisableInAppMessagingPreventsMessageDisplay() throws Exception {
        final OSTestInAppMessage testMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(OSTriggerKind.CUSTOM, "test_key", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 3);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(testMessage);
        }});

        OneSignalInit();
        threadAndTaskWait();

        // the SDK now has the in app message but it cannot be shown yet since the trigger is not valid
        // we will now disable in-app messages
        OneSignal.pauseInAppMessages(true);

        // We will set the trigger. However, since messaging is disabled, the message should not be shown
        OneSignal.addTrigger("test_key", 3);

        // Make sure 1 IAM is in the display queue
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        // Make sure no IAM is showing
        assertFalse(OneSignalPackagePrivateHelper.isInAppMessageShowing());
    }

    /**
     * Since it is possible for multiple in-app messages to be valid at the same time, we've implemented
     * a queue so that the SDK does not try to display both messages at the same time.
     */
    @Test
    public void testMultipleMessagesDoNotBothDisplay() throws Exception {
        initializeSdkWithMultiplePendingMessages();

        OneSignal.addTriggers(new HashMap<String, Object>() {{
            put("test_1", 3);
            put("test_2", 2);
        }});
        threadAndTaskWait();

        // Make sure 2 items are in the display queue
        assertEquals(2, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        // Make sure an IAM is showing
        assertTrue(OneSignalPackagePrivateHelper.isInAppMessageShowing());

        // Dismiss the message
        OneSignalPackagePrivateHelper.dismissCurrentMessage();
        threadAndTaskWait();

        // Make sure 1 item is in the display queue
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        // Make sure an IAM is showing
        assertTrue(OneSignalPackagePrivateHelper.isInAppMessageShowing());

        // Dismiss the message
        OneSignalPackagePrivateHelper.dismissCurrentMessage();
        threadAndTaskWait();

        // Make sure no items are in the display queue
        assertEquals(0, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        // Make sure no IAM is showing
        assertFalse(OneSignalPackagePrivateHelper.isInAppMessageShowing());
    }

    // This tests both rotating the device or the app being resumed.
    @Test
    public void testMessageDismissingWhileDeviceIsRotating() throws Exception {
        initializeSdkWithMultiplePendingMessages();

        // 1. Add trigger to show IAM
        OneSignal.addTriggers(new HashMap<String, Object>() {{
            put("test_1", 3);
            put("test_2", 2);
        }});
        threadAndTaskWait();

        // 2. Assert two IAM in the queue and 1 is showing
        assertEquals(2, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        assertTrue(OneSignalPackagePrivateHelper.isInAppMessageShowing());

        // 3. Rotate device - This will kick off a JS task to get the new height
        blankActivityController.pause();
        blankActivityController.resume();

        // 4. Dismiss the IAM
        OneSignalPackagePrivateHelper.WebViewManager.callDismissAndAwaitNextMessage();
        threadAndTaskWait();

        // 5. Now fire resize event which was scheduled in step 3.
        //    Test that this does not throw and handles this missing IAM view.
        ShadowOSWebView.fireEvalJSCallbacks();

        // 6. Make sure only 1 IAM ios left in queue now and it is showing
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        assertTrue(OneSignalPackagePrivateHelper.isInAppMessageShowing());
    }


    private void nextResponseMultiplePendingMessages() throws JSONException {
        final OSTestInAppMessage testFirstMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(OSTriggerKind.CUSTOM, "test_1", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 3);
        final OSTestInAppMessage testSecondMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(OSTriggerKind.CUSTOM, "test_2", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 2);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(testFirstMessage);
            add(testSecondMessage);
        }});
    }

    // initializes the SDK with multiple mock in-app messages and sets triggers so that
    // both in-app messages become valid and can be displayed
    private void initializeSdkWithMultiplePendingMessages() throws Exception {
        nextResponseMultiplePendingMessages();
        OneSignalInit();
        threadAndTaskWait();
    }

    @Test
    public void testTimedMessageIsDisplayed() throws Exception {
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(OSTriggerKind.SESSION_TIME, null, OSTestTrigger.OSTriggerOperator.GREATER_THAN.toString(), 0.05);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message);
        }});

        // the SDK should read the message from registration JSON, set up a timer, and once
        // the timer fires the message should get shown.
        OneSignalInit();
        threadAndTaskWait();

        // wait until the timer fires after 50ms and make sure the message gets displayed
        // we already have tests to make sure that the timer is actually being scheduled
        // for the correct amount of time, so all we are doing here is checking to
        // make sure the message actually gets displayed once the timer fires
        Awaitility.await()
                .atMost(new Duration(150, TimeUnit.MILLISECONDS))
                .pollInterval(new Duration(10, TimeUnit.MILLISECONDS))
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size() == 1;
                    }
                });
    }


    @Test
    public void testAfterLastInAppTimeIsDisplayed() throws Exception {
        final OSTestInAppMessage message1 = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.SESSION_TIME,
                null,
                OSTestTrigger.OSTriggerOperator.GREATER_THAN.toString(),
                0.05
        );

        ArrayList<ArrayList<OSTestTrigger>> triggers2 = new ArrayList<ArrayList<OSTestTrigger>>() {{
            add(new ArrayList<OSTestTrigger>() {{
                add(InAppMessagingHelpers.buildTrigger(OSTriggerKind.SESSION_TIME, null, OSTestTrigger.OSTriggerOperator.GREATER_THAN.toString(), 0.1));
                add(InAppMessagingHelpers.buildTrigger(OSTriggerKind.TIME_SINCE_LAST_IN_APP, null, OSTestTrigger.OSTriggerOperator.GREATER_THAN.toString(), 0.05));
            }});
        }};
        final OSTestInAppMessage message2 = InAppMessagingHelpers.buildTestMessageWithMultipleTriggers(triggers2);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message1);
            add(message2);
        }});

        // the SDK should read the message from registration JSON, set up a timer, and once
        // the timer fires the message should get shown.
        OneSignalInit();
        threadAndTaskWait();

        // wait until the timer fires after 50ms and make sure the message gets displayed
        // we already have tests to make sure that the timer is actually being scheduled
        // for the correct amount of time, so all we are doing here is checking to
        // make sure the message actually gets displayed once the timer fires
        Awaitility.await()
                .atMost(new Duration(150, TimeUnit.MILLISECONDS))
                .pollInterval(new Duration(10, TimeUnit.MILLISECONDS))
                .untilAsserted(new ThrowingRunnable() {
                    @Override
                    public void run() {
                        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
                        assertEquals(message1.messageId, OneSignalPackagePrivateHelper.getShowingInAppMessageId());
                    }
                });

        OneSignalPackagePrivateHelper.dismissCurrentMessage();

        // Second in app should now display
        Awaitility.await()
                .atMost(new Duration(1, TimeUnit.SECONDS))
                .pollInterval(new Duration(100, TimeUnit.MILLISECONDS))
                .untilAsserted(new ThrowingRunnable() {
                    @Override
                    public void run() {
                        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
                        assertEquals(message2.messageId, OneSignalPackagePrivateHelper.getShowingInAppMessageId());
                    }
                });
    }

    /**
     * If an in-app message should only be shown if (A) session_duration is > 30 seconds and
     * (B) a key/value trigger is set, and it should not set up a timer until all of the non-timer
     * based triggers for that message evaluate to true
     * <p>
     * For this test, a timer should never be scheduled because the key/value 'test_key' trigger
     * will not be set until the session duration has already exceeded the minimum (0.05 seconds)
     */
    @Test
    public void testTimedMessageDisplayedAfterAllTriggersValid() throws Exception {
        ArrayList<ArrayList<OSTestTrigger>> triggers = new ArrayList<ArrayList<OSTestTrigger>>() {{
            add(new ArrayList<OSTestTrigger>() {{
                add(InAppMessagingHelpers.buildTrigger(OSTriggerKind.CUSTOM, "test_key", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), "squirrel"));
                add(InAppMessagingHelpers.buildTrigger(OSTriggerKind.SESSION_TIME, null, OSTestTrigger.OSTriggerOperator.GREATER_THAN.toString(), 0.01));
            }});
        }};

        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithMultipleTriggers(triggers);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message);
        }});

        OneSignalInit();
        threadAndTaskWait();

        // no timer should be scheduled since 'test_key' != 'squirrel'
        assertFalse(ShadowDynamicTimer.hasScheduledTimer);
        assertEquals(0, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());

        // since we are not actually waiting on any logic to finish, sleeping here is fine
        Thread.sleep(20);

        // the message still should not be displayed
        assertEquals(0, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());

        // after setting this trigger the message should be displayed immediately
        OneSignal.addTrigger("test_key", "squirrel");
        threadAndTaskWait();

        // the message should now have been displayed
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        assertFalse(ShadowDynamicTimer.hasScheduledTimer);
    }

    @Test
    public void useCachedInAppListOnQuickColdRestart() throws Exception {
        // 1. Start app
        initializeSdkWithMultiplePendingMessages();
        // 2. Swipe away app
        fastColdRestartApp();
        // 3. Cold Start app
        initializeSdkWithMultiplePendingMessages();

        // Should used cached triggers since we won't be making an on_session call.
        //   Testing for this by trying to add a trigger that should display an IAM
        OneSignal.addTrigger("test_2", 2);
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
    }

    @Test
    public void useCachedInAppListOnQuickColdRestartWhenInitFromAppClass() throws Exception {
        // 1. Start app
        nextResponseMultiplePendingMessages();
        OneSignal.setAppId(ONESIGNAL_APP_ID);
        OneSignal.setAppContext(blankActivity.getApplicationContext());
        blankActivityController.resume();
        threadAndTaskWait();

        // 2. Swipe away app
        fastColdRestartApp();
        // 3. Cold Start app
        OneSignal.setAppId(ONESIGNAL_APP_ID);
        OneSignal.setAppContext(blankActivity.getApplicationContext());
        blankActivityController.resume();
        threadAndTaskWait();

        // Should used cached triggers since we won't be making an on_session call.
        //   Testing for this by trying to add a trigger that should display an IAM
        OneSignal.addTrigger("test_2", 2);
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
    }

    @Test
    public void doNotReshowInAppIfDismissed_evenAfterColdRestart() throws Exception {
        // 1. Start app
        initializeSdkWithMultiplePendingMessages();
        // 2. Trigger showing In App and dismiss it
        OneSignal.addTrigger("test_2", 2);
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        OneSignalPackagePrivateHelper.dismissCurrentMessage();
        // 3. Swipe away app
        fastColdRestartApp();
        // 4. Cold Start app
        initializeSdkWithMultiplePendingMessages();
        // 5. Set same trigger, should not display again
        OneSignal.addTrigger("test_2", 2);
        assertEquals(0, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
    }

    @Test
    public void reshowInAppIfDisplayedButNeverDismissedAfterColdRestart() throws Exception {
        // 1. Start app
        initializeSdkWithMultiplePendingMessages();
        // 2. Trigger showing In App
        OneSignal.addTrigger("test_2", 2);
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        // 3. Swipe away app
        fastColdRestartApp();
        // 4. Cold Start app
        initializeSdkWithMultiplePendingMessages();
        assertEquals(0, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        // 5. Set same trigger, should now display again, since it was never dismissed
        OneSignal.addTrigger("test_2", 2);
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
    }

    @Test
    public void testInAppMessageOnlyReceivesClickIdOnce() throws Exception {
        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // 2. Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.SESSION_TIME,
                null,
                OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(),
                null
        );

        // 2. Count IAM as clicked
        JSONObject action = new JSONObject() {{
            put("id", IAM_CLICK_ID);
        }};
        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);

        // 3. Ensure click is sent
        ShadowOneSignalRestClient.Request iamImpressionRequest = ShadowOneSignalRestClient.requests.get(2);
        assertEquals("in_app_messages/" + message.messageId + "/click", iamImpressionRequest.url);
        assertEquals(3, ShadowOneSignalRestClient.requests.size());

        // 4. Call IAM clicked again, ensure a 2nd network call is not made.
        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);
        assertEquals(3, ShadowOneSignalRestClient.requests.size());

        // Verify clickId was persisted locally
        Set<String> testClickedMessages = TestOneSignalPrefs.getStringSet(
           TestOneSignalPrefs.PREFS_ONESIGNAL,
           TestOneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
           null
        );
        assertEquals(1, testClickedMessages.size());
    }

    @Test
    public void testInAppMessageClickActionOutcome() throws Exception {
        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // Enable influence outcomes
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        // 2. Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.SESSION_TIME,
                null,
                OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(),
                null
        );

        final JSONArray outcomes = new JSONArray();
        outcomes.put(new JSONObject() {{
            put("name", IAM_OUTCOME_NAME);
        }});
        JSONObject action = new JSONObject() {{
            put("id", IAM_CLICK_ID);
            put("outcomes", outcomes);
        }};

        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);

        // 3. Ensure outcome is sent
        ShadowOneSignalRestClient.Request iamOutcomeRequest = ShadowOneSignalRestClient.requests.get(3);

        assertEquals("outcomes/measure", iamOutcomeRequest.url);
        // Requests: Param request + Players Request + Click request + Outcome Request
        assertEquals(4, ShadowOneSignalRestClient.requests.size());
        assertFalse(iamOutcomeRequest.payload.has("weight"));
        assertFalse(iamOutcomeRequest.payload.has("direct"));
        assertEquals(IAM_OUTCOME_NAME, iamOutcomeRequest.payload.get("id"));
        assertEquals(1, iamOutcomeRequest.payload.get("device_type"));
    }

    @Test
    public void testInAppMessageClickActionOutcomeV2() throws Exception {
        // Enable IAM v2
        preferences = new MockOSSharedPreferences();
        preferences.saveBool(preferences.getPreferencesName(), preferences.getOutcomesV2KeyName(), true);
        trackerFactory = new OSTrackerFactory(preferences, new MockOSLog());
        sessionManager = new MockSessionManager(OneSignal_getSessionListener(), trackerFactory, new MockOSLog());

        OneSignal_setSharedPreferences(preferences);
        OneSignal_setTrackerFactory(trackerFactory);
        OneSignal_setSessionManager(sessionManager);

        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // Enable influence outcomes
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        // 2. Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.SESSION_TIME,
                null,
                OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(),
                null
        );

        final JSONArray outcomes = new JSONArray();
        outcomes.put(new JSONObject() {{
            put("name", IAM_OUTCOME_NAME);
        }});
        JSONObject action = new JSONObject() {{
            put("id", IAM_CLICK_ID);
            put("outcomes", outcomes);
        }};

        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);

        // 3. Ensure outcome is sent
        assertMeasureOnV2AtIndex(3, "outcome_name", new JSONArray().put(message.messageId), new JSONArray(), null, null);
    }

    @Test
    public void testInAppMessageClickActionOutcomeWithValue() throws Exception {
        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // Enable influence outcomes
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        // 2. Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.SESSION_TIME,
                null,
                OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(),
                null
        );

        final JSONArray outcomesWithWeight = new JSONArray();
        outcomesWithWeight.put(new JSONObject() {{
            put("name", IAM_OUTCOME_NAME);
            put("weight", IAM_OUTCOME_WEIGHT);
        }});
        JSONObject actionWithWeight = new JSONObject() {{
            put("id", IAM_CLICK_ID);
            put("outcomes", outcomesWithWeight);
        }};

        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, actionWithWeight);

        // 3. Ensure outcome is sent
        ShadowOneSignalRestClient.Request iamOutcomeRequest = ShadowOneSignalRestClient.requests.get(3);

        assertEquals("outcomes/measure", iamOutcomeRequest.url);
        // Requests: Param request + Players Request + Click request + Outcome Request
        assertEquals(4, ShadowOneSignalRestClient.requests.size());
        assertEquals(IAM_OUTCOME_WEIGHT, iamOutcomeRequest.payload.get("weight"));
        assertEquals(IAM_OUTCOME_NAME, iamOutcomeRequest.payload.get("id"));
        assertFalse(iamOutcomeRequest.payload.has("direct"));
        assertEquals(1, iamOutcomeRequest.payload.get("device_type"));
    }

    @Test
    public void testOnIAMActionSendsOutcome_usingOutcomesV2() throws Exception {
        // Enable IAM v2
        preferences = new MockOSSharedPreferences();
        preferences.saveBool(preferences.getPreferencesName(), preferences.getOutcomesV2KeyName(), true);
        trackerFactory = new OSTrackerFactory(preferences, new MockOSLog());
        sessionManager = new MockSessionManager(OneSignal_getSessionListener(), trackerFactory, new MockOSLog());

        OneSignal_setSharedPreferences(preferences);
        OneSignal_setTrackerFactory(trackerFactory);
        OneSignal_setSessionManager(sessionManager);

        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.CUSTOM, "test_1", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 2);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message);
        }});

        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // Enable influence outcomes
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        final OSInAppMessageAction[] lastAction = new OSInAppMessageAction[1];
        OneSignal.setInAppMessageClickHandler(new OneSignal.InAppMessageClickHandler() {
            @Override
            public void inAppMessageClicked(OSInAppMessageAction result) {
                lastAction[0] = result;
                // Ensure we are on the main thread when running the callback, since the app developer
                //   will most likely need to update UI.
                assertMainThread();

                OneSignal.sendOutcome("test");
                try {
                    // Ensure outcome is sent
                    assertMeasureOnV2AtIndex(4, "test", new JSONArray().put(message.messageId), new JSONArray(), null, null);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        threadAndTaskWait();

        // Add trigger to make IAM display
        OneSignal.addTrigger("test_1", 2);
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());

        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message,
                new JSONObject() {{
                    put("id", "button_id_123");
                    put("name", "my_click_name");
                }}
        );

        // Ensure we fire public callback that In-App was clicked.
        assertEquals(lastAction[0].clickName, "my_click_name");
    }

    @Test
    public void testOnIAMActionSendsOutcome_afterDismiss_usingOutcomesV2() throws Exception {
        // Enable IAM v2
        preferences = new MockOSSharedPreferences();
        preferences.saveBool(preferences.getPreferencesName(), preferences.getOutcomesV2KeyName(), true);
        trackerFactory = new OSTrackerFactory(preferences, new MockOSLog());
        sessionManager = new MockSessionManager(OneSignal_getSessionListener(), trackerFactory, new MockOSLog());

        OneSignal_setSharedPreferences(preferences);
        OneSignal_setTrackerFactory(trackerFactory);
        OneSignal_setSessionManager(sessionManager);

        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.CUSTOM, "test_1", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 2);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message);
        }});

        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // Enable influence outcomes
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        final OSInAppMessageAction[] lastAction = new OSInAppMessageAction[1];
        OneSignal.setInAppMessageClickHandler(new OneSignal.InAppMessageClickHandler() {
            @Override
            public void inAppMessageClicked(OSInAppMessageAction result) {
                lastAction[0] = result;
                // Ensure we are on the main thread when running the callback, since the app developer
                //   will most likely need to update UI.
                assertMainThread();
            }
        });
        threadAndTaskWait();

        // Add trigger to make IAM display
        OneSignal.addTrigger("test_1", 2);
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());

        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message,
                new JSONObject() {{
                    put("id", "button_id_123");
                    put("name", "my_click_name");
                }}
        );

        // Ensure we fire public callback that In-App was clicked.
        assertEquals(lastAction[0].clickName, "my_click_name");

        OneSignalPackagePrivateHelper.dismissCurrentMessage();

        OneSignal.sendOutcome("test1");
        try {
            // Ensure outcome is sent but with INDIRECT influence from IAM
            assertMeasureOnV2AtIndex(5, "test1", null, null, new JSONArray().put(message.messageId), new JSONArray());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testInAppMessageClickActionMultipleOutcomes() throws Exception {
        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // Enable influence outcomes
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        // 2. Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.SESSION_TIME,
                null,
                OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(),
                null
        );

        final JSONArray outcomes = new JSONArray();
        outcomes.put(new JSONObject() {{
            put("name", IAM_OUTCOME_NAME);
            put("weight", IAM_OUTCOME_WEIGHT);
        }});
        outcomes.put(new JSONObject() {{
            put("name", IAM_OUTCOME_NAME);
        }});
        JSONObject action = new JSONObject() {{
            put("id", IAM_CLICK_ID);
            put("outcomes", outcomes);
        }};

        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);

        // 3. Ensure outcome is sent
        ShadowOneSignalRestClient.Request iamOutcomeRequest = ShadowOneSignalRestClient.requests.get(3);
        ShadowOneSignalRestClient.Request secondIamOutcomeRequest = ShadowOneSignalRestClient.requests.get(4);

        assertEquals("outcomes/measure", iamOutcomeRequest.url);
        assertEquals("outcomes/measure", secondIamOutcomeRequest.url);
        // Requests: Param request + Players Request + Click request + Outcome Request x 2
        assertEquals(5, ShadowOneSignalRestClient.requests.size());

        assertEquals(IAM_OUTCOME_WEIGHT, iamOutcomeRequest.payload.get("weight"));
        assertEquals(IAM_OUTCOME_NAME, iamOutcomeRequest.payload.get("id"));
        assertEquals(1, iamOutcomeRequest.payload.get("device_type"));

        assertFalse(secondIamOutcomeRequest.payload.has("weight"));
        assertEquals(IAM_OUTCOME_NAME, secondIamOutcomeRequest.payload.get("id"));
        assertEquals(1, secondIamOutcomeRequest.payload.get("device_type"));
    }

    @Test
    public void testInAppMessageClickActionDisabledOutcomes() throws Exception {
        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // Disable influence outcomes
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams(false, false, false));

        // 2. Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.SESSION_TIME,
                null,
                OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(),
                null
        );

        final JSONArray outcomes = new JSONArray();
        outcomes.put(new JSONObject() {{
            put("name", IAM_OUTCOME_NAME);
        }});
        JSONObject action = new JSONObject() {{
            put("id", IAM_CLICK_ID);
            put("outcomes", outcomes);
        }};

        // 3. Send IAM action
        // With unattributed outcomes disable no outcome request should happen
        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);
        // Requests: Param request + Players Request + Click request
        assertEquals(3, ShadowOneSignalRestClient.requests.size());
        assertEquals("in_app_messages/" + message.messageId + "/click", ShadowOneSignalRestClient.requests.get(2).url);
    }

    @Test
    public void testInAppMessageClickActionUniqueOutcome() throws Exception {
        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // Enable influence outcomes
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        // 2. Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.SESSION_TIME,
                null,
                OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(),
                null
        );

        final JSONArray outcomes = new JSONArray();
        outcomes.put(new JSONObject() {{
            put("name", IAM_OUTCOME_NAME);
            put("unique", true);
        }});
        JSONObject action = new JSONObject() {{
            put("id", IAM_CLICK_ID);
            put("outcomes", outcomes);
        }};

        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);
        threadAndTaskWait();

        // 3. Ensure outcome is sent
        ShadowOneSignalRestClient.Request iamOutcomeRequest = ShadowOneSignalRestClient.requests.get(3);

        assertEquals("outcomes/measure", iamOutcomeRequest.url);
        // Requests: Param request + Players Request + Click request + Outcome Request
        assertEquals(4, ShadowOneSignalRestClient.requests.size());
        assertEquals(IAM_OUTCOME_NAME, iamOutcomeRequest.payload.get("id"));
        assertEquals(1, iamOutcomeRequest.payload.get("device_type"));

        // 4. Call IAM clicked again, ensure no 2nd outcome call is made.
        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);
        // 5. Check no additional request was made
        assertEquals(4, ShadowOneSignalRestClient.requests.size());
    }

    @Test
    public void testInAppMessageClickActionSendTag() throws Exception {
        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // 2. Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.SESSION_TIME,
                null,
                OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(),
                null
        );

        final JSONObject addTags = new JSONObject() {{
            put(IAM_TAG_KEY, IAM_TAG_KEY);
        }};
        JSONObject action = new JSONObject() {{
            put("id", IAM_CLICK_ID);
            put("tags", new JSONObject() {{
                put("adds", addTags);
            }});
        }};

        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);
        threadAndTaskWait();
        // 3. Ensure players call is made
        ShadowOneSignalRestClient.Request iamSendTagRequest = ShadowOneSignalRestClient.requests.get(3);

        assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511", iamSendTagRequest.url);
        // Requests: Param request + Players Request + Click request + Tag Request
        assertEquals(4, ShadowOneSignalRestClient.requests.size());
        JsonAsserts.equals(addTags, (JSONObject) iamSendTagRequest.payload.get("tags"));
    }

    @Test
    public void testInAppMessageClickActionRemoveTag() throws Exception {
        // 1. Init OneSignal
        OneSignalInit();
        OneSignal.sendTags(new JSONObject("{" + IAM_TAG_KEY + ": \"value1\"}"));
        threadAndTaskWait();

        // 2. Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.SESSION_TIME,
                null,
                OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(),
                null
        );

        final JSONArray removeTags = new JSONArray();
        removeTags.put(IAM_TAG_KEY);
        JSONObject actionRemove = new JSONObject() {{
            put("id", IAM_CLICK_ID);
            put("tags", new JSONObject() {{
                put("removes", removeTags);
            }});
        }};

        JSONObject objectExpected = new JSONObject() {{
            put(IAM_TAG_KEY, "");
        }};

        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, actionRemove);
        threadAndTaskWait();
        // 3. Ensure players call is made
        ShadowOneSignalRestClient.Request iamSendTagRequest = ShadowOneSignalRestClient.requests.get(3);

        assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511", iamSendTagRequest.url);
        // Requests: Param request + Players Request + Click request + Tag Request
        assertEquals(4, ShadowOneSignalRestClient.requests.size());
        JsonAsserts.equals(objectExpected, (JSONObject) iamSendTagRequest.payload.get("tags"));
    }

    @Test
    public void testInAppMessageClickActionSendAndRemoveTag() throws Exception {
        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // 2. Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.SESSION_TIME,
                null,
                OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(),
                null
        );

        final JSONObject addTags = new JSONObject() {{
            put(IAM_TAG_KEY, IAM_TAG_KEY);
        }};
        JSONObject action = new JSONObject() {{
            put("id", IAM_CLICK_ID);
            put("tags", new JSONObject() {{
                put("adds", addTags);
            }});
        }};

        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);
        threadAndTaskWait();
        // 3. Ensure players call is made
        ShadowOneSignalRestClient.Request iamSendTagRequest = ShadowOneSignalRestClient.requests.get(3);

        assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511", iamSendTagRequest.url);
        // Requests: Param request + Players Request + Click request + Tag Request
        assertEquals(4, ShadowOneSignalRestClient.requests.size());
        JsonAsserts.equals(addTags, (JSONObject) iamSendTagRequest.payload.get("tags"));

        final JSONArray removeTags = new JSONArray();
        removeTags.put(IAM_TAG_KEY);
        final JSONObject[] lastGetTags = new JSONObject[1];
        JSONObject actionRemove = new JSONObject() {{
            put("id", IAM_CLICK_ID);
            put("tags", new JSONObject() {{
                put("removes", removeTags);
            }});
        }};

        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, actionRemove);
        threadAndTaskWait();
        OneSignal.getTags(new OneSignal.GetTagsHandler() {
            @Override
            public void tagsAvailable(JSONObject tags) {
                lastGetTags[0] = tags;
            }
        });
        threadAndTaskWait();
        // 3. Ensure no tags
        assertEquals(1, lastGetTags.length);
        assertEquals(0, lastGetTags[0].length());
    }

    @Test
    public void testInAppMessageOnlyReceivesOneClick_onColdRestart() throws Exception {
        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // 2. Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.SESSION_TIME,
                null,
                OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(),
                null);

        // 2. Count IAM as clicked
        JSONObject action = new JSONObject() {{
            put("id", IAM_CLICK_ID);
        }};
        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);

        // 3. Cold restart app and re-init OneSignal
        fastColdRestartApp();
        OneSignalInit();
        threadAndTaskWait();

        // 4. Click on IAM again
        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);

        // Since the app restart and another message shown callback only 1 more request should exist
        //  So verify 4 requests exist (3 old and 1 new)
        ShadowOneSignalRestClient.Request mostRecentRequest = ShadowOneSignalRestClient.requests.get(3);
        assertEquals(4, ShadowOneSignalRestClient.requests.size());

        // Now verify the most recent request was not a click request
        boolean isIamClickUrl = mostRecentRequest.url.equals("in_app_messages/" + message.messageId + "/click");
        assertFalse(isIamClickUrl);
    }

    @Test
    public void testInAppMessageOnlyReceivesOneImpression() throws Exception {
        // Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.SESSION_TIME,
                null,
                OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(),
                null);

        // Call message shown callback and verify only 3 requests exist (3rd being the iam impression request)
        OneSignalPackagePrivateHelper.onMessageWasShown(message);

        ShadowOneSignalRestClient.Request iamImpressionRequest = ShadowOneSignalRestClient.requests.get(2);
        assertEquals("in_app_messages/" + message.messageId + "/impression", iamImpressionRequest.url);
        assertEquals(3, ShadowOneSignalRestClient.requests.size());

        // Call message shown again and make sure no other requests were made, so the impression tracking exists locally
        OneSignalPackagePrivateHelper.onMessageWasShown(message);
        assertEquals(3, ShadowOneSignalRestClient.requests.size());

        // Verify impressioned messageId was persisted locally
        Set<String> testImpressionedMessages = TestOneSignalPrefs.getStringSet(
                TestOneSignalPrefs.PREFS_ONESIGNAL,
                TestOneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
                null
        );
        assertEquals(1, testImpressionedMessages.size());
    }

    @Test
    public void testInAppMessageOnlyReceivesOneImpression_onColdRestart() throws Exception {
        // Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(
                OSTriggerKind.SESSION_TIME,
                null,
                OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(),
                null);

        // Trigger the impression request and caching of the impressioned messageId
        OneSignalPackagePrivateHelper.onMessageWasShown(message);

        // Cold restart app and re-init OneSignal
        fastColdRestartApp();
        OneSignalInit();
        threadAndTaskWait();

        OneSignalPackagePrivateHelper.onMessageWasShown(message);

        // Since the app restart and another message shown callback only 1 more request should exist
        //  So verify 4 requests exist (3 old and 1 new)
        ShadowOneSignalRestClient.Request mostRecentRequest = ShadowOneSignalRestClient.requests.get(3);
        assertEquals(4, ShadowOneSignalRestClient.requests.size());

        // Now verify the most recent request was not a impression request
        boolean isImpressionUrl = mostRecentRequest.url.equals("in_app_messages/" + message.messageId + "/impression");
        assertFalse(isImpressionUrl);
    }

    @Test
    public void testInAppMessageDisplayMultipleTimes() throws Exception {
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTriggerAndRedisplay(
                OSTriggerKind.CUSTOM, "test_1", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 2, LIMIT, DELAY);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message);
        }});

        // Init OneSignal IAM with redisplay
        OneSignalInit();
        threadAndTaskWait();

        // Add trigger to make IAM display
        OneSignal.addTrigger("test_1", 2);
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());

        // Check impression request
        int requestSize = ShadowOneSignalRestClient.requests.size();
        ShadowOneSignalRestClient.Request iamImpressionRequest = ShadowOneSignalRestClient.requests.get(requestSize - 1);
        assertEquals("in_app_messages/" + message.messageId + "/impression", iamImpressionRequest.url);

        // Dismiss IAM will make display quantity increase and last display time to change
        OneSignalPackagePrivateHelper.dismissCurrentMessage();
        // Check IAMs was removed from queue
        assertEquals(0, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        // Check if data after dismiss is set correctly
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().size());
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getDisplayQuantity());
        long lastDisplayTime =  OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getLastDisplayTime();
        assertTrue(lastDisplayTime > 0);

        // Change time for delay to be covered
        advanceSystemTimeBy(DELAY);
        // Set same trigger, should display again
        OneSignal.addTrigger("test_1", 2);
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());

        // Check impression request is sent again
        int requestSizeAfterRedisplay = ShadowOneSignalRestClient.requests.size();
        ShadowOneSignalRestClient.Request iamImpressionRequestAfterRedisplay = ShadowOneSignalRestClient.requests.get(requestSizeAfterRedisplay - 1);
        assertEquals("in_app_messages/" + message.messageId + "/impression", iamImpressionRequestAfterRedisplay.url);

        OneSignalPackagePrivateHelper.dismissCurrentMessage();
        // Check IAMs was removed from queue
        assertEquals(0, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        // Check if data after dismiss is set correctly
        assertEquals(1,  OneSignalPackagePrivateHelper.getRedisplayInAppMessages().size());
        assertEquals(2,  OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getDisplayQuantity());
        assertTrue( OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getLastDisplayTime() - lastDisplayTime >= DELAY);
    }

    @Test
    public void testInAppMessageDisplayMultipleTimes_NoTriggers() throws Exception {
        final long currentTimeInSeconds = System.currentTimeMillis() / 1000;

        // Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWitRedisplay(LIMIT, DELAY);
        message.getRedisplayStats().setLastDisplayTime(currentTimeInSeconds);
        message.getRedisplayStats().setDisplayQuantity(1);
        message.setDisplayedInSession(true);
        // Save IAM on DB
        TestHelpers.saveIAM(message, dbHelper);
        // Save IAM for dismiss
        TestOneSignalPrefs.saveStringSet(
                TestOneSignalPrefs.PREFS_ONESIGNAL,
                TestOneSignalPrefs.PREFS_OS_DISMISSED_IAMS,
                new HashSet<>(Collections.singletonList(message.messageId))
        );

        // Check IAM was saved correctly
        List<OSTestInAppMessage> savedInAppMessages = TestHelpers.getAllInAppMessages(dbHelper);
        assertEquals(savedInAppMessages.size(), 1);
        assertTrue(savedInAppMessages.get(0).isDisplayedInSession());

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message);
        }});
        // Change time for delay to be covered
        advanceSystemTimeBy(DELAY);

        // Init OneSignal with IAM with redisplay
        OneSignalInit();
        threadAndTaskWait();

        // First init will start a new session, then the IAM should be shown
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        // Dismiss IAM will make display quantity increase and last display time to change
        OneSignalPackagePrivateHelper.dismissCurrentMessage();
        // Check IAM was removed from queue
        assertEquals(0, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        // Restart OneSignal
        fastColdRestartApp();
        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message);
        }});
        OneSignalInit();
        threadAndTaskWait();

        // Change time for delay to be covered
        advanceSystemTimeBy(DELAY * 2);
        // Add trigger to call evaluateInAppMessage
        OneSignal.addTrigger("test_1", 2);
        // IAM shouldn't display again because It don't have triggers
        assertEquals(0, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
    }

    @Test
    public void testInAppMessageDisplayMultipleTimes_RemoveTrigger() throws Exception {
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTriggerAndRedisplay(
                OSTriggerKind.CUSTOM, "test_1", OSTestTrigger.OSTriggerOperator.NOT_EXISTS.toString(), 2, LIMIT, DELAY);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message);
        }});
        // Init OneSignal with IAM with redisplay
        OneSignalInit();
        threadAndTaskWait();

        // Because trigger doesn't exist IAM will be shown immediately
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        // Dismiss IAM will make display quantity increase and last display time to change
        OneSignalPackagePrivateHelper.dismissCurrentMessage();
        // Check IAM was removed from queue
        assertEquals(0, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        // Check if data after dismiss is set correctly
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().size());
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getDisplayQuantity());
        long lastDisplayTime = OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getLastDisplayTime();
        assertTrue(lastDisplayTime > 0);

        OneSignal.addTrigger("test_1", 2);
        // Wait for the delay between redisplay
        advanceSystemTimeBy(DELAY);
        assertEquals(0, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());

        // Remove trigger, IAM should display again
        OneSignal.removeTriggerForKey("test_1");
        // Check that IAM is queue for redisplay
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
    }

    @Test
    public void testInAppMessageNoDisplayMultipleTimes_Delay() throws Exception {
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTriggerAndRedisplay(
                OSTriggerKind.CUSTOM, "test_1", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 2, LIMIT, DELAY);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message);
        }});

        // Init OneSignal with IAM with redisplay
        OneSignalInit();
        threadAndTaskWait();

        // Add trigger to make IAM display
        OneSignal.addTrigger("test_1", 2);
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
        // Dismiss IAM will make display quantity increase and last display time to change
        OneSignalPackagePrivateHelper.dismissCurrentMessage();

        // Check if data after dismiss is set correctly
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().size());
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getDisplayQuantity());
        long lastDisplayTime = OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getLastDisplayTime();
        assertTrue(lastDisplayTime > 0);

        // Set trigger, will evaluate IAMs again
        OneSignal.addTrigger("test_1", 2);

        // Check that the IAM was not displayed again because time between display is not covered
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().size());
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getDisplayQuantity());
        assertEquals(lastDisplayTime, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getLastDisplayTime());
    }

    @Test
    public void testInAppMessageNoDisplayMultipleTimes_Limit() throws Exception {
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTriggerAndRedisplay(
                OSTriggerKind.CUSTOM, "test_1", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 2, 1, DELAY);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message);
        }});

        // Init OneSignal with IAM with redisplay
        OneSignalInit();
        threadAndTaskWait();

        // Add trigger to make IAM display
        OneSignal.addTrigger("test_1", 2);
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());

        // Dismiss IAM will make display quantity increase and last display time to change
        OneSignalPackagePrivateHelper.dismissCurrentMessage();

        // Check if data after dismiss is set correctly
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().size());
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getDisplayQuantity());
        long lastDisplayTime = OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getLastDisplayTime();
        assertTrue(lastDisplayTime > 0);

        // Wait for the delay between redisplay
        advanceSystemTimeBy(DELAY);

        // Set trigger, will evaluate IAMs again
        OneSignal.addTrigger("test_1", 2);

        // Check that the IAM was not displayed again because Limit of display is 1
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().size());
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getDisplayQuantity());
        assertEquals(lastDisplayTime, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getLastDisplayTime());
    }

    @Test
    public void testInAppMessageDisplayMultipleTimes_onColdRestart() throws Exception {
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTriggerAndRedisplay(
                OSTriggerKind.CUSTOM, "test_1", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 2, LIMIT, DELAY);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message);
        }});

        // Init OneSignal with IAM with redisplay
        OneSignalInit();
        threadAndTaskWait();

        // Add trigger to make IAM display
        OneSignal.addTrigger("test_1", 2);
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());

        // Check impression request
        int requestSize = ShadowOneSignalRestClient.requests.size();
        ShadowOneSignalRestClient.Request iamImpressionRequest = ShadowOneSignalRestClient.requests.get(requestSize - 1);
        assertEquals("in_app_messages/" + message.messageId + "/impression", iamImpressionRequest.url);

        // Dismiss IAM will make display quantity increase and last display time to change
        OneSignalPackagePrivateHelper.dismissCurrentMessage();
        // Check IAM removed from queue
        assertEquals(0, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());

        // Check if data after dismiss is set correctly
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().size());
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getDisplayQuantity());
        long lastDisplayTime = OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getLastDisplayTime();
        assertTrue(lastDisplayTime > 0);

        // Wait for the delay between redisplay
        advanceSystemTimeBy(DELAY);
        // Swipe away app
        fastColdRestartApp();
        // Cold Start app
        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message);
        }});

        OneSignalInit();
        threadAndTaskWait();
        // Check No IAMs
        assertEquals(0, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());

        // Set same trigger, should display again
        OneSignal.addTrigger("test_1", 2);
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());

        // Check impression request is sent again
        int requestSizeAfterRedisplay = ShadowOneSignalRestClient.requests.size();
        ShadowOneSignalRestClient.Request iamImpressionRequestAfterRedisplay = ShadowOneSignalRestClient.requests.get(requestSizeAfterRedisplay - 1);
        assertEquals("in_app_messages/" + message.messageId + "/impression", iamImpressionRequestAfterRedisplay.url);

        OneSignalPackagePrivateHelper.dismissCurrentMessage();
        // Check if data after dismiss is set correctly
        assertEquals(1, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().size());
        assertEquals(2, OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getDisplayQuantity());
        assertTrue(OneSignalPackagePrivateHelper.getRedisplayInAppMessages().get(0).getRedisplayStats().getLastDisplayTime() - lastDisplayTime >= DELAY);
    }

    @Test
    public void testInAppMessageMultipleRedisplayReceivesClickId() throws Exception {
        // Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // Create an IAM
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTriggerAndRedisplay(
                OSTriggerKind.CUSTOM, "test_1", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 2, LIMIT, DELAY);

        assertTrue(message.getClickedClickIds().isEmpty());
        // Count IAM as clicked
        JSONObject action = new JSONObject() {{
            put("id", IAM_CLICK_ID);
        }};
        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);

        // Ensure click is sent
        ShadowOneSignalRestClient.Request firstIAMClickRequest = ShadowOneSignalRestClient.requests.get(2);
        assertEquals("in_app_messages/" + message.messageId + "/click", firstIAMClickRequest.url);
        assertEquals(3, ShadowOneSignalRestClient.requests.size());

        // Call IAM clicked again, ensure a 2nd network call isn't made.
        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);
        assertEquals(3, ShadowOneSignalRestClient.requests.size());

        // Verify clickId was persisted locally
        Set<String> testClickedMessages = TestOneSignalPrefs.getStringSet(
                TestOneSignalPrefs.PREFS_ONESIGNAL,
                TestOneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
                null
        );
        assertEquals(1, testClickedMessages.size());
        // Verify click id is associated with message
        assertEquals(1, message.getClickedClickIds().size());
        assertTrue(message.getClickedClickIds().contains(IAM_CLICK_ID));

        message.clearClickIds();
        assertTrue(message.getClickedClickIds().isEmpty());

        // Click should be received twice
        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);

        // Call IAM clicked again, ensure a 2nd network call is made.
        ShadowOneSignalRestClient.Request secondIAMClickRequest = ShadowOneSignalRestClient.requests.get(3);
        assertEquals("in_app_messages/" + message.messageId + "/click", secondIAMClickRequest.url);
        assertEquals(4, ShadowOneSignalRestClient.requests.size());

        // Verify clickId was persisted locally
        Set<String> secondRestClickedMessages = TestOneSignalPrefs.getStringSet(
                TestOneSignalPrefs.PREFS_ONESIGNAL,
                TestOneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
                null
        );
        assertEquals(1, secondRestClickedMessages.size());

        // Verify click id is associated with message
        assertEquals(1, message.getClickedClickIds().size());
        assertTrue(message.getClickedClickIds().contains(IAM_CLICK_ID));

        // Call IAM clicked again
        OneSignalPackagePrivateHelper.onMessageActionOccurredOnMessage(message, action);

        // Call IAM clicked again, ensure a 3nd network call isn't made.
        assertEquals("in_app_messages/" + message.messageId + "/click", secondIAMClickRequest.url);
        assertEquals(4, ShadowOneSignalRestClient.requests.size());
    }

    @Test
    public void testCachedIAMSharedPreferenceAndSQL_cleanedAfterSixMonths() throws Exception {
        final long currentTimeInSeconds = System.currentTimeMillis() / 1_000L;

        // 1. Setup IAMs
        // Create an IAM younger than 6 months
        final OSTestInAppMessage iam1 = InAppMessagingHelpers.buildTestMessage(null);
        iam1.setRedisplayStats(1, currentTimeInSeconds - SIX_MONTHS_TIME_SECONDS + 10);
        String clickId1 = "iam1_click_id_1";
        iam1.addClickId(clickId1);
        TestHelpers.saveIAM(iam1, dbHelper);

        // Create an IAM older than 6 months
        final OSTestInAppMessage iam2 = InAppMessagingHelpers.buildTestMessage(null);
        iam2.setRedisplayStats(1, currentTimeInSeconds - SIX_MONTHS_TIME_SECONDS - 10);
        String clickId2 = "iam2_click_id_1";
        iam2.addClickId(clickId2);
        TestHelpers.saveIAM(iam2, dbHelper);

        // 2. Cache IAMs as dismissed, impressioned, and clicked
        Set<String> messageIds = new HashSet<String>() {{
            add(iam1.messageId);
            add(iam2.messageId);
        }};
        TestOneSignalPrefs.saveStringSet(
                TestOneSignalPrefs.PREFS_ONESIGNAL,
                TestOneSignalPrefs.PREFS_OS_DISMISSED_IAMS,
                messageIds);

        TestOneSignalPrefs.saveStringSet(
                TestOneSignalPrefs.PREFS_ONESIGNAL,
                TestOneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
                messageIds);

        Set<String> clickedClickIds = new HashSet<String>() {{
            addAll(iam1.getClickedClickIds());
            addAll(iam2.getClickedClickIds());
        }};
        TestOneSignalPrefs.saveStringSet(
                TestOneSignalPrefs.PREFS_ONESIGNAL,
                TestOneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
                clickedClickIds);

        // 3. Init OneSignal so it attempts to clean IAM cache
        OneSignalInit();
        threadAndTaskWait();

        // 4. Validate all data associated with the 6 month old IAM has been deleted
        Set<String> testDismissedMessages = TestOneSignalPrefs.getStringSet(
                TestOneSignalPrefs.PREFS_ONESIGNAL,
                TestOneSignalPrefs.PREFS_OS_DISMISSED_IAMS,
                null);
        assertEquals(1, testDismissedMessages.size());
        assertTrue(testDismissedMessages.contains(iam1.messageId));

        Set<String> testImpressionedMessages = TestOneSignalPrefs.getStringSet(
                TestOneSignalPrefs.PREFS_ONESIGNAL,
                TestOneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
                null);
        assertEquals(1, testImpressionedMessages.size());
        assertTrue(testImpressionedMessages.contains(iam1.messageId));

        Set<String> testClickedClickIds = TestOneSignalPrefs.getStringSet(
                TestOneSignalPrefs.PREFS_ONESIGNAL,
                TestOneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
                null);
        assertEquals(1, testClickedClickIds.size());
        assertTrue(testClickedClickIds.contains(clickId1));

        // 5. Make sure only IAM left is the IAM younger than 6 months
        List<OSTestInAppMessage> savedInAppMessagesAfterInit = TestHelpers.getAllInAppMessages(dbHelper);
        assertEquals(1, savedInAppMessagesAfterInit.size());
        assertEquals(iam1.messageId, savedInAppMessagesAfterInit.get(0).messageId);
    }

    @Test
    public void testInAppMessageRedisplayCacheCleaning() throws Exception {
        final long currentTimeInSeconds = System.currentTimeMillis() / 1000;

        final OSTestInAppMessage inAppMessage = InAppMessagingHelpers.buildTestMessageWithSingleTriggerAndRedisplay(
                OSTriggerKind.CUSTOM, "test_saved", OneSignalPackagePrivateHelper.OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 2, LIMIT, DELAY);

        String firstID = inAppMessage.messageId + "_test";
        inAppMessage.messageId = firstID;
        inAppMessage.getRedisplayStats().setLastDisplayTime(currentTimeInSeconds - SIX_MONTHS_TIME_SECONDS + 1);
        TestHelpers.saveIAM(inAppMessage, dbHelper);

        inAppMessage.getRedisplayStats().setLastDisplayTime(currentTimeInSeconds - SIX_MONTHS_TIME_SECONDS - 1);
        inAppMessage.messageId += "1";
        TestHelpers.saveIAM(inAppMessage, dbHelper);

        List<OSTestInAppMessage> savedInAppMessages = TestHelpers.getAllInAppMessages(dbHelper);

        assertEquals(2, savedInAppMessages.size());

        final OSTestInAppMessage message1 = InAppMessagingHelpers.buildTestMessageWithSingleTriggerAndRedisplay(
                OSTriggerKind.CUSTOM, "test_1", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 2, LIMIT, DELAY);
        final OSTestInAppMessage message2 = InAppMessagingHelpers.buildTestMessageWithSingleTriggerAndRedisplay(
                OSTriggerKind.CUSTOM, "test_2", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 2, LIMIT, DELAY);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message1);
            add(message2);
        }});

        // Init OneSignal with IAM with redisplay
        OneSignalInit();
        threadAndTaskWait();

        List<OSTestInAppMessage> savedInAppMessagesAfterInit = TestHelpers.getAllInAppMessages(dbHelper);
        // Message with old display time should be removed
        assertEquals(1, savedInAppMessagesAfterInit.size());
        assertEquals(firstID, savedInAppMessagesAfterInit.get(0).messageId);
    }

    @Test
    @Config(sdk = 18)
    public void testMessageNotShownForAndroidApi18Lower() throws Exception {
        initializeSdkWithMultiplePendingMessages();

        // Send a new IAM
        OneSignal.addTriggers(new HashMap<String, Object>() {{
            put("test_1", 3);
        }});
        threadAndTaskWait();

        // Check no messages exist
        assertEquals(0, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());
    }

    @Test
    public void testInAppMessageIdTracked() throws Exception {
        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTriggerAndRedisplay(
                OSTriggerKind.CUSTOM, "test_1", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 2, LIMIT, DELAY);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(message);
        }});

        // For mocking behaviour
        OneSignal_setTrackerFactory(trackerFactory);
        OneSignal_setSessionManager(sessionManager);
        // Init OneSignal IAM with redisplay
        OneSignalInit();
        threadAndTaskWait();

        // Check no influence id saved
        JSONArray lastReceivedIds = trackerFactory.getIAMChannelTracker().getLastReceivedIds();
        assertEquals(0, lastReceivedIds.length());

        // Add trigger to make IAM display
        OneSignal.addTrigger("test_1", 2);
        assertEquals(1, OneSignalPackagePrivateHelper.getInAppMessageDisplayQueue().size());

        // Check influence id saved
        lastReceivedIds = trackerFactory.getIAMChannelTracker().getLastReceivedIds();
        assertEquals(1, lastReceivedIds.length());
    }

    private void setMockRegistrationResponseWithMessages(ArrayList<OSTestInAppMessage> messages) throws JSONException {
        final JSONArray jsonMessages = new JSONArray();

        for (OSTestInAppMessage message : messages)
            jsonMessages.put(message.toJSONObject());

        ShadowOneSignalRestClient.setNextSuccessfulRegistrationResponse(new JSONObject() {{
            put("id", "df8f05be55ba-b2f7f966-d8cc-11e4-bed1");
            put("success", 1);
            put(OneSignalPackagePrivateHelper.IN_APP_MESSAGES_JSON_KEY, jsonMessages);
        }});
    }

    private void OneSignalInit() {
        OneSignal_setTrackerFactory(trackerFactory);
        OneSignal_setSessionManager(sessionManager);
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.NONE);
        ShadowOSUtils.subscribableStatus = 1;
        OneSignal.setAppId(ONESIGNAL_APP_ID);
        OneSignal.setAppContext(blankActivity);
        blankActivityController.resume();
    }
}
