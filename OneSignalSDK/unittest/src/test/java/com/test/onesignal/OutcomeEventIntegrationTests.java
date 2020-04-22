package com.test.onesignal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;

import com.onesignal.MockOutcomesUtils;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalPackagePrivateHelper;
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

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.Arrays;

import static com.onesignal.OneSignalPackagePrivateHelper.GcmBroadcastReceiver_onReceived;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_getSessionDirectNotification;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_getSessionIndirectNotificationIds;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_getSessionType;
import static com.test.onesignal.GenerateNotificationRunner.getBaseNotifBundle;
import static com.test.onesignal.RestClientAsserts.assertMeasureAtIndex;
import static com.test.onesignal.RestClientAsserts.assertOnFocusAtIndex;
import static com.test.onesignal.RestClientAsserts.assertOnFocusAtIndexDoesNotHaveKeys;
import static com.test.onesignal.RestClientAsserts.assertOnFocusAtIndexForPlayerId;
import static com.test.onesignal.RestClientAsserts.assertRestCalls;
import static com.test.onesignal.TestHelpers.advanceSystemTimeBy;
import static com.test.onesignal.TestHelpers.afterTestCleanup;
import static com.test.onesignal.TestHelpers.fastColdRestartApp;
import static com.test.onesignal.TestHelpers.getAllNotificationRecords;
import static com.test.onesignal.TestHelpers.getAllUniqueOutcomeNotificationRecords;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
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
                ShadowJobService.class
        },
        instrumentedPackages = {"com.onesignal"},
        sdk = 26)
@RunWith(RobolectricTestRunner.class)
public class OutcomeEventIntegrationTests {

    private static final String ONESIGNAL_APP_ID = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";
    private static final String ONESIGNAL_NOTIFICATION_ID = "97d8e764-81c2-49b0-a644-713d052ae7d5";
    private static final String ONESIGNAL_OUTCOME_NAME = "Testing_Outcome";
    @SuppressLint("StaticFieldLeak")
    private static Activity blankActivity;
    private static ActivityController<BlankActivity> blankActivityController;
    private static String notificationOpenedMessage;

    private static OneSignal.NotificationOpenedHandler getNotificationOpenedHandler() {
        return new OneSignal.NotificationOpenedHandler() {
            @Override
            public void notificationOpened(OSNotificationOpenResult openedResult) {
                notificationOpenedMessage = openedResult.notification.payload.body;
            }
        };
    }

    @BeforeClass // Runs only once, before any tests
    public static void setUpClass() throws Exception {
        ShadowLog.stream = System.out;

        TestHelpers.beforeTestSuite();
        StaticResetHelper.saveStaticValues();

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
    }

    private static void cleanUp() throws Exception {
        notificationOpenedMessage = null;

        TestHelpers.beforeTestInitAndCleanup();
    }

    @Before
    public void beforeEachTest() throws Exception {
        blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
        blankActivity = blankActivityController.get();

        cleanUp();
    }

    @After
    public void afterEachTest() throws Exception {
        afterTestCleanup();
    }

    @AfterClass
    public static void afterEverything() throws Exception {
        cleanUp();
    }

    @Test
    public void testAppSessions_beforeOnSessionCalls() throws Exception {
        foregroundAppAfterReceivingNotification();

        // Check session INDIRECT
        assertTrue(OneSignal_getSessionType().isIndirect());

        // Background app
        blankActivityController.pause();
        threadAndTaskWait();

        // Click notification
        OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false, ONESIGNAL_NOTIFICATION_ID + "2");
        threadAndTaskWait();

        // Foreground app
        blankActivityController.resume();
        threadAndTaskWait();

