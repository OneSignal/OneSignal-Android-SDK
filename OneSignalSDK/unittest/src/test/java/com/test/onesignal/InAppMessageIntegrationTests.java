package com.test.onesignal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;

import com.onesignal.BuildConfig;
import com.onesignal.InAppMessagingHelpers;
import com.onesignal.OneSignalPackagePrivateHelper.OSInAppMessageController;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalPackagePrivateHelper;
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
import com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger;

import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromGCMIntentService;
import static com.onesignal.OneSignalPackagePrivateHelper.OSTestTrigger.OSTriggerKind;

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
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.android.controller.ActivityController;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.awaitility.Duration;

import static com.test.onesignal.GenerateNotificationRunner.getBaseNotifBundle;
import static com.test.onesignal.TestHelpers.fastColdRestartApp;
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
public class InAppMessageIntegrationTests {
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

        // the second in app message should now be displayed
        assertEquals(2, ShadowOSInAppMessageController.displayedMessages.size());
    }

    // initializes the SDK with multiple mock in-app messages and sets triggers so that
    // both in-app messages become valid and can be displayed
    private void initializeSdkWithMultiplePendingMessages() throws Exception {
        final OSTestInAppMessage testFirstMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(OSTriggerKind.CUSTOM,"test_1", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 3);
        final OSTestInAppMessage testSecondMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(OSTriggerKind.CUSTOM,"test_2", OSTestTrigger.OSTriggerOperator.EQUAL_TO.toString(), 2);

        setMockRegistrationResponseWithMessages(new ArrayList<OSTestInAppMessage>() {{
            add(testFirstMessage);
            add(testSecondMessage);
        }});

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
          .atMost(new Duration(150, TimeUnit.MILLISECONDS))
          .pollInterval(new Duration(10, TimeUnit.MILLISECONDS))
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
    @Config(sdk = 18)
    public void testMessageNotShownForAndroidApi18Lower() throws Exception {
        initializeSdkWithMultiplePendingMessages();

        // Background app
        blankActivityController.pause();
        threadAndTaskWait();

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
