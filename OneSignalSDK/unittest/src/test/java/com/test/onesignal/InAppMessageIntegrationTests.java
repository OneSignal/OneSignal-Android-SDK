package com.test.onesignal;

import android.annotation.SuppressLint;
import android.app.Activity;

import com.onesignal.BuildConfig;
import com.onesignal.InAppMessagingHelpers;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.OneSignalPackagePrivateHelper.OSInAppMessageController;
import com.onesignal.OneSignalPackagePrivateHelper.OSTestInAppMessage;
import com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger;
import com.onesignal.ShadowAdvertisingIdProviderGPS;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowDynamicTimer;
import com.onesignal.ShadowJobService;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSInAppMessageController;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOSViewUtils;
import com.onesignal.ShadowOSWebView;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorGCM;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;

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
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger.OSTriggerKind;
import static com.test.onesignal.TestHelpers.fastColdRestartApp;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

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
                ShadowOSInAppMessageController.class,
                ShadowOSWebView.class,
                ShadowOSViewUtils.class
        },
        instrumentedPackages = {"com.onesignal"},
        constants = BuildConfig.class,
        sdk = 21)
@RunWith(RobolectricTestRunner.class)
public class InAppMessageIntegrationTests {
    private static final String IAM_CLICK_ID = "button_id_123";
    private static final String ONESIGNAL_APP_ID = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";

    @SuppressLint("StaticFieldLeak")
    private static Activity blankActivity;
    private static ActivityController<BlankActivity> blankActivityController;

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

        blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
        blankActivity = blankActivityController.get();