        // Check session DIRECT
        assertTrue(OneSignal_getSessionType().isDirect());
    }

    @Test
    public void testAppSessions_afterOnSessionCalls() throws Exception {
        foregroundAppAfterReceivingNotification();

        // Check session INDIRECT
        assertTrue(OneSignal_getSessionType().isIndirect());

        // Background app for 30 seconds
        blankActivityController.pause();
        threadAndTaskWait();
        advanceSystemTimeBy(31);

        // Click notification
        OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false, ONESIGNAL_NOTIFICATION_ID + "2");
        threadAndTaskWait();

        // Foreground app
        blankActivityController.resume();
        threadAndTaskWait();

        // Check session DIRECT
        assertTrue(OneSignal_getSessionType().isDirect());
    }

    @Test
    public void testIndirectAttributionWindow_withNoNotifications() throws Exception {
        foregroundAppAfterReceivingNotification();

        // Check received notifications matches indirectNotificationIds
        assertEquals(new JSONArray().put(ONESIGNAL_NOTIFICATION_ID + "1"), OneSignal_getSessionIndirectNotificationIds());
        // Check session INDIRECT
        assertTrue(OneSignal_getSessionType().isIndirect());

        // Background app for attribution window time
        blankActivityController.pause();
        threadAndTaskWait();
        advanceSystemTimeBy(1_441L * 60L);

        // Foreground app
        blankActivityController.resume();
        threadAndTaskWait();

        // Check session UNATTRIBUTED
        assertTrue(OneSignal_getSessionType().isUnattributed());
    }

    @Test
    public void testUniqueOutcomeMeasureOnlySentOncePerClickedNotification_whenSendingMultipleUniqueOutcomes_inDirectSession() throws Exception {
        foregroundAppAfterClickingNotification();

        // Check clicked notification matches directNotificationId
        assertEquals(ONESIGNAL_NOTIFICATION_ID + "1", OneSignal_getSessionDirectNotification());
        // Make sure session is DIRECT
        assertTrue(OneSignal_getSessionType().isDirect());

        // Send unique outcome event
        OneSignal.sendUniqueOutcome(ONESIGNAL_OUTCOME_NAME);
        threadAndTaskWait();

        // Check measure end point was most recent request and contains clicked notification
        assertMeasureAtIndex(3, true, ONESIGNAL_OUTCOME_NAME, new JSONArray().put(ONESIGNAL_NOTIFICATION_ID + "1"));
        // Only 4 requests have been made
        assertRestCalls(4);

        // Send unique outcome event
        OneSignal.sendUniqueOutcome(ONESIGNAL_OUTCOME_NAME);
        threadAndTaskWait();

        // Make still only 4 requests have been made
        assertRestCalls(4);
    }

    @Test
    public void testUniqueOutcomeMeasureOnlySentOncePerNotification_whenSendingMultipleUniqueOutcomes_inIndirectSessions() throws Exception {
        foregroundAppAfterReceivingNotification();

        // Check notificationIds equal indirectNotificationIds from OSSessionManager
        JSONArray notificationIds = new JSONArray().put(ONESIGNAL_NOTIFICATION_ID + "1");
        assertEquals(notificationIds, OneSignal_getSessionIndirectNotificationIds());
        // Make sure session is INDIRECT
        assertTrue(OneSignal_getSessionType().isIndirect());

        // Send unique outcome event
        OneSignal.sendUniqueOutcome(ONESIGNAL_OUTCOME_NAME);
        threadAndTaskWait();

        // Check measure end point was most recent request and contains received notification
        assertMeasureAtIndex(2, false, ONESIGNAL_OUTCOME_NAME, new JSONArray().put(ONESIGNAL_NOTIFICATION_ID + "1"));
        // Only 3 requests have been made
        assertRestCalls(3);

        // Send unique outcome event
        OneSignal.sendUniqueOutcome(ONESIGNAL_OUTCOME_NAME);
        threadAndTaskWait();

        // Make still only 3 requests have been made
        assertRestCalls(3);

        // Background app
        blankActivityController.pause();
        threadAndTaskWait();

        // Receive notification
        Bundle bundle = getBaseNotifBundle(ONESIGNAL_NOTIFICATION_ID + "2");
        GcmBroadcastReceiver_onReceived(blankActivity, bundle);

        // Wait 31 seconds to start new session
        advanceSystemTimeBy(31);

        // Foreground app
        blankActivityController.resume();
        threadAndTaskWait();

        // Send unique outcome event
        OneSignal.sendUniqueOutcome(ONESIGNAL_OUTCOME_NAME);
        threadAndTaskWait();

        // Check notificationIds are not equal indirectNotificationIds from OSSessionManager
        notificationIds.put(ONESIGNAL_NOTIFICATION_ID + "2");
        assertEquals(notificationIds, OneSignal_getSessionIndirectNotificationIds());
        // Make sure session is INDIRECT
        assertTrue(OneSignal_getSessionType().isIndirect());

        // Check measure end point was most recent request and contains received notification
        assertMeasureAtIndex(4, false, ONESIGNAL_OUTCOME_NAME, new JSONArray().put(ONESIGNAL_NOTIFICATION_ID + "2"));
    }

    @Test
    public void testOutcomeNameSentWithMeasureOncePerSession_whenSendingMultipleUniqueOutcomes_inUnattributedSession() throws Exception {
        OneSignalInit();
        threadAndTaskWait();

        // Make sure session is UNATTRIBUTED
        assertTrue(OneSignal_getSessionType().isUnattributed());

        // Send unique outcome event
        OneSignal.sendUniqueOutcome(ONESIGNAL_OUTCOME_NAME);
        threadAndTaskWait();

        // Check measure end point was most recent request and contains received notification
        assertMeasureAtIndex(2, ONESIGNAL_OUTCOME_NAME);
        // Only 3 requests have been made
        assertRestCalls(3);

        // Send unique outcome event
        OneSignal.sendUniqueOutcome(ONESIGNAL_OUTCOME_NAME);
        threadAndTaskWait();

        // Make still only 3 requests have been made
        assertRestCalls(3);

        // Background app
        blankActivityController.pause();
        threadAndTaskWait();

        // Wait 31 seconds to start new session
        advanceSystemTimeBy(31);

        // Foreground app
        blankActivityController.resume();
        threadAndTaskWait();

        // Send unique outcome event
        OneSignal.sendUniqueOutcome(ONESIGNAL_OUTCOME_NAME);
        threadAndTaskWait();

        // Make sure session is UNATTRIBUTED
        assertTrue(OneSignal_getSessionType().isUnattributed());

        // Check measure end point was most recent request and contains received notification
        assertMeasureAtIndex(4, ONESIGNAL_OUTCOME_NAME);
    }

    @Test
    public void testCorrectOutcomeSent_fromNotificationOpenedHandler() throws Exception {
        // Init OneSignal with a custom opened handler
        OneSignalInit(new OneSignal.NotificationOpenedHandler() {
            @Override
            public void notificationOpened(OSNotificationOpenResult result) {
                OneSignal.sendOutcome(ONESIGNAL_OUTCOME_NAME);
            }
        });
        threadAndTaskWait();

        // Background app
        blankActivityController.pause();
        threadAndTaskWait();

        // Receive and open a notification
        OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false, ONESIGNAL_NOTIFICATION_ID);
        threadAndTaskWait();

        // Foreground the application
        blankActivityController.resume();
        threadAndTaskWait();

        // Make sure a measure request is made with the correct session and notifications
        assertMeasureAtIndex(3, true, ONESIGNAL_OUTCOME_NAME, new JSONArray("[" + ONESIGNAL_NOTIFICATION_ID + "]"));
    }

    @Test
    public void testNoDirectSession_fromNotificationOpen_whenAppIsInForeground() throws Exception {
        OneSignalInit();
        threadAndTaskWait();

        // Make sure no notification data exists
        assertNull(notificationOpenedMessage);

        // Click notification
        OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false, ONESIGNAL_NOTIFICATION_ID);
        threadAndTaskWait();

        // Check message String matches data sent in open handler
        assertEquals("Test Msg", notificationOpenedMessage);

        // Make sure session is not DIRECT
        assertFalse(OneSignal_getSessionType().isDirect());
    }

    @Test
    public void testDirectSession_fromNotificationOpen_whenAppIsInBackground() throws Exception {
        foregroundAppAfterClickingNotification();

        // Check message String matches data sent in open handler
        assertEquals("Test Msg", notificationOpenedMessage);

        // Make sure session is DIRECT
        assertTrue(OneSignal_getSessionType().isDirect());
    }

    @Test
    public void testIndirectSession_wontOverrideUnattributedSession_fromNotificationReceived_whenAppIsInForeground() throws Exception {
        OneSignalInit();
        threadAndTaskWait();

        // Make sure session is unattributed
        assertTrue(OneSignal_getSessionType().isUnattributed());

        // Receive notification
        Bundle bundle = getBaseNotifBundle(ONESIGNAL_NOTIFICATION_ID);
        GcmBroadcastReceiver_onReceived(blankActivity, bundle);

        // Make sure not indirect notifications exist
        assertNull(OneSignal_getSessionIndirectNotificationIds());
        // Make sure session is not DIRECT
        assertFalse(OneSignal_getSessionType().isIndirect());
    }

    @Test
    public void testDirectSession_willOverrideIndirectSession_whenAppIsInBackground() throws Exception {
        foregroundAppAfterReceivingNotification();

        // Foreground for 10 seconds
        advanceSystemTimeBy(10);

        // Make sure session is INDIRECT
        assertTrue(OneSignal_getSessionType().isIndirect());

        // Background app
        blankActivityController.pause();
        threadAndTaskWait();

        // Click notification before new session
        OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false, ONESIGNAL_NOTIFICATION_ID + "2");
        threadAndTaskWait();

        // Foreground app
        blankActivityController.resume();
        threadAndTaskWait();

        // Make sure on_focus is sent immediately since DIRECT session is going to override
        assertOnFocusAtIndex(3, new JSONObject() {{
            put("active_time", 10);
            put("direct", false);
            put("notification_ids", new JSONArray().put(ONESIGNAL_NOTIFICATION_ID + "1"));
        }});
        // Check directNotificationId is set to clicked notification
        assertEquals(ONESIGNAL_NOTIFICATION_ID + "2", OneSignal_getSessionDirectNotification());
        // Make sure session is DIRECT
        assertTrue(OneSignal_getSessionType().isDirect());
    }

    @Test
    public void testDirectSession_willOverrideDirectSession_whenAppIsInBackground() throws Exception {
        foregroundAppAfterClickingNotification();

        // Check directNotificationId is set to clicked notification
        assertEquals(ONESIGNAL_NOTIFICATION_ID + "1", OneSignal_getSessionDirectNotification());
        // Make sure session is DIRECT
        assertTrue(OneSignal_getSessionType().isDirect());

        // Background app
        blankActivityController.pause();
        threadAndTaskWait();

        // Click notification before new session
        OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false, ONESIGNAL_NOTIFICATION_ID + "2");
        threadAndTaskWait();

        // Check directNotificationId is set to clicked notification
        assertEquals(ONESIGNAL_NOTIFICATION_ID + "2", OneSignal_getSessionDirectNotification());
        // Make sure session is DIRECT
        assertTrue(OneSignal_getSessionType().isDirect());
    }

    @Test
    public void testIndirectSession_fromDirectSession_afterNewSession() throws Exception {
        foregroundAppAfterClickingNotification();

        // Check directNotificationId is set to clicked notification
        assertEquals(ONESIGNAL_NOTIFICATION_ID + "1", OneSignal_getSessionDirectNotification());
        // Make sure session is DIRECT
        assertTrue(OneSignal_getSessionType().isDirect());

        // Background app
        blankActivityController.pause();
        threadAndTaskWait();

        // Receive notification
        Bundle bundle = getBaseNotifBundle(ONESIGNAL_NOTIFICATION_ID + "2");
        GcmBroadcastReceiver_onReceived(blankActivity, bundle);
        threadAndTaskWait();

        // Wait 31 seconds
        advanceSystemTimeBy(31);

        // Foreground app through icon before new session
        blankActivityController.resume();
        threadAndTaskWait();

        // Check on_session is triggered
        assertTrue(ShadowOneSignalRestClient.lastUrl.matches("players/.*/on_session"));
        // Make sure no indirectNotificationIds exist
        assertNull(OneSignal_getSessionDirectNotification());
        // Make sure indirectNotificationIds are correct
        assertEquals(new JSONArray().put(ONESIGNAL_NOTIFICATION_ID + "2"), OneSignal_getSessionIndirectNotificationIds());
        // Make sure session is INDIRECT
        assertTrue(OneSignal_getSessionType().isIndirect());
    }

    @Test
    public void testIndirectSession_wontOverrideDirectSession_beforeNewSession() throws Exception {
        foregroundAppAfterClickingNotification();

        // Check directNotificationId is set to clicked notification
        assertEquals(ONESIGNAL_NOTIFICATION_ID + "1", OneSignal_getSessionDirectNotification());
        // Make sure session is DIRECT
        assertTrue(OneSignal_getSessionType().isDirect());

        // Background app
        blankActivityController.pause();
        threadAndTaskWait();

        // Receive notification
        Bundle bundle = getBaseNotifBundle(ONESIGNAL_NOTIFICATION_ID + "2");
        GcmBroadcastReceiver_onReceived(blankActivity, bundle);

        // Foreground app through icon before new session
        blankActivityController.resume();
        threadAndTaskWait();

        // Make sure no indirectNotificationIds exist
        assertNull(OneSignal_getSessionIndirectNotificationIds());
        // Check directNotificationId is set to clicked notification
        assertEquals(ONESIGNAL_NOTIFICATION_ID + "1", OneSignal_getSessionDirectNotification());
        // Make sure session is DIRECT
        assertTrue(OneSignal_getSessionType().isDirect());
    }

    @Test
    public void testIndirectSession_wontOverrideIndirectSession_beforeNewSession() throws Exception {
        foregroundAppAfterReceivingNotification();

        // Background app
        blankActivityController.pause();
        threadAndTaskWait();

        // Receive another notification
        Bundle bundle = getBaseNotifBundle(ONESIGNAL_NOTIFICATION_ID + "2");
        GcmBroadcastReceiver_onReceived(blankActivity, bundle);

        // Foreground app through icon before new session
        blankActivityController.resume();
        threadAndTaskWait();

        // Make sure indirectNotificationIds are correct
        JSONArray indirectNotificationIds = new JSONArray().put(ONESIGNAL_NOTIFICATION_ID + "1");
        assertEquals(indirectNotificationIds, OneSignal_getSessionIndirectNotificationIds());

        // Make sure session is INDIRECT
        assertTrue(OneSignal_getSessionType().isIndirect());
    }

    @Test
    public void testIndirectSession_sendsOnFocusFromSyncJob_after10SecondSession() throws Exception {
        foregroundAppAfterReceivingNotification();

        // App in foreground for 10 seconds
        advanceSystemTimeBy(10);

        // Background app
        // Sync job will be scheduled here but not run yet
        blankActivityController.pause();
        threadAndTaskWait();

        TestHelpers.runNextJob();
        threadAndTaskWait();

        assertOnFocusAtIndex(2, new JSONObject() {{
            put("active_time", 10);
            put("direct", false);
            put("notification_ids", new JSONArray().put(ONESIGNAL_NOTIFICATION_ID + "1"));
        }});
    }

    @Test
    public void testIndirectSession_sendsOnFocusFromSyncJob_evenAfterKillingApp_after10SecondSession() throws Exception {
        foregroundAppAfterReceivingNotification();

        // App in foreground for 10 seconds
        advanceSystemTimeBy(10);

        // Background app
        // Sync job will be scheduled here but not run yet
        blankActivityController.pause();
        threadAndTaskWait();

        fastColdRestartApp();

        TestHelpers.runNextJob();
        threadAndTaskWait();

        assertOnFocusAtIndex(2, new JSONObject() {{
            put("active_time", 10);
            put("direct", false);
            put("notification_ids", new JSONArray().put(ONESIGNAL_NOTIFICATION_ID + "1"));
        }});
    }

    @Test
    public void testIndirectSession_sendsOnFocusAttributionForPushPlayer_butNotEmailPlayer() throws Exception {
        OneSignal.setEmail("test@test.com");
        foregroundAppAfterReceivingNotification();

        // App in foreground for 10 seconds
        advanceSystemTimeBy(10);

        // Background app
        // Sync job will be scheduled here but not run yet
        blankActivityController.pause();
        threadAndTaskWait();

        TestHelpers.runNextJob();
        threadAndTaskWait();

        // Ensure we send notification attribution for push player
        assertOnFocusAtIndexForPlayerId(4, ShadowOneSignalRestClient.pushUserId);
        assertOnFocusAtIndex(4, new JSONObject() {{
            put("active_time", 10);
            put("direct", false);
            put("notification_ids", new JSONArray().put(ONESIGNAL_NOTIFICATION_ID + "1"));
        }});

        // Ensure we DO NOT send notification attribution for email player
        //   Otherwise it would look like 2 different session to outcomes.
        assertOnFocusAtIndexForPlayerId(5, ShadowOneSignalRestClient.emailUserId);
        assertOnFocusAtIndexDoesNotHaveKeys(5, Arrays.asList("direct", "notification_ids"));
    }

    @Test
    public void testIndirectSessionNotificationsUpdated_onNewIndirectSession() throws Exception {
        foregroundAppAfterReceivingNotification();

        // Make sure indirectNotificationIds are correct
        JSONArray indirectNotificationIds = new JSONArray().put(ONESIGNAL_NOTIFICATION_ID + "1");
        assertEquals(indirectNotificationIds, OneSignal_getSessionIndirectNotificationIds());

        // Background app
        blankActivityController.pause();
        threadAndTaskWait();

        // Receive notification
        Bundle bundle = getBaseNotifBundle(ONESIGNAL_NOTIFICATION_ID + "2");
        GcmBroadcastReceiver_onReceived(blankActivity, bundle);
        indirectNotificationIds.put(ONESIGNAL_NOTIFICATION_ID + "2");

        // App in background for 31 seconds to trigger new session
        advanceSystemTimeBy(31);

        // Foreground app through icon
        blankActivityController.resume();
        threadAndTaskWait();

        // Make sure indirectNotificationIds are updated and correct
        assertEquals(indirectNotificationIds, OneSignal_getSessionIndirectNotificationIds());
    }

    @Test
    public void testCleaningCachedNotifications_after7Days_willAlsoCleanUniqueOutcomeNotifications() throws Exception {
        foregroundAppAfterReceivingNotification();

        assertEquals(1, getAllNotificationRecords().size());
        assertEquals(0, getAllUniqueOutcomeNotificationRecords().size());

        // Should add a new unique outcome notifications (total in cache = 0 + 1)
        OneSignal.sendUniqueOutcome("unique_1");
        threadAndTaskWait();

        // Should not add a new unique outcome notifications (total in cache = 1)
        OneSignal.sendUniqueOutcome("unique_1");
        threadAndTaskWait();

        assertEquals(1, getAllNotificationRecords().size());
        assertEquals(1, getAllUniqueOutcomeNotificationRecords().size());

        // Background app
        blankActivityController.pause();
        threadAndTaskWait();

        // Wait for 30 seconds to trigger new session
        advanceSystemTimeBy(31);

        // Receive notification
        Bundle bundle = getBaseNotifBundle(ONESIGNAL_NOTIFICATION_ID + "2");
        GcmBroadcastReceiver_onReceived(blankActivity, bundle);

        // Foreground app through icon
        blankActivityController.resume();
        threadAndTaskWait();

        // Should add two unique outcome notifications (total in cache = 1 + 2)
        OneSignal.sendUniqueOutcome("unique_2");
        threadAndTaskWait();

        // Should add two unique outcome notifications (total in cache = 3 + 2)
        OneSignal.sendUniqueOutcome("unique_3");
        threadAndTaskWait();

        // Make sure only 2 notifications exist still, but 5 unique outcome notifications exist
        assertEquals(2, getAllNotificationRecords().size());
        assertEquals(5, getAllUniqueOutcomeNotificationRecords().size());

        // Wait a week to clear cached notifications
        advanceSystemTimeBy(604_800);

        // Restart the app and re-init OneSignal
        fastColdRestartApp();
        OneSignalInit();
        threadAndTaskWait();

        // Make sure when notification cache is cleaned so is the unique outcome events cache
        assertEquals(0, getAllNotificationRecords().size());
        assertEquals(0, getAllUniqueOutcomeNotificationRecords().size());
    }

    private void foregroundAppAfterClickingNotification() throws Exception {
        OneSignalInit();
        threadAndTaskWait();

        // Make sure no notification data exists
        assertNull(notificationOpenedMessage);
        // Make no direct notification id is set
        assertNull(OneSignal_getSessionDirectNotification());
        // Make sure session started unattributed
        assertTrue(OneSignal_getSessionType().isUnattributed());

        // Background app
        blankActivityController.pause();
        threadAndTaskWait();

        // Click notification before new session
        OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false, ONESIGNAL_NOTIFICATION_ID + "1");
        threadAndTaskWait();

        // App opened after clicking notification, but Robolectric needs this to simulate onAppFocus() code after a click
        blankActivityController.resume();
        threadAndTaskWait();
    }

    private void foregroundAppAfterReceivingNotification() throws Exception {
        OneSignalInit();
        threadAndTaskWait();

        // Make no direct notification id is set
        assertNull(OneSignal_getSessionIndirectNotificationIds());
        // Make sure session started unattributed
        assertTrue(OneSignal_getSessionType().isUnattributed());

        // Background app
        blankActivityController.pause();
        threadAndTaskWait();

        // Receive notification
        Bundle bundle = getBaseNotifBundle(ONESIGNAL_NOTIFICATION_ID + "1");
        GcmBroadcastReceiver_onReceived(blankActivity, bundle);

        // Foreground app through icon
        blankActivityController.resume();
        threadAndTaskWait();
    }

    private void OneSignalInit() throws Exception {
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());
        threadAndTaskWait();
        OneSignalPackagePrivateHelper.RemoteOutcomeParams params = new OneSignalPackagePrivateHelper.RemoteOutcomeParams();
        new MockOutcomesUtils().saveOutcomesParams(params);
        blankActivityController.resume();
    }

    private void OneSignalInit(OneSignal.NotificationOpenedHandler notificationOpenedHandler) throws Exception {
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, notificationOpenedHandler);
        threadAndTaskWait();
        OneSignalPackagePrivateHelper.RemoteOutcomeParams params = new OneSignalPackagePrivateHelper.RemoteOutcomeParams();
        new MockOutcomesUtils().saveOutcomesParams(params);
        blankActivityController.resume();
    }
}
