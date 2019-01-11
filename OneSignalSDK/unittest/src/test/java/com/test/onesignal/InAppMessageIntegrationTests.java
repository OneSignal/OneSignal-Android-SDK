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
import org.robolectric.shadows.ShadowLog;
import org.robolectric.android.controller.ActivityController;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.awaitility.Duration;

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
        blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
        blankActivity = blankActivityController.get();

        TestHelpers.beforeTestInitAndCleanup();
    }

    @After
    public void afterEachTest() {
        // reset back to the default
        ShadowDynamicTimer.shouldScheduleTimers = true;
        ShadowDynamicTimer.hasScheduledTimer = false;

        TestHelpers.afterTestCleanup();

        InAppMessagingHelpers.clearTestState();
    }

    @Test
    public void testDisableInAppMessagingPersisted() throws JSONException {
        OneSignal.startInit(blankActivity).init();

        assertTrue(OneSignal.isInAppMessagingEnabled());

        OneSignal.setInAppMessagingEnabled(false);

        // check to make sure that this setting is persisted to shared prefs
        flushBufferedSharedPrefs();
        final SharedPreferences prefs = blankActivity.getSharedPreferences(OneSignal.class.getSimpleName(), Context.MODE_PRIVATE);

        assertFalse(OneSignal.isInAppMessagingEnabled());
        assertFalse(prefs.getBoolean("ONESIGNAL_MESSAGING_ENABLED", true));
    }

    @Test
    public void testDisableInAppMessagingPreventsMessageDisplay() throws Exception {
        final OSTestInAppMessage testMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger("test_key", OSTestTrigger.OSTriggerOperatorType.EQUAL_TO.toString(), 3);

        setMockRegistrationResponseWithMessages(new ArrayList() {{
            add(testMessage);
        }});

        OneSignalInit();
        threadAndTaskWait();

        // the SDK now has the in app message but it cannot be shown yet since the trigger is not valid
        // we will now disable in-app messages
        OneSignal.setInAppMessagingEnabled(false);

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
        final OSTestInAppMessage testFirstMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger("test_1", OSTestTrigger.OSTriggerOperatorType.EQUAL_TO.toString(), 3);
        final OSTestInAppMessage testSecondMessage = InAppMessagingHelpers.buildTestMessageWithSingleTrigger("test_2", OSTestTrigger.OSTriggerOperatorType.EQUAL_TO.toString(), 2);

        setMockRegistrationResponseWithMessages(new ArrayList() {{
            add(testFirstMessage);
            add(testSecondMessage);
        }});

        OneSignalInit();
        threadAndTaskWait();
    }

    @Test
    public void testTimedMessageIsDisplayed() throws Exception {
        ShadowDynamicTimer.shouldScheduleTimers = true;

        final OSTestInAppMessage message = InAppMessagingHelpers.buildTestMessageWithSingleTrigger(InAppMessagingHelpers.DYNAMIC_TRIGGER_SESSION_DURATION, OSTestTrigger.OSTriggerOperatorType.GREATER_THAN.toString(), 0.05);

        setMockRegistrationResponseWithMessages(new ArrayList() {{
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

    private void setMockRegistrationResponseWithMessages(ArrayList<OSTestInAppMessage> messages) throws JSONException {
        final JSONArray jsonMessages = new JSONArray();

        for (OSTestInAppMessage message : messages)
            jsonMessages.put(message.toJSONObject());

        ShadowOneSignalRestClient.setNextSuccessfulRegistrationResponse(new JSONObject() {{
            put("id", "df8f05be55ba-b2f7f966-d8cc-11e4-bed1");
            put("success", 1);
            put("in_app_messages", jsonMessages);
        }});
    }

    private void OneSignalInit() {
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.NONE);
        ShadowOSUtils.subscribableStatus = 1;
        OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID);
        blankActivityController.resume();
    }
}