        TestHelpers.beforeTestInitAndCleanup();
    }

    @After
    public void afterEachTest() throws Exception {
        // reset back to the default
        ShadowDynamicTimer.shouldScheduleTimers = true;
        ShadowDynamicTimer.hasScheduledTimer = false;

        TestHelpers.afterTestCleanup();

        InAppMessagingHelpers.clearTestState();
    }

    @Test
    public void testDisableInAppMessagingPreventsMessageDisplay() throws Exception {
        final OSTestInAppMessage testMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(OSTriggerKind.CUSTOM,"test_key", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 3);

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

        assertEquals(0, ShadowOSInAppMessageController.displayedMessages.size());
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

        //both messages should now be valid but only one should display
        //which one displays first is undefined and doesn't really matter
        assertEquals(1, ShadowOSInAppMessageController.displayedMessages.size());

        // dismiss the message
        OneSignalPackagePrivateHelper.dismissCurrentMessage();
        threadAndTaskWait();

        // the second in app message should now be displayed
        assertEquals(2, ShadowOSInAppMessageController.displayedMessages.size());
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

        // 2. Assert one IAM was displayed
        assertEquals(1, ShadowOSInAppMessageController.displayedMessages.size());

        // 3. Rotate device - This will kick off a JS task to get the new height
        blankActivityController.pause();
        blankActivityController.resume();

        // 4. Dismiss the IAM
        OneSignalPackagePrivateHelper.WebViewManager.callDismissAndAwaitNextMessage();
        threadAndTaskWait();

        // 5. Now fire resize event which was scheduled in step 3.
        //    Test that this does not throw and handles this missing IAM view.
        ShadowOSWebView.fireEvalJSCallbacks();
    }


    private void nextResponseMultiplePendingMessages() throws JSONException {
        final OSTestInAppMessage testFirstMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(OSTriggerKind.CUSTOM,"test_1", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 3);
        final OSTestInAppMessage testSecondMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(OSTriggerKind.CUSTOM,"test_2", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 2);

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
                return ShadowOSInAppMessageController.displayedMessages.size() == 1;
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
                add(InAppMessagingHelpers.buildTrigger(OSTriggerKind.SESSION_TIME,null, OSTestTrigger.OSTriggerOperator.GREATER_THAN.toString(), 0.1));
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
                   assertEquals(1, ShadowOSInAppMessageController.displayedMessages.size());
                   assertEquals(message1.messageId, ShadowOSInAppMessageController.displayedMessages.get(0));
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
                  assertEquals(2, ShadowOSInAppMessageController.displayedMessages.size());
                  assertEquals(message2.messageId, ShadowOSInAppMessageController.displayedMessages.get(1));
              }
          });
    }

    /**
     * If an in-app message should only be shown if (A) session_duration is > 30 seconds and
     * (B) a key/value trigger is set, and it should not set up a timer until all of the non-timer
     * based triggers for that message evaluate to true
     *
     * For this test, a timer should never be scheduled because the key/value 'test_key' trigger
     * will not be set until the session duration has already exceeded the minimum (0.05 seconds)
     */
    @Test
    public void testTimedMessageDisplayedAfterAllTriggersValid() throws Exception {
        ArrayList<ArrayList<OSTestTrigger>> triggers = new ArrayList<ArrayList<OSTestTrigger>>() {{
            add(new ArrayList<OSTestTrigger>() {{
                add(InAppMessagingHelpers.buildTrigger(OSTriggerKind.CUSTOM,"test_key", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), "squirrel"));
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
        assertEquals(0, ShadowOSInAppMessageController.displayedMessages.size());

        // since we are not actually waiting on any logic to finish, sleeping here is fine
        Thread.sleep(20);

        // the message still should not be displayed
        assertEquals(0, ShadowOSInAppMessageController.displayedMessages.size());

        // after setting this trigger the message should be displayed immediately
        OneSignal.addTrigger("test_key", "squirrel");
        threadAndTaskWait();

        // the message should now have been displayed
        assertEquals(1, ShadowOSInAppMessageController.displayedMessages.size());
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
        assertEquals(1, ShadowOSInAppMessageController.displayedMessages.size());
    }

    @Test
    public void useCachedInAppListOnQuickColdRestartWhenInitFromAppClass() throws Exception {
        // 1. Start app
        nextResponseMultiplePendingMessages();
        OneSignal.init(blankActivity.getApplicationContext(), "123456789", ONESIGNAL_APP_ID);
        blankActivityController.resume();
        threadAndTaskWait();

        // 2. Swipe away app
        fastColdRestartApp();
        // 3. Cold Start app
        OneSignal.init(blankActivity.getApplicationContext(), "123456789", ONESIGNAL_APP_ID);
        blankActivityController.resume();
        threadAndTaskWait();

        // Should used cached triggers since we won't be making an on_session call.
        //   Testing for this by trying to add a trigger that should display an IAM
        OneSignal.addTrigger("test_2", 2);
        assertEquals(1, ShadowOSInAppMessageController.displayedMessages.size());
    }

    @Test
    public void doNotReshowInAppIfDismissed_evenAfterColdRestart() throws Exception {
        // 1. Start app
        initializeSdkWithMultiplePendingMessages();
        // 2. Trigger showing In App and dismiss it
        OneSignal.addTrigger("test_2", 2);
        assertEquals(1, ShadowOSInAppMessageController.displayedMessages.size());
        OneSignalPackagePrivateHelper.dismissCurrentMessage();
        // 3. Swipe away app
        fastColdRestartApp();
        // 4. Cold Start app
        initializeSdkWithMultiplePendingMessages();
        // 5. Set same trigger, should not display again
        OneSignal.addTrigger("test_2", 2);
        assertEquals(1, ShadowOSInAppMessageController.displayedMessages.size());
    }

    @Test
    public void reshowInAppIfDisplayedButNeverDismissedAfterColdRestart() throws Exception {
        // 1. Start app
        initializeSdkWithMultiplePendingMessages();
        // 2. Trigger showing In App
        OneSignal.addTrigger("test_2", 2);
        assertEquals(1, ShadowOSInAppMessageController.displayedMessages.size());
        // 3. Swipe away app
        fastColdRestartApp();
        // 4. Cold Start app
        initializeSdkWithMultiplePendingMessages();
        assertEquals(1, ShadowOSInAppMessageController.displayedMessages.size());
        // 5. Set same trigger, should now display again, since it was never dismissed
        OneSignal.addTrigger("test_2", 2);
        assertEquals(2, ShadowOSInAppMessageController.displayedMessages.size());
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
        JSONObject action = new JSONObject() {{ put("id", IAM_CLICK_ID); }};
        OSInAppMessageController.getController().onMessageActionOccurredOnMessage(message, action);

        // 3. Ensure click is sent
        ShadowOneSignalRestClient.Request iamImpressionRequest = ShadowOneSignalRestClient.requests.get(2);
        assertEquals("in_app_messages/" + message.messageId + "/click", iamImpressionRequest.url);
        assertEquals(3, ShadowOneSignalRestClient.requests.size());

        // 4. Call IAM clicked again, ensure a 2nd network call is not made.
        OSInAppMessageController.getController().onMessageActionOccurredOnMessage(message, action);
        assertEquals(3, ShadowOneSignalRestClient.requests.size());

        // Verify clickId was persisted locally
        Set<String> testClickedMessages = OneSignalPackagePrivateHelper.OneSignalPrefs.getStringSet(
           OneSignalPackagePrivateHelper.OneSignalPrefs.PREFS_ONESIGNAL,
           OneSignalPackagePrivateHelper.OneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
           null
        );
        assertEquals(1, testClickedMessages.size());
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
        JSONObject action = new JSONObject() {{ put("id", IAM_CLICK_ID); }};
        OSInAppMessageController.getController().onMessageActionOccurredOnMessage(message, action);

        // 3. Cold restart app and re-init OneSignal
        fastColdRestartApp();
        OneSignalInit();
        threadAndTaskWait();

        // 4. Click on IAM again
        OSInAppMessageController.getController().onMessageActionOccurredOnMessage(message, action);

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
        OSInAppMessageController.getController().onMessageWasShown(message);

        ShadowOneSignalRestClient.Request iamImpressionRequest = ShadowOneSignalRestClient.requests.get(2);
        assertEquals("in_app_messages/" + message.messageId + "/impression", iamImpressionRequest.url);
        assertEquals(3, ShadowOneSignalRestClient.requests.size());

        // Call message shown again and make sure no other requests were made, so the impression tracking exists locally
        OSInAppMessageController.getController().onMessageWasShown(message);
        assertEquals(3, ShadowOneSignalRestClient.requests.size());

        // Verify impressioned messageId was persisted locally
        Set<String> testImpressionedMessages = OneSignalPackagePrivateHelper.OneSignalPrefs.getStringSet(
                OneSignalPackagePrivateHelper.OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPackagePrivateHelper.OneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
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
        OSInAppMessageController.getController().onMessageWasShown(message);

        // Cold restart app and re-init OneSignal
        fastColdRestartApp();
        OneSignalInit();
        threadAndTaskWait();

        OSInAppMessageController.getController().onMessageWasShown(message);

        // Since the app restart and another message shown callback only 1 more request should exist
        //  So verify 4 requests exist (3 old and 1 new)
        ShadowOneSignalRestClient.Request mostRecentRequest = ShadowOneSignalRestClient.requests.get(3);
        assertEquals(4, ShadowOneSignalRestClient.requests.size());

        // Now verify the most recent request was not a impression request
        boolean isImpressionUrl = mostRecentRequest.url.equals("in_app_messages/" + message.messageId + "/impression");
        assertFalse(isImpressionUrl);
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
        assertEquals(0, ShadowOSInAppMessageController.displayedMessages.size());
    }

    private void setMockRegistrationResponseWithMessages(ArrayList<OSTestInAppMessage> messages) throws JSONException {
        final JSONArray jsonMessages = new JSONArray();

        for (OSTestInAppMessage message : messages)
            jsonMessages.put(message.toJSONObject());

        ShadowOneSignalRestClient.setNextSuccessfulRegistrationResponse(new JSONObject() {{
            put("id", "df8f05be55ba-b2f7f966-d8cc-11e4-bed1");
            put("success", 1);
            put(OSInAppMessageController.IN_APP_MESSAGES_JSON_KEY, jsonMessages);
        }});
    }

    private void OneSignalInit() {
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.NONE);
        ShadowOSUtils.subscribableStatus = 1;
        OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID);
        blankActivityController.resume();
    }
}
