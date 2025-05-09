/**
 * Modified MIT License
 *
 * Copyright 2018 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.test.onesignal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.onesignal.MockOSLog;
import com.onesignal.MockOSSharedPreferences;
import com.onesignal.MockOSTimeImpl;
import com.onesignal.MockOneSignalDBHelper;
import com.onesignal.MockSessionManager;
import com.onesignal.OSDeviceState;
import com.onesignal.OSEmailSubscriptionObserver;
import com.onesignal.OSEmailSubscriptionStateChanges;
import com.onesignal.OSNotification;
import com.onesignal.OSNotificationAction;
import com.onesignal.OSNotificationOpenedResult;
import com.onesignal.OSNotificationReceivedEvent;
import com.onesignal.OSPermissionObserver;
import com.onesignal.OSPermissionStateChanges;
import com.onesignal.OSSMSSubscriptionObserver;
import com.onesignal.OSSMSSubscriptionStateChanges;
import com.onesignal.OSSubscriptionObserver;
import com.onesignal.OSSubscriptionStateChanges;
import com.onesignal.OneSignal;
import com.onesignal.OneSignal.ChangeTagsUpdateHandler;
import com.onesignal.OneSignal.OSSetLanguageCompletionHandler;
import com.onesignal.OneSignal.OSLanguageError;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.OneSignalShadowPackageManager;
import com.onesignal.PermissionsActivity;
import com.onesignal.ShadowBadgeCountUpdater;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowFirebaseAnalytics;
import com.onesignal.ShadowFocusHandler;
import com.onesignal.ShadowFusedLocationApiWrapper;
import com.onesignal.ShadowGMSLocationController;
import com.onesignal.ShadowGenerateNotification;
import com.onesignal.ShadowGoogleApiClientBuilder;
import com.onesignal.ShadowGoogleApiClientCompatProxy;
import com.onesignal.ShadowHmsInstanceId;
import com.onesignal.ShadowJobService;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignal;
import com.onesignal.ShadowOneSignalNotificationManager;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorADM;
import com.onesignal.ShadowPushRegistratorFCM;
import com.onesignal.ShadowReceiveReceiptController;
import com.onesignal.ShadowRoboNotificationManager;
import com.onesignal.StaticResetHelper;
import com.onesignal.SyncJobService;
import com.onesignal.SyncService;
import com.onesignal.debug.OneSignalLogEvent;
import com.onesignal.debug.OneSignalLogListener;
import com.onesignal.example.BlankActivity;
import com.onesignal.example.MainActivity;
import com.onesignal.influence.data.OSTrackerFactory;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowLog;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static com.onesignal.OneSignalPackagePrivateHelper.FCMBroadcastReceiver_processBundle;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_Process;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationOpenedProcessor_processFromContext;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_Log;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_getSessionListener;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_handleNotificationOpen;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_isInForeground;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setSessionManager;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setTime;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setTrackerFactory;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_taskQueueWaitingForInit;
import static com.onesignal.OneSignalPackagePrivateHelper.OSObservable;
import static com.onesignal.OneSignalPackagePrivateHelper.toUnescapedEUIDString;
import static com.onesignal.ShadowOneSignalRestClient.EMAIL_USER_ID;
import static com.onesignal.ShadowOneSignalRestClient.PUSH_USER_ID;
import static com.onesignal.ShadowOneSignalRestClient.REST_METHOD;
import static com.onesignal.ShadowOneSignalRestClient.SMS_USER_ID;
import static com.onesignal.ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse;
import static com.test.onesignal.GenerateNotificationRunner.getBaseNotifBundle;
import static com.test.onesignal.RestClientAsserts.assertAmazonPlayerCreateAtIndex;
import static com.test.onesignal.RestClientAsserts.assertAndroidPlayerCreateAtIndex;
import static com.test.onesignal.RestClientAsserts.assertHuaweiPlayerCreateAtIndex;
import static com.test.onesignal.RestClientAsserts.assertOnFocusAtIndex;
import static com.test.onesignal.RestClientAsserts.assertOnFocusAtIndexDoesNotHaveKeys;
import static com.test.onesignal.RestClientAsserts.assertOnSessionAtIndex;
import static com.test.onesignal.RestClientAsserts.assertPlayerCreatePushAtIndex;
import static com.test.onesignal.RestClientAsserts.assertRemoteParamsAtIndex;
import static com.test.onesignal.RestClientAsserts.assertRestCalls;
import static com.test.onesignal.TestHelpers.afterTestCleanup;
import static com.test.onesignal.TestHelpers.assertAndRunSyncService;
import static com.test.onesignal.TestHelpers.assertNextJob;
import static com.test.onesignal.TestHelpers.assertNumberOfServicesAvailable;
import static com.test.onesignal.TestHelpers.fastColdRestartApp;
import static com.test.onesignal.TestHelpers.flushBufferedSharedPrefs;
import static com.test.onesignal.TestHelpers.getNextJob;
import static com.test.onesignal.TestHelpers.pauseActivity;
import static com.test.onesignal.TestHelpers.restartAppAndElapseTimeToNextSession;
import static com.test.onesignal.TestHelpers.startRemoteNotificationReceivedHandlerService;
import static com.test.onesignal.TestHelpers.stopActivity;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.robolectric.Shadows.shadowOf;

@Config(packageName = "com.onesignal.example",
        shadows = {
            ShadowOneSignalRestClient.class,
            ShadowPushRegistratorADM.class,
            ShadowPushRegistratorFCM.class,
            ShadowOSUtils.class,
            ShadowCustomTabsClient.class,
            ShadowCustomTabsSession.class,
            ShadowNotificationManagerCompat.class,
            ShadowJobService.class,
            ShadowHmsInstanceId.class,
            ShadowFocusHandler.class,
            OneSignalShadowPackageManager.class
        },
        sdk = 21
)
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.LEGACY)
// Enable to ensure test order to consistency debug flaky test.
// @FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MainOneSignalClassRunner {

   private static final String ONESIGNAL_APP_ID = "b4f7f966-d8cc-11e4-bed1-df8f05be55ba";
   private static final String ONESIGNAL_NOTIFICATION_ID = "97d8e764-81c2-49b0-a644-713d052ae7d5";
   private static final String ONESIGNAL_SMS_NUMBER = "123456789";
   private static final String TIMEZONE_ID = "Europe/London";

   @SuppressLint("StaticFieldLeak")
   private static Activity blankActivity;
   private static ActivityController<BlankActivity> blankActivityController;
   private MockOSTimeImpl time;
   private OSTrackerFactory trackerFactory;
   private MockSessionManager sessionManager;
   private MockOneSignalDBHelper dbHelper;

   private static String lastNotificationOpenedBody;
   private static OSNotificationReceivedEvent lastServiceNotificationReceivedEvent;

   private static OneSignal.OSNotificationOpenedHandler getNotificationOpenedHandler() {
      return openedResult -> {

         // TODO: Double check if we should use this or not
         lastNotificationOpenedBody = openedResult.getNotification().getBody();
      };
   }

   private static JSONObject lastGetTags;
   private static void getGetTagsHandler() {
      OneSignal.getTags(tags -> lastGetTags = tags);
   }

   private static OSEmailSubscriptionStateChanges lastEmailSubscriptionStateChanges;
   private static OSSMSSubscriptionStateChanges lastSMSSubscriptionStateChanges;

   private static void cleanUp() throws Exception {
      lastServiceNotificationReceivedEvent = null;
      lastNotificationOpenedBody = null;
      lastGetTags = null;
      lastEmailSubscriptionStateChanges = null;
      lastSMSSubscriptionStateChanges = null;

      ShadowGMSLocationController.reset();

      TestHelpers.beforeTestInitAndCleanup();

      // Set remote_params GET response
      setRemoteParamsGetHtmlResponse();
   }

   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
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
      time = new MockOSTimeImpl();
      trackerFactory = new OSTrackerFactory(new MockOSSharedPreferences(), new MockOSLog(), time);
      sessionManager = new MockSessionManager(OneSignal_getSessionListener(), trackerFactory, new MockOSLog());
      dbHelper = new MockOneSignalDBHelper(ApplicationProvider.getApplicationContext());

      TestHelpers.setupTestWorkManager(blankActivity);

      cleanUp();

      OneSignal_setTime(time);
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
   public void testInitFromApplicationContext() throws Exception {
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(ApplicationProvider.getApplicationContext());
      threadAndTaskWait();
      // Testing we still register the user in the background if this is the first time. (Note Context is application)
      assertNotNull(ShadowOneSignalRestClient.lastPost);

      ShadowOneSignalRestClient.lastPost = null;
      restartAppAndElapseTimeToNextSession(time);

      OneSignal_setTime(time);
      // Restart app, should not send onSession automatically
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(ApplicationProvider.getApplicationContext());
      threadAndTaskWait();
      assertNull(ShadowOneSignalRestClient.lastPost);

      // Starting of first Activity should trigger onSession
      blankActivityController.resume();
      threadAndTaskWait();
      assertNotNull(ShadowOneSignalRestClient.lastPost);
   }

   @Test
   public void testDeviceTypeIsAndroid_forPlayerCreate() throws Exception {
      // 1. Init OneSignal so the app id is cached
      OneSignalInit();
      threadAndTaskWait();

      // 2. Make sure device_type is Android (1) in player create
      assertAndroidPlayerCreateAtIndex(1);
   }

   @Test
   public void testDeviceTypeIsAmazon_forPlayerCreate() throws Exception {
      // 1. Mock Amazon device type for this test
      ShadowOSUtils.supportsADM = true;

      // 2. Init OneSignal so the app id is cached
      OneSignalInit();
      threadAndTaskWait();

      // 3. Make sure device_type is Amazon (2) in player create
      assertAmazonPlayerCreateAtIndex(1);
   }

   @Test
   public void testDeviceTypeIsHuawei_forPlayerCreate() throws Exception {
      // 1. Mock Amazon device type for this test
      ShadowOSUtils.supportsHMS(true);

      // 2. Init OneSignal so the app id is cached
      OneSignalInit();
      threadAndTaskWait();

      // 3. Make sure device_type is Huawei (13) in player create
      assertHuaweiPlayerCreateAtIndex(1);
   }

   @Test
   public void testDeviceTypeIsAndroid_withoutOneSignalInit() throws Exception {
      // 1. Init OneSignal so the app id is cached
      OneSignalInit();
      threadAndTaskWait();

      // 2. Background app
      pauseActivity(blankActivityController);

      // 3. Restart OneSignal and clear the ShadowPushRegistratorADM statics
      restartAppAndElapseTimeToNextSession(time);
      threadAndTaskWait();

      // 4. Set OneSignal.appId and context simulating a background sync doing so
      OneSignal_setTime(time);
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity.getApplicationContext());

      // 5. Foreground app and trigger new session
      blankActivityController.resume();
      threadAndTaskWait();

      // 6. Make sure device_type is Android (1) in player create and on_session
      assertAndroidPlayerCreateAtIndex(1);
      assertOnSessionAtIndex(3);
   }

   @Test
   public void testDeviceTypeIsAmazon_withoutOneSignalInit() throws Exception {
      // 1. Mock Amazon device type for this test
      ShadowOSUtils.supportsADM = true;

      // 2. Init OneSignal so the app id is cached
      OneSignalInit();
      threadAndTaskWait();

      // 3. Background the app
      pauseActivity(blankActivityController);

      // 4. Restart the entire OneSignal and clear the ShadowPushRegistratorADM statics
      restartAppAndElapseTimeToNextSession(time);
      threadAndTaskWait();

      // 5. Set OneSignal.appId and context simulating a background sync doing so
      OneSignal_setTime(time);
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity.getApplicationContext());

      // 6. Foreground app and trigger new session
      blankActivityController.resume();
      threadAndTaskWait();

      // 7. Make sure device_type is Android (1) in player create and on_session
      assertAmazonPlayerCreateAtIndex(1);
      assertOnSessionAtIndex(3);
   }

   /**
    * 1. User opens app to MainActivity
    * 2. Comparison of MainActivity to dummy PermissionsActivity (1st Test Case)
    * 3. User gives privacy consent and LocationGMS prompt is shown with PermissionsActivity
    * 4. Comparison of PermissionsActivity to dummy PermissionsActivity (2nd Test Case)
    */
   @Test
   @Config(sdk = 26)
   public void testLocationPermissionPromptWithPrivacyConsent() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);
      OneSignal.promptLocation();
      OneSignalInit();
      threadAndTaskWait();

      // Create a dummy PermissionsActivity to compare in the test cases
      Intent expectedActivity = new Intent(ApplicationProvider.getApplicationContext(), PermissionsActivity.class);

      /* Without showing the LocationGMS prompt we check to see that the current
       * activity is not equal to PermissionsActivity since it is not showing yet */
      Intent actualActivity = shadowOf(blankActivity).getNextStartedActivity();
      // Assert false that the current activity is equal to the dummy PermissionsActivity
      assertFalse(actualActivity.filterEquals(expectedActivity));

      // Now we trigger the LocationGMS but providing consent to OneSignal SDK
      OneSignal.provideUserConsent(true);
      threadAndTaskWait();

      // Now the PermissionsActivity should be the next on the stack
      actualActivity = shadowOf(blankActivity).getNextStartedActivity();
      // Assert true that the current activity is equal to the dummy PermissionsActivity
      assertTrue(actualActivity.filterEquals(expectedActivity));
   }

   @Test
   public void testAppFocusWithPrivacyConsent() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);
      OneSignalInit();
      threadAndTaskWait();

      // Make sure onAppFocus does not move past privacy consent check and on_session is not called
      pauseActivity(blankActivityController);

      time.advanceSystemTimeBy(31);

      blankActivityController.resume();
      threadAndTaskWait();

      // Only remote param request should be made at this point since privacy consent has not been given
      int requestsCount = ShadowOneSignalRestClient.requests.size();
      assertEquals(1, requestsCount);
      assertRemoteParamsAtIndex(0);

      // Give privacy consent
      OneSignal.provideUserConsent(true);

      // Pause app and wait enough time to trigger on_session
      pauseActivity(blankActivityController);

      time.advanceSystemTimeBy(31);

      // Call onAppFocus and check that the last url is a on_session request
      blankActivityController.resume();
      threadAndTaskWait();

      assertTrue(ShadowOneSignalRestClient.lastUrl.matches("players/.*/on_session"));
   }

   @Test
   public void testAppOnFocusAfterOnSessionCall() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      pauseActivity(blankActivityController);
      time.advanceSystemTimeBy(60);

      time.advanceSystemAndElapsedTimeBy(0);
      blankActivityController.resume();
      threadAndTaskWait();

      assertTrue(ShadowOneSignalRestClient.lastUrl.matches("players/.*/on_session"));

      time.advanceSystemAndElapsedTimeBy(61);

      pauseActivity(blankActivityController);

      assertAndRunSyncService();
      assertTrue(ShadowOneSignalRestClient.lastUrl.matches("players/.*/on_focus"));
      assertEquals(61, ShadowOneSignalRestClient.lastPost.getInt("active_time"));
   }

   @Test
   public void testAppOnFocusAfterOnSessionCallFail() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      pauseActivity(blankActivityController);
      time.advanceSystemTimeBy(60);

      blankActivityController.resume();
      threadAndTaskWait();

      assertTrue(ShadowOneSignalRestClient.lastUrl.matches("players/.*/on_session"));
      ShadowOneSignalRestClient.lastUrl = null;

      time.advanceSystemTimeBy(59);

      pauseActivity(blankActivityController);
      assertNull(ShadowOneSignalRestClient.lastUrl);
   }

   @Test
   public void testAppOnFocusNeededAfterOnSessionCall() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      pauseActivity(blankActivityController);
      time.advanceSystemTimeBy(31);

      sessionManager.onNotificationReceived("notification_id");
      time.advanceSystemAndElapsedTimeBy(0);
      blankActivityController.resume();
      threadAndTaskWait();

      assertTrue(ShadowOneSignalRestClient.lastUrl.matches("players/.*/on_session"));

      time.advanceSystemAndElapsedTimeBy(61);

      OneSignalPackagePrivateHelper.RemoteOutcomeParams params = new OneSignalPackagePrivateHelper.RemoteOutcomeParams();
      trackerFactory.saveInfluenceParams(params);

      pauseActivity(blankActivityController);

      assertAndRunSyncService();
      assertEquals(4,  ShadowOneSignalRestClient.requests.size());
      assertOnFocusAtIndex(3, new JSONObject() {{
         put("active_time", 61);
         put("direct", false);
         put("notification_ids", new JSONArray().put("notification_id"));
      }});
   }

   @Test
   public void testAppOnFocus_containsOutcomeData_withOutcomeEventFlagsEnabled() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      // Disable all outcome flags
      OneSignalPackagePrivateHelper.RemoteOutcomeParams params = new OneSignalPackagePrivateHelper.RemoteOutcomeParams();
      trackerFactory.saveInfluenceParams(params);

      // Background app for 31 seconds
      pauseActivity(blankActivityController);
      time.advanceSystemTimeBy(31);

      // Click notification
      OneSignal_handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), "notification_id");
      threadAndTaskWait();

      // Foreground app
      time.advanceSystemAndElapsedTimeBy(0);
      blankActivityController.resume();
      threadAndTaskWait();

      // Make sure on_session is called
      assertTrue(ShadowOneSignalRestClient.lastUrl.matches("players/.*/on_session"));

      time.advanceSystemAndElapsedTimeBy(61);

      // Background app
      pauseActivity(blankActivityController);

      // A sync job should have been schedule, lets run it to ensure on_focus is called.
      assertRestCalls(4);
      assertAndRunSyncService();

      assertOnFocusAtIndex(4, new JSONObject() {{
         put("active_time", 61);
         put("direct", true);
         put("notification_ids", new JSONArray().put("notification_id"));
      }});
   }

   @Test
   public void testAppOnFocus_wontContainOutcomeData_withOutcomeEventFlagsDisabled() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      // Disable all outcome flags
      OneSignalPackagePrivateHelper.RemoteOutcomeParams params = new OneSignalPackagePrivateHelper.RemoteOutcomeParams(false, false, false);
      trackerFactory.saveInfluenceParams(params);

      // Background app for 31 seconds
      pauseActivity(blankActivityController);
      // Non on_focus call scheduled
      assertNumberOfServicesAvailable(0);
      time.advanceSystemTimeBy(31);

      // Click notification
      OneSignal_handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), ONESIGNAL_NOTIFICATION_ID + "1");
      threadAndTaskWait();

      // Foreground app
      time.advanceSystemAndElapsedTimeBy(0);
      blankActivityController.resume();
      threadAndTaskWait();
      // Make sure on_session is called
      assertTrue(ShadowOneSignalRestClient.lastUrl.matches("players/.*/on_session"));

      // Wait 60 seconds
      time.advanceSystemAndElapsedTimeBy(60);

      // Background app
      pauseActivity(blankActivityController);
      assertAndRunSyncService();
      assertNumberOfServicesAvailable(1);

      // Make sure no direct flag or notifications are added into the on_focus
      assertOnFocusAtIndexDoesNotHaveKeys(4, Arrays.asList("notification_ids", "direct"));
      assertOnFocusAtIndex(4, new JSONObject().put("active_time", 60));
   }

   @Test
   public void testAppOnFocusNeededAfterOnSessionCallFail() throws Exception {
      time.advanceSystemTimeBy(60);
      OneSignalInit();
      threadAndTaskWait();

      blankActivityController.resume();
      threadAndTaskWait();

      sessionManager.onDirectInfluenceFromNotificationOpen("notification_id");

      OneSignalPackagePrivateHelper.RemoteOutcomeParams params = new OneSignalPackagePrivateHelper.RemoteOutcomeParams();

      trackerFactory.saveInfluenceParams(params);
      time.advanceSystemTimeBy(10);

      ShadowOneSignalRestClient.lastUrl = null;
      pauseActivity(blankActivityController);

      assertNull(ShadowOneSignalRestClient.lastUrl);
   }

   @Test
   public void testAppTwiceOnFocusNeededAfterOnSessionCallFail() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      pauseActivity(blankActivityController);
      time.advanceSystemTimeBy(31);

      final String notificationId = "notification_id";
      sessionManager.onNotificationReceived(notificationId);
      sessionManager.onDirectInfluenceFromNotificationOpen(notificationId);
      time.advanceSystemAndElapsedTimeBy(0);
      blankActivityController.resume();
      threadAndTaskWait();

      assertTrue(ShadowOneSignalRestClient.lastUrl.matches("players/.*/on_session"));

      OneSignalPackagePrivateHelper.RemoteOutcomeParams params = new OneSignalPackagePrivateHelper.RemoteOutcomeParams();
      trackerFactory.saveInfluenceParams(params);

      time.advanceSystemAndElapsedTimeBy(61);

      pauseActivity(blankActivityController);

      // on_focus should not run yet as we need to wait until the job kicks off due to needing
      //   to wait until the session is ended for outcome session counts to be correct
      assertRestCalls(3);
      assertAndRunSyncService();
      assertRestCalls(4);
      assertOnFocusAtIndex(3, new JSONObject() {{
         put("active_time", 61);
         put("direct", false);
         put("notification_ids", new JSONArray().put(notificationId));
      }});

      // Doing a quick 1 second focus should NOT trigger another network call
      blankActivityController.resume();
      threadAndTaskWait();
      time.advanceSystemTimeBy(1);
      pauseActivity(blankActivityController);

      assertRestCalls(4);
   }

   @Test
   public void testAppStartFocus() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      assertTrue(ShadowOneSignalRestClient.lastUrl.matches("players"));

      stopActivity(blankActivityController);
      assertTrue(ShadowFocusHandler.Companion.getHasStopped());

      blankActivityController.resume();
      assertFalse(ShadowFocusHandler.Companion.getHasStopped());
   }

   private void setOneSignalContextOpenAppThenBackgroundAndResume() throws Exception {
      // 1. Context could be set by the app like this; Or on it's own when a push or other event happens
      OneSignal.initWithContext(blankActivity.getApplication());

      // 2. App is opened by user
      blankActivityController.resume();
      threadAndTaskWait();

      // 3. User backgrounds app
      pauseActivity(blankActivityController);

      // 4. User goes back to app again
      blankActivityController.resume();
      threadAndTaskWait();
   }

   @Test
   public void testAppDoesNotCrashIfContextIsSetupViaEventButInitWasNotCalled() throws Exception {
      setOneSignalContextOpenAppThenBackgroundAndResume();
      assertRestCalls(0);
   }

   @Test
   public void testStillRegistersIfInitCalledAfterIgnoredFocusEvents() throws Exception {
      OneSignal_setTrackerFactory(trackerFactory);
      OneSignal_setSessionManager(sessionManager);

      setOneSignalContextOpenAppThenBackgroundAndResume();

      OneSignalInit();
      threadAndTaskWait();

      // Assert network calls are still made after SDK is initialized.
      assertRemoteParamsAtIndex(0);
      assertPlayerCreatePushAtIndex(1);
      assertRestCalls(2);
   }

   @Test
   public void testInitWithContext_Activity() throws Exception {
      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
      ShadowOSUtils.subscribableStatus = 1;

      OneSignal.initWithContext(blankActivity);
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      blankActivityController.resume();
      threadAndTaskWait();

      assertTrue(OneSignal_isInForeground());
   }

   @Test
   public void testInitWithContext_ActivityResumedBeforeInit() throws Exception {
      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
      ShadowOSUtils.subscribableStatus = 1;

      blankActivityController.resume();
      OneSignal.initWithContext(blankActivity);
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      threadAndTaskWait();

      assertTrue(OneSignal_isInForeground());
   }

   @Test
   public void testInitWithContextAppIdSet_ActivityResumedBeforeInit() throws Exception {
      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
      ShadowOSUtils.subscribableStatus = 1;

      blankActivityController.resume();
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);

      threadAndTaskWait();

      assertTrue(OneSignal_isInForeground());
   }

   @Test
   public void testInitWithContext_Application() throws Exception {
      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.NONE);
      ShadowOSUtils.subscribableStatus = 1;

      OneSignal.initWithContext(blankActivity.getApplicationContext());
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      blankActivityController.resume();
      threadAndTaskWait();

      assertTrue(OneSignal_isInForeground());
   }

   @Test
   public void testOnSessionCalledOnlyOncePer30Sec() throws Exception {
      // Will call create
      time.advanceSystemTimeBy(2 * 60);
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      assertEquals("players", ShadowOneSignalRestClient.lastUrl);

      // Shouldn't call on_session if just resuming app with a short delay
      pauseActivity(blankActivityController);
      ShadowOneSignalRestClient.lastUrl = null;
      blankActivityController.resume();
      threadAndTaskWait();
      assertNull(ShadowOneSignalRestClient.lastUrl);

      // Or when restarting the app quickly.
      ShadowOneSignalRestClient.lastPost = null;
      fastColdRestartApp();
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      threadAndTaskWait();
      assertTrue(ShadowOneSignalRestClient.lastUrl.matches(".*android_params.js.*"));

      pauseActivity(blankActivityController);
      time.advanceSystemTimeBy(2 * 60);
      ShadowOneSignalRestClient.lastUrl = null;
      blankActivityController.resume();
      threadAndTaskWait();
      assertTrue(ShadowOneSignalRestClient.lastUrl.matches("players/.*/on_session"));
      assertEquals("{\"app_id\":\"b4f7f966-d8cc-11e4-bed1-df8f05be55ba\"}", ShadowOneSignalRestClient.lastPost.toString());
   }

   @Test
   public void testRequestMadeWithCorrectTimeZoneID() throws Exception {
      //Sets Default timezone and initalizes onesignal
      TimeZone.setDefault(TimeZone.getTimeZone(TIMEZONE_ID));
      OneSignalInit();
      threadAndTaskWait();
      assertEquals(TIMEZONE_ID, ShadowOneSignalRestClient.lastPost.get("timezone_id"));
   }

   @Test
   public void testAlwaysUseRemoteProjectNumberOverLocal() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      assertEquals("87654321", ShadowPushRegistratorFCM.lastProjectNumber);

      // A 2nd init call
      OneSignalInit();

      pauseActivity(blankActivityController);
      time.advanceSystemTimeBy(31);
      blankActivityController.resume();
      threadAndTaskWait();

      // Make sure when we try to register again before our on_session call it is with the remote
      // project number instead of the local one.
      assertEquals("87654321", ShadowPushRegistratorFCM.lastProjectNumber);
   }

   @Test
   public void testPutStillCalledOnChanges() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      assertEquals("players", ShadowOneSignalRestClient.lastUrl);

      // Shouldn't call on_session if just resuming app with a short delay
      pauseActivity(blankActivityController);
      ShadowOneSignalRestClient.lastUrl = null;
      blankActivityController.resume();
      threadAndTaskWait();
      assertNull(ShadowOneSignalRestClient.lastUrl);
      assertEquals(2, ShadowOneSignalRestClient.networkCallCount);

      ShadowOSUtils.carrierName = "test2";

      // Should make PUT call with changes on app restart
      ShadowOneSignalRestClient.lastPost = null;
      fastColdRestartApp();
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      threadAndTaskWait();

      assertEquals(4, ShadowOneSignalRestClient.networkCallCount);
      assertEquals("players/" + OneSignal.getDeviceState().getUserId(), ShadowOneSignalRestClient.lastUrl);
      assertEquals("{\"carrier\":\"test2\",\"app_id\":\"b4f7f966-d8cc-11e4-bed1-df8f05be55ba\"}", ShadowOneSignalRestClient.lastPost.toString());
   }

   @Test
   public void testCreatesEvenIfAppIsQuicklyForceKilledOnFirstLaunch() throws Exception {
      // 1. App cold restarted before the device has a chance to create a player
      OneSignalInit();
      fastColdRestartApp();

      // 2. 2nd cold start of the app.
      OneSignalInit();
      threadAndTaskWait();

      // 3. Ensure we made 3 network calls. (2 to android_params and 1 create player call)
      assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
      assertEquals("players", ShadowOneSignalRestClient.lastUrl);
      assertEquals(REST_METHOD.POST, ShadowOneSignalRestClient.requests.get(2).method);
   }

   @Test
   public void testOnSessionEvenIfQuickAppRestart() throws Exception {
      // 1. Do app first start and register the device for a player id
      OneSignalInit();
      threadAndTaskWait();

      restartAppAndElapseTimeToNextSession(time);

      // 2. App is restarted before it can make it's on_session call
      OneSignalInit();
      fastColdRestartApp();

      // 3. 3rd start of the app, we should make an on_session call since the last one did not go through
      OneSignalInit();
      threadAndTaskWait();

      // 4. Ensure we made 4 network calls. (2 to android_params and 1 create player call)
      assertEquals(5, ShadowOneSignalRestClient.networkCallCount);
      assertEquals("players/" + OneSignal.getDeviceState().getUserId() + "/on_session", ShadowOneSignalRestClient.lastUrl);
      assertEquals(REST_METHOD.POST, ShadowOneSignalRestClient.requests.get(4).method);
   }

   @Test
   public void testOnSessionFlagIsClearedAfterSuccessfullySynced() throws Exception {
      // 1. App cold restarted before the device has a chance to create a player
      OneSignalInit();
      fastColdRestartApp();

      // 2. 2nd cold start of the app, waiting to make sure device gets registered
      OneSignalInit();
      threadAndTaskWait();

      // 3. Restart the app without wating.
      fastColdRestartApp();

      // 4. 3rd cold start of the app.
      OneSignalInit();
      threadAndTaskWait();

      // 4. Ensure we made 4 network calls. (3 to android_params and 1 create player call)
      //    We are making sure we are only making on create call and NO on_session calls
      assertEquals(4, ShadowOneSignalRestClient.networkCallCount);
      assertEquals("players", ShadowOneSignalRestClient.requests.get(2).url);
      assertEquals(REST_METHOD.POST, ShadowOneSignalRestClient.requests.get(2).method);
   }

   @Test
   public void testPutCallsMadeWhenUserStateChangesOnAppResume() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      assertEquals("players", ShadowOneSignalRestClient.lastUrl);
   }

   @Test
   public void testAndroidParamsProjectNumberOverridesLocal() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      assertThat(ShadowPushRegistratorFCM.lastProjectNumber, not("123456789"));
   }

   @Test
   @Config(shadows = {ShadowOneSignal.class})
   public void testOpenFromNotificationWhenAppIsDead() throws Exception {
      OneSignal.initWithContext(blankActivity);
      OneSignal_handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Robo test message\", \"custom\": { \"i\": \"UUID\" } }]"), ONESIGNAL_NOTIFICATION_ID);

      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationOpenedHandler(getNotificationOpenedHandler());

      threadAndTaskWait();

      assertEquals("Robo test message", lastNotificationOpenedBody);
   }

   @Test
   public void testNullProjectNumberSetsErrorType() throws Exception {
      // Get call will not return a Google project number if it hasn't been entered on the OneSignal dashboard.
      ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(new JSONObject() {{
         put("awl_list", new JSONObject());
         put("android_sender_id", "");
      }});

      // Don't fire the mock callback, it will be done from the real class.
      ShadowPushRegistratorFCM.skipComplete = true;

      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      assertEquals(-6, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
   }

   @Test
   @Config(shadows = {ShadowRoboNotificationManager.class}, sdk = 26)
   public void testNotificationChannelListPayload() throws Exception {
      NotificationChannelManagerRunner testHelper = new NotificationChannelManagerRunner().setContext(blankActivity);

      JSONObject androidParams = testHelper.createBasicChannelListPayload();
      androidParams.put("awl_list", new JSONObject());
      // Get call will not return a Google project number if it hasn't been entered on the OneSignal dashboard.
      ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(androidParams.toString());

      // Don't fire the mock callback, it will be done from the real class.
      ShadowPushRegistratorFCM.skipComplete = true;

      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      testHelper.assertChannelsForBasicChannelList();
   }

   @Test
   public void shouldCorrectlyRemoveOpenedHandlerAndFireMissedOnesWhenAddedBack() throws Exception {
      OneSignalInit();
      OneSignal.setNotificationOpenedHandler(getNotificationOpenedHandler());
      threadAndTaskWait();

      OneSignal.setNotificationOpenedHandler(null);
      OneSignal_handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Robo test message\", \"custom\": { \"i\": \"UUID\" } }]"), ONESIGNAL_NOTIFICATION_ID);
      assertNull(lastNotificationOpenedBody);

      OneSignalInit();
      OneSignal.setNotificationOpenedHandler(getNotificationOpenedHandler());
      assertEquals("Robo test message", lastNotificationOpenedBody);
   }

   @Test
   public void shouldNotFireNotificationOpenAgainAfterAppRestart() throws Exception {
      OneSignalInit();
      OneSignal.setNotificationOpenedHandler(getNotificationOpenedHandler());

      threadAndTaskWait();

      Bundle bundle = getBaseNotifBundle();
      OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle);

      threadAndTaskWait();

      lastNotificationOpenedBody = null;

      // Restart app - Should omit notification_types
      StaticResetHelper.restSetStaticFields();
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationOpenedHandler(getNotificationOpenedHandler());

      threadAndTaskWait();

      assertEquals(null, lastNotificationOpenedBody);
   }

   @Test
   public void testOpeningLauncherActivity() throws Exception {
      // First init run for appId to be saved
      // At least OneSignal was init once for user to be subscribed
      // If this doesn't' happen, notifications will not arrive
      OneSignalInit();
      fastColdRestartApp();

      AddLauncherIntentFilter();
      // From app launching normally
      assertNotNull(shadowOf(blankActivity).getNextStartedActivity());
      // Will get appId saved
      OneSignal.initWithContext(blankActivity.getApplicationContext());
      OneSignal_handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), ONESIGNAL_NOTIFICATION_ID);

      assertNotNull(shadowOf(blankActivity).getNextStartedActivity());
      assertNull(shadowOf(blankActivity).getNextStartedActivity());
   }

   @Test
   public void testOpeningLauncherActivityWhenOffline() throws Exception {
      ShadowOneSignalRestClient.failGetParams = true;
      AddLauncherIntentFilter();

      OneSignalInit();
      // This removes Activity from the unit test's state
      assertNotNull(shadowOf(blankActivity).getNextStartedActivity());

      // Background the app
      blankActivityController.pause();

      // Open a notification
      OneSignal_handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), ONESIGNAL_NOTIFICATION_ID);

      // Ensure the app is foregrounded
      assertNotNull(shadowOf(blankActivity).getNextStartedActivity());
   }

   @Test
   public void testOpeningLaunchUrl() throws Exception {
      // First init run for appId to be saved
      // At least OneSignal was init once for user to be subscribed
      // If this doesn't' happen, notifications will not arrive
      OneSignalInit();
      fastColdRestartApp();
      OneSignal.initWithContext(blankActivity);
      // Removes app launch
      shadowOf(blankActivity).getNextStartedActivity();

      // No OneSignal init here to test case where it is located in an Activity.
      OneSignal_handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\", \"u\": \"http://google.com\" } }]"), ONESIGNAL_NOTIFICATION_ID);
      Intent intent = shadowOf(blankActivity).getNextStartedActivity();
      assertEquals("android.intent.action.VIEW", intent.getAction());
      assertEquals("http://google.com", intent.getData().toString());
      assertNull(shadowOf(blankActivity).getNextStartedActivity());
   }

   @Test
   public void testOpeningLaunchUrlWithDisableDefault() throws Exception {
      // Add the 'com.onesignal.NotificationOpened.DEFAULT' as 'DISABLE' meta-data tag
      OneSignalShadowPackageManager.addManifestMetaData("com.onesignal.NotificationOpened.DEFAULT", "DISABLE");

      // Removes app launch
      shadowOf(blankActivity).getNextStartedActivity();

      // No OneSignal init here to test case where it is located in an Activity.

      OneSignal_handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\", \"u\": \"http://google.com\" } }]"), ONESIGNAL_NOTIFICATION_ID);
      assertNull(shadowOf(blankActivity).getNextStartedActivity());
   }

   @Test
   public void testDisableOpeningLauncherActivityOnNotificationOpen() throws Exception {
      // Add the 'com.onesignal.NotificationOpened.DEFAULT' as 'DISABLE' meta-data tag
      OneSignalShadowPackageManager.addManifestMetaData("com.onesignal.NotificationOpened.DEFAULT", "DISABLE");

      // From app launching normally
      assertNotNull(shadowOf(blankActivity).getNextStartedActivity());
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationOpenedHandler(getNotificationOpenedHandler());
      assertNull(lastNotificationOpenedBody);

      OneSignal_handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), ONESIGNAL_NOTIFICATION_ID);

      assertNull(shadowOf(blankActivity).getNextStartedActivity());
      assertEquals("Test Msg", lastNotificationOpenedBody);
   }

   @Test
   public void testLaunchUrlSuppressTrue() throws Exception {
      // Add the 'com.onesignal.suppressLaunchURLs' as 'true' meta-data tag
      // First init run for appId to be saved
      // At least OneSignal was init once for user to be subscribed
      // If this doesn't' happen, notifications will not arrive
      OneSignalInit();
      fastColdRestartApp();

      // Add the 'com.onesignal.suppressLaunchURLs' as 'true' meta-data tag
      OneSignalShadowPackageManager.addManifestMetaData("com.onesignal.suppressLaunchURLs", true);

      // Removes app launch
      shadowOf(blankActivity).getNextStartedActivity();

      // Init with context since this is call before calling OneSignal_handleNotificationOpen internally
      OneSignal.initWithContext(blankActivity);

      OneSignal_handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\", \"u\": \"http://google.com\" } }]"), ONESIGNAL_NOTIFICATION_ID);
      threadAndTaskWait();

      assertOpenMainActivityIntent(shadowOf(blankActivity).getNextStartedActivity());
   }

   private void assertOpenMainActivityIntent(@NonNull Intent intent) {
      assertEquals(Intent.ACTION_MAIN, intent.getAction());
      assertTrue(intent.getCategories().contains(Intent.CATEGORY_LAUNCHER));
   }

   @Test
   public void testLaunchUrlSuppressFalse() throws Exception {
      // Add the 'com.onesignal.suppressLaunchURLs' as 'true' meta-data tag
      // First init run for appId to be saved
      // At least OneSignal was init once for user to be subscribed
      // If this doesn't' happen, notifications will not arrive
      OneSignalInit();
      fastColdRestartApp();

      OneSignalShadowPackageManager.addManifestMetaData("com.onesignal.suppressLaunchURLs", false);
      OneSignal.initWithContext(blankActivity);

      // Removes app launch
      shadowOf(blankActivity).getNextStartedActivity();

      // Init with context since this is call before calling OneSignal_handleNotificationOpen internally
      OneSignal.initWithContext(blankActivity);

      OneSignal_handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\", \"u\": \"http://google.com\" } }]"), ONESIGNAL_NOTIFICATION_ID);
      threadAndTaskWait();

      Intent intent = shadowOf(blankActivity).getNextStartedActivity();
      assertEquals("android.intent.action.VIEW", intent.getAction());
      assertEquals("http://google.com", intent.getData().toString());
      assertNull(shadowOf(blankActivity).getNextStartedActivity());
   }

   private static String notificationReceivedBody;
   private static int androidNotificationId;

   @Test
   @Config (shadows = { ShadowGenerateNotification.class })
   public void testNotificationReceivedWhenAppInFocus() throws Exception {
      // 1. Init OneSignal
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationWillShowInForegroundHandler(notificationReceivedEvent -> {
         androidNotificationId = notificationReceivedEvent.getNotification().getAndroidNotificationId();
         notificationReceivedBody = notificationReceivedEvent.getNotification().getBody();

         notificationReceivedEvent.complete(notificationReceivedEvent.getNotification());
      });
      OneSignal.setNotificationOpenedHandler(getNotificationOpenedHandler());

      // 2. Foreground the app
      blankActivityController.resume();
      threadAndTaskWait();

      Bundle bundle = getBaseNotifBundle();

      final boolean[] callbackEnded = {false};
      OneSignalPackagePrivateHelper.ProcessBundleReceiverCallback processBundleReceiverCallback = new OneSignalPackagePrivateHelper.ProcessBundleReceiverCallback() {
         public void onBundleProcessed(OneSignalPackagePrivateHelper.ProcessedBundleResult processedResult) {
            assertNotNull(processedResult);
            assertTrue(processedResult.isProcessed());
            callbackEnded[0] = true;
         }
      };

      FCMBroadcastReceiver_processBundle(blankActivity, bundle, processBundleReceiverCallback);
      Awaitility.await()
              .atMost(new Duration(3, TimeUnit.SECONDS))
              .pollInterval(new Duration(100, TimeUnit.MILLISECONDS))
              .untilAsserted(() -> {
                 assertTrue(callbackEnded[0]);
              });
      assertNull(lastNotificationOpenedBody);

      assertEquals("Robo test message", notificationReceivedBody);
      assertNotEquals(0, androidNotificationId);

      // Don't fire for duplicates
      lastNotificationOpenedBody = null;
      notificationReceivedBody = null;

      FCMBroadcastReceiver_processBundle(blankActivity, bundle);
      threadAndTaskWait();

      assertNull(lastNotificationOpenedBody);
      assertNull(notificationReceivedBody);

      lastNotificationOpenedBody = null;
      notificationReceivedBody = null;

      // Test that only NotificationReceivedHandler fires
      bundle = getBaseNotifBundle("UUID2");
      FCMBroadcastReceiver_processBundle(blankActivity, bundle);
      threadAndTaskWait();

      assertNull(lastNotificationOpenedBody);
      assertEquals("Robo test message", notificationReceivedBody);
   }

   // Start Received Request tests (report_received)

   @Test
   @Config(shadows = { ShadowGenerateNotification.class, ShadowReceiveReceiptController.class })
   public void testNotificationReceivedSendReceivedRequest_WhenAppInBackground() throws Exception {
      // First init run for appId to be saved
      // At least OneSignal was init once for user to be subscribed
      // If this doesn't' happen, notifications will not arrive
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();
      fastColdRestartApp();

      ShadowOneSignalRestClient.setRemoteParamsReceiveReceiptsEnable(true);
      // 1. initWithContext is called when startProcessing notification
      OneSignal.initWithContext(blankActivity.getApplicationContext());
      // 2. Receive a notification in background
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 3. Check that report_received where sent
      assertEquals(4, ShadowOneSignalRestClient.requests.size());
      assertEquals("notifications/UUID/report_received", ShadowOneSignalRestClient.lastUrl);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class, ShadowReceiveReceiptController.class })
   public void testNotificationReceivedSendReceivedRequest_WhenAppInForeground() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsReceiveReceiptsEnable(true);
      // First init run for appId to be saved
      // At least OneSignal was init once for user to be subscribed
      // If this doesn't' happen, notifications will not arrive
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      // 1. Receive a notification in background
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 2. Check that report_received where sent
      assertEquals(3, ShadowOneSignalRestClient.requests.size());
      assertEquals("notifications/UUID/report_received", ShadowOneSignalRestClient.lastUrl);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class, ShadowReceiveReceiptController.class })
   public void testNotificationReceivedNoSendReceivedRequest_WhenDisabled() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsReceiveReceiptsEnable(false);
      // First init run for appId to be saved
      // At least OneSignal was init once for user to be subscribed
      // If this doesn't' happen, notifications will not arrive
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      // 1. Receive a notification in background
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 2. Check that report_received where sent
      assertEquals(2, ShadowOneSignalRestClient.requests.size());
      assertNotEquals("notifications/UUID/report_received", ShadowOneSignalRestClient.lastUrl);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class, ShadowReceiveReceiptController.class })
   public void testNotificationReceivedNoSendReceivedRequest_WhenNotificationNotDisplayed() throws Exception {
      // 1. Setup correct notification extension service class
      startRemoteNotificationReceivedHandlerService("com.test.onesignal.MainOneSignalClassRunner$" +
              "RemoteNotificationReceivedHandler_NoDisplay");

      ShadowOneSignalRestClient.setRemoteParamsReceiveReceiptsEnable(true);
      // First init run for appId to be saved
      // At least OneSignal was init once for user to be subscribed
      // If this doesn't' happen, notifications will not arrive
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      // 2. Receive a notification in background
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 3. Make sure service was called
      assertNotNull(lastServiceNotificationReceivedEvent);

      // 4. Check that report_received where sent
      assertEquals(2, ShadowOneSignalRestClient.requests.size());
      assertNotEquals("notifications/UUID/report_received", ShadowOneSignalRestClient.lastUrl);
   }

   @Test
   @Config(sdk = 26, shadows = { ShadowGenerateNotification.class, ShadowOneSignalNotificationManager.class, ShadowReceiveReceiptController.class })
   public void testNotificationReceivedNoSendReceivedRequest_WhenNotificationNotDisplayed_DisabledByChannel() throws Exception {
      // 1. Setup correct notification extension service class
      startRemoteNotificationReceivedHandlerService("com.test.onesignal.MainOneSignalClassRunner$" +
              "RemoteNotificationReceivedHandler");

      ShadowOneSignalRestClient.setRemoteParamsReceiveReceiptsEnable(true);
      ShadowOneSignalNotificationManager.setNotificationChannelEnabled(false);
      // First init run for appId to be saved
      // At least OneSignal was init once for user to be subscribed
      // If this doesn't' happen, notifications will not arrive
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      // 2. Receive a notification in background
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 3. Make sure service was called
      assertNotNull(lastServiceNotificationReceivedEvent);

      // 4. Check that report_received where sent
      assertEquals(2, ShadowOneSignalRestClient.requests.size());
      assertNotEquals("notifications/UUID/report_received", ShadowOneSignalRestClient.lastUrl);
   }

   /**
    * @see #testNotificationReceivedNoSendReceivedRequest_WhenNotificationNotDisplayed
    */
   public static class RemoteNotificationReceivedHandler_NoDisplay implements OneSignal.OSRemoteNotificationReceivedHandler {

      @Override
      public void remoteNotificationReceived(Context context, OSNotificationReceivedEvent receivedEvent) {
         lastServiceNotificationReceivedEvent = receivedEvent;

         receivedEvent.complete(null);
      }
   }

   /**
    * @see #testNotificationReceivedNoSendReceivedRequest_WhenNotificationNotDisplayed_DisabledByChannel
    */
   public static class RemoteNotificationReceivedHandler implements OneSignal.OSRemoteNotificationReceivedHandler {

      @Override
      public void remoteNotificationReceived(Context context, OSNotificationReceivedEvent receivedEvent) {
         lastServiceNotificationReceivedEvent = receivedEvent;

         receivedEvent.complete(receivedEvent.getNotification());
      }
   }

   // End Received Request tests (report_received)

   @Test
   @Config(shadows = {ShadowBadgeCountUpdater.class})
   public void testBadgeClearOnFirstStart() throws Exception {
      ShadowBadgeCountUpdater.lastCount = -1;

      // First run should set badge to 0
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(ApplicationProvider.getApplicationContext());
      threadAndTaskWait();
      assertEquals(0, ShadowBadgeCountUpdater.lastCount);

      // Resume should have effect on badges. Notifications will be restored
      ShadowBadgeCountUpdater.lastCount = -1;
      blankActivityController.resume();
      threadAndTaskWait();
      assertEquals(-1, ShadowBadgeCountUpdater.lastCount);

      StaticResetHelper.restSetStaticFields();
      // Restart should have no effect on badges
      ShadowBadgeCountUpdater.lastCount = -1;
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(ApplicationProvider.getApplicationContext());
      threadAndTaskWait();
      assertEquals(-1, ShadowBadgeCountUpdater.lastCount);
   }

   @Test
   public void testUnsubscribeStatusShouldBeSetIfFCMErrored() throws Exception {
      ShadowPushRegistratorFCM.fail = true;
      OneSignalInit();
      threadAndTaskWait();
      assertEquals(-7, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
   }

   @Test
   public void testInvalidGoogleProjectNumberWithSuccessfulRegisterResponse() throws Exception {
      // A more real test would be "missing support library" but bad project number is an easier setup
      //   and is testing the same logic.
      ShadowPushRegistratorFCM.fail = true;
//      OneSignalInitWithBadProjectNum();
      OneSignalInit();
      threadAndTaskWait();
      Robolectric.getForegroundThreadScheduler().runOneTask();

      assertEquals(-7, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
      // Test that idsAvailable still fires
      assertEquals(ShadowOneSignalRestClient.pushUserId, OneSignal.getDeviceState().getUserId());
      assertNull(OneSignal.getDeviceState().getPushToken()); // Since FCM registration failed, this should be null

      // We now get a push token after the device registers with Onesignal,
      //    the idsAvailable callback should fire a 2nd time with a registrationId automatically
      ShadowPushRegistratorFCM.manualFireRegisterForPush();
      threadAndTaskWait();
      assertEquals(ShadowPushRegistratorFCM.regId, OneSignal.getDeviceState().getPushToken());
   }

   @Test
   public void testGMSErrorsAfterSuccessfulSubscribeDoNotUnsubscribeTheDevice() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));

      ShadowOneSignalRestClient.lastPost = null;
      restartAppAndElapseTimeToNextSession(time);

      ShadowPushRegistratorFCM.fail = true;
      OneSignalInit();
      threadAndTaskWait();
      assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void testInvalidGoogleProjectNumberWithFailedRegisterResponse() throws Exception {
      // Ensures lower number notification_types do not over right higher numbered ones.
      ShadowPushRegistratorFCM.fail = true;
//      OneSignalInitWithBadProjectNum();
      OneSignalInit();
      threadAndTaskWait();
      Robolectric.getForegroundThreadScheduler().runOneTask();
      assertEquals(-7, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Test that idsAvailable still fires
      assertEquals(ShadowOneSignalRestClient.pushUserId, OneSignal.getDeviceState().getUserId());
   }

   @Test
   public void testSetSubscriptionShouldNotOverrideSubscribeError() throws Exception {
//      OneSignalInitWithBadProjectNum();
      OneSignalInit();
      blankActivityController.resume();
      threadAndTaskWait();

      // Should not try to update server
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.disablePush(false);
      assertNull(ShadowOneSignalRestClient.lastPost);

      // Restart app - Should omit notification_types
      restartAppAndElapseTimeToNextSession(time);
      OneSignal_setTime(time);
//      OneSignalInitWithBadProjectNum();
      OneSignalInit();
      blankActivityController.resume();
      threadAndTaskWait();
      assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void shouldNotResetSubscriptionOnSession() throws Exception {
      OneSignalInit();
      OneSignal.disablePush(true);
      threadAndTaskWait();
      assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      restartAppAndElapseTimeToNextSession(time);

      OneSignalInit();
      threadAndTaskWait();
      assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void shouldSetSubscriptionCorrectlyEvenAfterFirstOneSignalRestInitFail() throws Exception {
      // Failed to register with OneSignal but SetSubscription was called with false
      ShadowOneSignalRestClient.failAll = true;
      OneSignalInit();
      OneSignal.disablePush(true);
      threadAndTaskWait();
      ShadowOneSignalRestClient.failAll = false;


      // Restart app - Should send unsubscribe with create player call.
      restartAppAndElapseTimeToNextSession(time);
      OneSignalInit();
      threadAndTaskWait();
      assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Restart app again - Value synced last time so don't send again.
      restartAppAndElapseTimeToNextSession(time);
      OneSignalInit();
      threadAndTaskWait();
      assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void shouldUpdateNotificationTypesCorrectlyEvenWhenSetSubscriptionIsCalledInAnErrorState() throws Exception {
      ShadowPushRegistratorFCM.fail = true;
      OneSignalInit();
      threadAndTaskWait();
      OneSignal.disablePush(false);

      // Restart app - Should send subscribe with on_session call.
      fastColdRestartApp();
      ShadowPushRegistratorFCM.fail = false;
      OneSignalInit();
      threadAndTaskWait();
      assertEquals(1, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
   }

   @Test
   public void shouldAllowMultipleSetSubscription() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      OneSignal.disablePush(true);
      threadAndTaskWait();

      assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Should not resend same value
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.disablePush(true);
      assertNull(ShadowOneSignalRestClient.lastPost);

      OneSignal.disablePush(false);
      threadAndTaskWait();
      assertEquals(1, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Should not resend same value
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.disablePush(false);
      threadAndTaskWait();
      assertNull(ShadowOneSignalRestClient.lastPost);
   }

   @Test
   public void testFCMTimeOutThenSuccessesLater() throws Exception {
      // Init with a bad connection to Google.
      ShadowPushRegistratorFCM.fail = true;
      OneSignalInit();
      threadAndTaskWait();
      assertFalse(ShadowOneSignalRestClient.lastPost.has("identifier"));

      // Registers for FCM after a retry
      ShadowPushRegistratorFCM.fail = false;
      ShadowPushRegistratorFCM.manualFireRegisterForPush();
      threadAndTaskWait();
      assertEquals(ShadowPushRegistratorFCM.regId, ShadowOneSignalRestClient.lastPost.getString("identifier"));

      // Cold restart app, should not send the same identifier again.
      ShadowOneSignalRestClient.lastPost = null;
      restartAppAndElapseTimeToNextSession(time);
      OneSignalInit();
      threadAndTaskWait();
      assertFalse(ShadowOneSignalRestClient.lastPost.has("identifier"));
   }

   @Test
   public void testChangeAppId_fromColdStart() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      int normalCreateFieldCount = ShadowOneSignalRestClient.lastPost.length();
      fastColdRestartApp();
      OneSignal.setAppId("99f7f966-d8cc-11e4-bed1-df8f05be55b2");
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      assertEquals(normalCreateFieldCount, ShadowOneSignalRestClient.lastPost.length());
   }

   /**
    * Similar to testChangeAppId_fromColdStart test
    */
   @Test
   public void testChangeAppId_duringRuntime() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      int normalCreateFieldCount = ShadowOneSignalRestClient.lastPost.length();
      OneSignal.setAppId("99f7f966-d8cc-11e4-bed1-df8f05be55b2");
      threadAndTaskWait();

      assertEquals(normalCreateFieldCount, ShadowOneSignalRestClient.lastPost.length());
   }

   @Test
   public void testUserDeletedFromServer() throws Exception {
      // 1. Open app and register for the first time.
      OneSignalInit();
      threadAndTaskWait();

      int normalCreateFieldCount = ShadowOneSignalRestClient.lastPost.length();
      ShadowOneSignalRestClient.lastPost = null;

      // 2. Developer deletes user and cold restarts the app
      restartAppAndElapseTimeToNextSession(time);
      ShadowOneSignalRestClient.failNext = true;
      ShadowOneSignalRestClient.setNextFailureJSONResponse(new JSONObject() {{
         put("errors", new JSONArray() {{
            put("Device type  is not a valid device_type. Valid options are: 0 = iOS, 1 = Android, 2 = Amazon, 3 = WindowsPhone(MPNS), 4 = ChromeApp, 5 = ChromeWebsite, 6 = WindowsPhone(WNS), 7 = Safari(APNS), 8 = Firefox");
         }});
      }});
      OneSignalInit();
      threadAndTaskWait();

      // 3. Assert the SDK handles the error above and make a new create call.
      assertPlayerCreatePushAtIndex(4);
      // Checking that the number  of fields matches exactly to the original create call.
      assertEquals(normalCreateFieldCount, ShadowOneSignalRestClient.lastPost.length());

      // 4. Developer deletes users again from dashboard while app is running.
      ShadowOneSignalRestClient.failNext = true;
      ShadowOneSignalRestClient.setNextFailureJSONResponse(new JSONObject() {{
         put("errors", new JSONArray().put("No user with this id found"));
      }});
      // 5. Make some call the will attempt a player update
      OneSignal.sendTag("key1", "value1");
      threadAndTaskWait();

      // 6. Assert the SDK handles the error above and make a new create call.
      assertPlayerCreatePushAtIndex(6);
      // Checking that the number of fields matches original create call, +1 for tags
      assertEquals(normalCreateFieldCount + 1, ShadowOneSignalRestClient.lastPost.length());
   }

   @Test
   public void testOfflineCrashes() throws Exception {
      ConnectivityManager connectivityManager = (ConnectivityManager)ApplicationProvider.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
      ShadowConnectivityManager shadowConnectivityManager = shadowOf(connectivityManager);
      shadowConnectivityManager.setActiveNetworkInfo(null);

      OneSignalInit();
      threadAndTaskWait();

      OneSignal.sendTag("key", "value");
      threadAndTaskWait();

      OneSignal.disablePush(true);
      threadAndTaskWait();
   }

   // ####### SendTags Tests ########

   @Test
   public void shouldSendTags() throws Exception {
      OneSignalInit();
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1\", \"test2\": \"value2\"}"));
      threadAndTaskWait();
      assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      assertEquals(ONESIGNAL_APP_ID, ShadowOneSignalRestClient.lastPost.getString("app_id"));
      assertEquals("value1", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("test1"));
      assertEquals("value2", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("test2"));

      // Should omit sending repeated tags
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1\", \"test2\": \"value2\"}"));
      threadAndTaskWait();
      assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      assertNull(ShadowOneSignalRestClient.lastPost);

      // Should only send changed and new tags
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1.5\", \"test2\": \"value2\", \"test3\": \"value3\"}"));
      threadAndTaskWait();
      assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
      JSONObject sentTags = ShadowOneSignalRestClient.lastPost.getJSONObject("tags");
      assertEquals("value1.5", sentTags.getString("test1"));
      assertFalse(sentTags.has(("test2")));
      assertEquals("value3", sentTags.getString("test3"));

      // Test empty JSONObject
      OneSignal.sendTags(new JSONObject());
      OneSignal.sendTags(new JSONObject(), null);
   }

   @Test
   @Config(shadows = { ShadowGenerateNotification.class })
   public void shouldSendTagsFromBackgroundOnAppKilled() throws Exception {
      // 1. Setup correct notification extension service class
      startRemoteNotificationReceivedHandlerService("com.test.onesignal.MainOneSignalClassRunner$" +
              "RemoteNotificationReceivedHandler_callSendTags");

      // First init run for appId to be saved
      // At least OneSignal was init once for user to be subscribed
      // If this doesn't' happen, notifications will not arrive
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();
      fastColdRestartApp();

      // 2. initWithContext is called when startProcessing notification
      OneSignal.initWithContext(blankActivity.getApplicationContext());
      // 3. Receive a notification in background
      FCMBroadcastReceiver_processBundle(blankActivity, getBaseNotifBundle());
      threadAndTaskWait();

      // 4. Make sure service was called
      assertNotNull(lastServiceNotificationReceivedEvent);

      // 5. Check that tags where sent
      assertEquals(4, ShadowOneSignalRestClient.requests.size());
      ShadowOneSignalRestClient.Request playersRequest = ShadowOneSignalRestClient.requests.get(3);
      JSONObject sentTags = playersRequest.payload.getJSONObject("tags");
      assertEquals("test_value", sentTags.getString("test_key"));
   }

   /**
    * @see #shouldSendTagsFromBackgroundOnAppKilled
    */
   public static class RemoteNotificationReceivedHandler_callSendTags implements OneSignal.OSRemoteNotificationReceivedHandler {

      @Override
      public void remoteNotificationReceived(Context context, OSNotificationReceivedEvent receivedEvent) {
         lastServiceNotificationReceivedEvent = receivedEvent;

         OneSignal.sendTag("test_key", "test_value");
         receivedEvent.complete(receivedEvent.getNotification());
      }
   }

   @Test
   public void shouldNotSendTagOnRepeats() throws Exception {
      OneSignalInit();
      OneSignal.sendTag("test1", "value1");
      threadAndTaskWait();
      assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      assertEquals(ONESIGNAL_APP_ID, ShadowOneSignalRestClient.lastPost.getString("app_id"));
      assertEquals("value1", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("test1"));

      // Should only send new tag
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.sendTag("test2", "value2");
      threadAndTaskWait();
      assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
      assertEquals("value2", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("test2"));

      // Should not resend first tags
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.sendTag("test1", "value1");
      threadAndTaskWait();
      assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
      assertNull(ShadowOneSignalRestClient.lastPost);
   }

   @Test
   public void shouldSendTagsWithRequestBatching() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1\"}"));
      OneSignal.sendTags(new JSONObject("{\"test2\": \"value2\"}"));

      getGetTagsHandler();
      threadAndTaskWait();

      assertEquals("value1", lastGetTags.getString("test1"));
      assertEquals("value2", lastGetTags.getString("test2"));
      assertEquals(4, ShadowOneSignalRestClient.networkCallCount);
   }

   @Test
   public void shouldNotAttemptToSendTagsBeforeGettingPlayerId() throws Exception {
      ShadowPushRegistratorFCM.skipComplete = true;
      OneSignalInit();
      threadAndTaskWait();

      assertEquals(1, ShadowOneSignalRestClient.networkCallCount);

      // Should not attempt to make a network call yet as we don't have a player_id
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1\"}"));
      threadAndTaskWait();

      assertEquals(1, ShadowOneSignalRestClient.networkCallCount);

      ShadowPushRegistratorFCM.fireLastCallback();
      threadAndTaskWait();

      assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      assertNotNull(OneSignal.getDeviceState().getUserId());
   }

   private static class TestChangeTagsUpdateHandler implements ChangeTagsUpdateHandler {
      private AtomicBoolean succeeded = new AtomicBoolean(false);
      private AtomicBoolean failed = new AtomicBoolean(false);

      @Override
      public void onSuccess(JSONObject tags) {
         succeeded.set(true);
      }

      @Override
      public void onFailure(OneSignal.SendTagsError error) {
         failed.set(true);
      }

      boolean getSucceeded() {
         return succeeded.get();
      }

      boolean getFailed() {
         return failed.get();
      }
   }

   // Tests to make sure the onSuccess handler works
   @Test
   public void shouldSendNewTagsWithResponse() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      TestChangeTagsUpdateHandler handler = new TestChangeTagsUpdateHandler();

      OneSignal.sendTags(new JSONObject("{\"test\" : \"value\"}"), handler);

      threadAndTaskWait();

      assertTrue(handler.getSucceeded());

      // now test to make sure the handler still fires for a call to
      // sendTags() that doesn't modify existing tags (no JSON delta)

      handler = new TestChangeTagsUpdateHandler();

      OneSignal.sendTags(new JSONObject("{\"test\" : \"value\"}"), handler);

      threadAndTaskWait();

      assertTrue(handler.getSucceeded());
   }

   // Tests to make sure that the onFailure callback works
   @Test
   public void shouldFailToSendTagsWithResponse() throws Exception {
      TestChangeTagsUpdateHandler handler = new TestChangeTagsUpdateHandler();

      OneSignalInit();
      threadAndTaskWait();

      ShadowOneSignalRestClient.failMethod = "players";
      ShadowOneSignalRestClient.failHttpCode = 403;
      ShadowOneSignalRestClient.setNextFailureJSONResponse(new JSONObject() {{
         put("tags", "error");
      }});

      // Should fail because players call failed with tags
      OneSignal.sendTags(new JSONObject("{\"test\" : \"value\"}"), handler);
      threadAndTaskWait();

      assertTrue(handler.getFailed());
   }

   // Tests to make sure that the SDK will call both handlers
   @Test
   public void shouldCallMultipleHandlers() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      TestChangeTagsUpdateHandler firstHandler = new TestChangeTagsUpdateHandler();
      TestChangeTagsUpdateHandler secondHandler = new TestChangeTagsUpdateHandler();

      OneSignal.sendTags(new JSONObject("{\"test1\" : \"value1\"}"), firstHandler);
      OneSignal.sendTags(new JSONObject("{\"test2\" : \"value2\"}"), secondHandler);

      threadAndTaskWait();

      assertTrue(firstHandler.getSucceeded());
      assertTrue(secondHandler.getSucceeded());
   }

   @Test
   public void testNestedSendTagsOnSuccess() throws Exception {
      final JSONObject tags = new JSONObject().put("key", "value");

      OneSignalInit();
      OneSignal.sendTags(tags);
      threadAndTaskWait();

      // Sending same tags a 2nd time creates the issue, as it take a different code path
      OneSignal.sendTags(
         tags,
         new ChangeTagsUpdateHandler() {
            @Override
            public void onSuccess(JSONObject values) {
               OneSignal.sendTags(tags, new TestChangeTagsUpdateHandler());
            }
            @Override
            public void onFailure(OneSignal.SendTagsError error) {}
         }
      );
      threadAndTaskWait();
   }

   @Test
   public void shouldCreatePlayerAfterDelayedTokenFromApplicationOnCreate() throws Exception {
      ShadowPushRegistratorFCM.skipComplete = true;
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity.getApplicationContext());
      blankActivityController.resume();
      threadAndTaskWait();

      ShadowPushRegistratorFCM.fireLastCallback();
      threadAndTaskWait();

      ShadowOneSignalRestClient.Request createPlayer = ShadowOneSignalRestClient.requests.get(1);
      assertEquals(2, ShadowOneSignalRestClient.requests.size());
      assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, createPlayer.method);
      assertEquals("players", createPlayer.url);
      assertEquals("b4f7f966-d8cc-11e4-bed1-df8f05be55ba", createPlayer.payload.get("app_id"));
      assertEquals(1, createPlayer.payload.get("device_type"));
   }

   @Test
   public void testOldIntValues() throws Exception {
      final SharedPreferences prefs = blankActivity.getSharedPreferences(OneSignal.class.getSimpleName(), Context.MODE_PRIVATE);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putString("ONESIGNAL_USERSTATE_SYNCVALYES_CURRENT_STATE", "{\"tags\": {\"int\": 123}}");
      editor.putString("ONESIGNAL_USERSTATE_SYNCVALYES_TOSYNC_STATE", "{\"tags\": {\"int\": 123}}");
      editor.apply();

      OneSignalInit();
      threadAndTaskWait();

      OneSignal.deleteTag("int");
      threadAndTaskWait();

      getGetTagsHandler();
      threadAndTaskWait();

      assertEquals("{}", lastGetTags.toString());
   }

   @Test
   public void testSendTagNonStringValues() throws Exception {
      OneSignalInit();
      OneSignal.sendTags("{\"int\": 122, \"bool\": true, \"null\": null, \"array\": [123], \"object\": {}}");
      getGetTagsHandler();
      threadAndTaskWait();

      assertEquals(String.class, lastGetTags.get("int").getClass());
      assertEquals("122", lastGetTags.get("int"));
      assertEquals(String.class, lastGetTags.get("bool").getClass());
      assertEquals("true", lastGetTags.get("bool"));

      // null should be the same as a blank string.
      assertFalse(lastGetTags.has("null"));

      assertFalse(lastGetTags.has("array"));
      assertFalse(lastGetTags.has("object"));
   }

   @Test
   public void testOneSignalMethodsBeforeDuringInitMultipleThreads() throws Exception {
      // Avoid logging to much
      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.ERROR, OneSignal.LOG_LEVEL.NONE);
      for (int a = 0; a < 10; a++) {
         List<Thread> threadList = new ArrayList<>(30);
         for (int i = 0; i < 30; i++) {
            Thread lastThread = newSendTagTestThread(Thread.currentThread(), i);
            lastThread.start();
            threadList.add(lastThread);
            assertFalse(failedCurModTest);
         }

         for (Thread thread : threadList)
            thread.join();
         assertFalse(failedCurModTest);
      }

      assertEquals(30000, OneSignal_taskQueueWaitingForInit().size());
      OneSignalInit();

      for (int a = 0; a < 10; a++) {
         List<Thread> threadList = new ArrayList<>(30);
         for (int i = 0; i < 30; i++) {
            Thread lastThread = newSendTagSetZeroThread(Thread.currentThread(), i);
            lastThread.start();
            threadList.add(lastThread);
            assertFalse(failedCurModTest);
         }

         for (Thread thread : threadList)
            thread.join();
         assertFalse(failedCurModTest);
      }

      threadAndTaskWait();

      JSONObject tags = ShadowOneSignalRestClient.lastPost.getJSONObject("tags");
      //assert the tags...which should all be 0
      for (int a = 0; a < 10; a++) {
         for (int i = 0; i < 30; i++) {
            assertEquals("0", tags.getString("key" + i));
         }
      }
   }

   private static Thread newSendTagSetZeroThread(final Thread mainThread, final int id) {
      //sets all keys to "0"
      return new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               OneSignal.sendTags("{\"key" + id + "\": " + 0 + "}");
            } catch (Throwable t) {
               // Ignore the flaky Robolectric null error.
               if (t.getStackTrace()[0].getClassName().equals("org.robolectric.shadows.ShadowMessageQueue"))
                  return;
               failedCurModTest = true;
               mainThread.interrupt();
               throw t;
            }
         }
      });
   }

   @Test
   @Config(shadows = {ShadowGoogleApiClientBuilder.class, ShadowGoogleApiClientCompatProxy.class, ShadowFusedLocationApiWrapper.class})
   public void testOneSignalMethodsBeforeInit() throws Exception {
      shadowOf(RuntimeEnvironment.application).grantPermissions("android.permission.ACCESS_FINE_LOCATION");
      ShadowFusedLocationApiWrapper.lat = 1.0d;
      ShadowFusedLocationApiWrapper.log = 2.0d;
      ShadowFusedLocationApiWrapper.accuracy = 3.0f;
      ShadowFusedLocationApiWrapper.time = 12345L;

      //queue up a bunch of actions and check that the queue gains size before init
      // ----- START QUEUE ------

      for(int a = 0; a < 500; a++) {
         OneSignal.sendTag("a" + a, String.valueOf(a));
      }

      OneSignal.getTags(new OneSignal.OSGetTagsHandler() {
         @Override
         public void tagsAvailable(JSONObject tags) {
            //assert that the first 10 tags sent were available
            try {
               for(int a = 0; a < 10; a++) {
                  assertEquals(String.valueOf(a), tags.get("a" + a));
               }
            }
            catch (Exception e) {
               e.printStackTrace();
            }
         }
      });
      threadAndTaskWait();

      // ----- END QUEUE ------

      // There should be 501 pending operations in the queue
      assertEquals(501, OneSignal_taskQueueWaitingForInit().size());

      OneSignalInit(); //starts the pending tasks executor

      // ---- EXECUTOR STILL RUNNING -----
      //these operations should be sent straight to the executor which is still running...
      OneSignal.sendTag("a499","5");
      OneSignal.sendTag("a498","4");
      OneSignal.sendTag("a497","3");
      OneSignal.sendTag("a496","2");
      OneSignal.sendTag("a495","1");

      OneSignal.getTags(new OneSignal.OSGetTagsHandler() {
         @Override
         public void tagsAvailable(JSONObject tags) {
            try {
               //assert that the first 10 tags sent were available
               for(int a = 0; a < 10; a++) {
                  assertEquals(String.valueOf(a),tags.get("a"+a));
               }
               //these tags should be returned with new values - getTags should be the
               //last operation with new tag values
               assertEquals("5",tags.getString("a499"));
               assertEquals("4",tags.getString("a498"));
               assertEquals("3",tags.getString("a497"));
               assertEquals("2",tags.getString("a496"));
               assertEquals("1",tags.getString("a495"));
            }
            catch (Exception e) {
               e.printStackTrace();
            }
         }
      });

      //after init, the queue should be empty...
      assertEquals(0, OneSignal_taskQueueWaitingForInit().size());

      threadAndTaskWait();

      //Assert that the queued up operations ran in correct order
      // and that the correct user state was POSTed and synced

      assertNotNull(ShadowOneSignalRestClient.lastPost.getJSONObject("tags"));

      JSONObject tags = ShadowOneSignalRestClient.lastPost.getJSONObject("tags");
      assertEquals("0",tags.getString("a0"));
      assertEquals("1",tags.getString("a1"));
      assertEquals("2",tags.getString("a2"));
      assertEquals("3",tags.getString("a3"));
      assertEquals("4",tags.getString("a4"));

      //we changed these tags while the executor was running...
      assertEquals("5",tags.getString("a499"));
      assertEquals("4",tags.getString("a498"));
      assertEquals("3",tags.getString("a497"));
      assertEquals("2",tags.getString("a496"));
      assertEquals("1",tags.getString("a495"));
   }

   @Test
   @Config(shadows = {ShadowGoogleApiClientBuilder.class, ShadowGoogleApiClientCompatProxy.class, ShadowFusedLocationApiWrapper.class})
   public void testOneSignalEmptyPendingTaskQueue() throws Exception {
      shadowOf(RuntimeEnvironment.application).grantPermissions("android.permission.ACCESS_FINE_LOCATION");
      ShadowFusedLocationApiWrapper.lat = 1.0d;
      ShadowFusedLocationApiWrapper.log = 2.0d;
      ShadowFusedLocationApiWrapper.accuracy = 3.0f;
      ShadowFusedLocationApiWrapper.time = 12345L;

      OneSignalInit(); //starts the pending tasks executor

      for(int a = 0; a < 5; a++)
         OneSignal.sendTag("a" + a, String.valueOf(a));

      //the queue should be empty since we already initialized the SDK
      assertEquals(0, OneSignal_taskQueueWaitingForInit().size());

      threadAndTaskWait();

      //Assert that the queued up operations ran in correct order
      // and that the correct user state was POSTed and synced

      assertNotNull(ShadowOneSignalRestClient.lastPost.getJSONObject("tags"));

      JSONObject tags = ShadowOneSignalRestClient.lastPost.getJSONObject("tags");
      assertEquals("0", tags.getString("a0"));
      assertEquals("1", tags.getString("a1"));
      assertEquals("2", tags.getString("a2"));
      assertEquals("3", tags.getString("a3"));
      assertEquals("4", tags.getString("a4"));
   }

   private static boolean failedCurModTest;
   @Test
   @Config(sdk = 26)
   public void testSendTagsConcurrentModificationException() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      final int TOTAL_RUNS = 75, CONCURRENT_THREADS = 15;
      for(int a = 0; a < TOTAL_RUNS; a++) {
         List<Thread> threadList = new ArrayList<>(CONCURRENT_THREADS);
         for (int i = 0; i < CONCURRENT_THREADS; i++) {
            Thread lastThread = newSendTagTestThread(Thread.currentThread(), a * i);
            lastThread.start();
            threadList.add(lastThread);
            assertFalse(failedCurModTest);
         }

         OneSignalPackagePrivateHelper.runAllNetworkRunnables();

         for(Thread thread : threadList)
            thread.join();

         assertFalse(failedCurModTest);
         System.out.println("Pass " + a + " out of " + TOTAL_RUNS);
      }
   }

   @Test
   @Config(sdk = 26)
   public void testFocusConcurrentModificationException() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      final int TOTAL_RUNS = 75, CONCURRENT_THREADS = 15;
      for(int a = 0; a < TOTAL_RUNS; a++) {
         List<Thread> threadList = new ArrayList<>(CONCURRENT_THREADS);
         for (int i = 0; i < CONCURRENT_THREADS; i++) {
            OneSignalPackagePrivateHelper.OneSignal_onAppLostFocus();
            Thread lastThread = newSendTagTestThread(Thread.currentThread(), a * i);
            lastThread.start();
            threadList.add(lastThread);
            assertFalse(failedCurModTest);
         }

         OneSignalPackagePrivateHelper.runAllNetworkRunnables();

         for(Thread thread : threadList)
            thread.join();

         assertFalse(failedCurModTest);
         System.out.println("Pass " + a + " out of " + TOTAL_RUNS);
      }
   }

   private static Thread newSendTagTestThread(final Thread mainThread, final int id) {
      return new Thread(() -> {
         try {
            for (int i = 0; i < 100; i++) {
               if (failedCurModTest)
                  break;
               OneSignal.sendTags("{\"key" + id + "\": " + i + "}");
            }
         } catch (Throwable t) {
            // Ignore the flaky Robolectric null error.
            if (t.getStackTrace()[0].getClassName().equals("org.robolectric.shadows.ShadowMessageQueue"))
               return;
            t.printStackTrace();
            failedCurModTest = true;
            mainThread.interrupt();
            throw t;
         }
      });
   }

   @Test
   public void shouldSaveToSyncIfKilledAndSyncOnNextAppStart() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      OneSignal.sendTag("key", "value");
      // Pause Activity
      blankActivityController.pause();

      // Network call for android params and player create should have been made.
      assertEquals(2, ShadowOneSignalRestClient.networkCallCount);

      // App closed and re-opened.
      fastColdRestartApp();
      OneSignalInit();
      threadAndTaskWait();

      // Un-synced tag should now sync.
      assertEquals("value", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("key"));
   }

   @Test
   @Config(sdk = 19)
   public void shouldSyncPendingChangesFromSyncService() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      OneSignal.sendTag("key", "value");

      // App is swiped away
      blankActivityController.pause();

      assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      fastColdRestartApp();
      threadAndTaskWait();

      // Tags did not get synced so SyncService should be scheduled
      AlarmManager alarmManager = (AlarmManager)ApplicationProvider.getApplicationContext().getSystemService(Context.ALARM_SERVICE);
      ShadowAlarmManager shadowAlarmManager = shadowOf(alarmManager);
      assertEquals(1, shadowAlarmManager.getScheduledAlarms().size());
      assertEquals(SyncService.class, shadowOf(shadowOf(shadowAlarmManager.getNextScheduledAlarm().operation).getSavedIntent()).getIntentClass());
      shadowAlarmManager.getScheduledAlarms().clear();

      // Test running the service
      Robolectric.buildService(SyncService.class).startCommand(0, 0);
      threadAndTaskWait();
      assertEquals("value", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("key"));
      // Remote params are called before players call due to initWithContext call inside SyncService
      assertEquals(4, ShadowOneSignalRestClient.networkCallCount);

      // Test starting app
      OneSignalInit();
      threadAndTaskWait();

      // No new changes, don't schedule another restart.
      // App is swiped away
      blankActivityController.pause();
      threadAndTaskWait();

      // No Focus service should be scheduled
      assertEquals(0, shadowOf(alarmManager).getScheduledAlarms().size());
      assertEquals(4, ShadowOneSignalRestClient.networkCallCount);
   }

   @Test
   public void shouldNotCrashIfSyncServiceIsRunBeforeInitIsDone() throws Exception {
      Robolectric.buildService(SyncService.class).create().startCommand(0,0);
      Robolectric.buildService(SyncJobService.class).create().get().onStartJob(null);
      threadAndTaskWait();
   }

   // Only fails if you run on it's own when running locally.
   //   Untested on travis CI
   @Test
   public void syncServiceRunnableShouldWorkConcurrently() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      for(int a = 0; a < 10; a++) {
         List<Thread> threadList = new ArrayList<>(30);
         for (int i = 0; i < 30; i++) {
            Thread lastThread = newSendTagTestThread(Thread.currentThread(), i);
            lastThread.start();
            threadList.add(lastThread);
            Runnable syncRunable = new OneSignalPackagePrivateHelper.OneSignalSyncServiceUtils_SyncRunnable();
            new Thread(syncRunable).start();
         }

         for(Thread thread : threadList)
            thread.join();
         assertFalse(failedCurModTest);
      }

      threadAndTaskWait();
   }

   private void sendTagsAndImmediatelyBackgroundApp() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      // Set tags and background app before a network call can be made
      OneSignal.sendTag("test", "value");
      blankActivityController.pause();
   }

   @Test
   public void ensureSchedulingOfSyncJobServiceOnActivityPause() throws Exception {
      sendTagsAndImmediatelyBackgroundApp();

      // There should be a SyncJobService service scheduled
      assertNumberOfServicesAvailable(1);
      assertAndRunSyncService();
   }

   @Test
   public void ensureSyncJobIsCanceledOnAppResume() throws Exception {
      sendTagsAndImmediatelyBackgroundApp();
      blankActivityController.resume();

      // Jobs should no longer be not be scheduled
      assertNull(getNextJob());
   }

   @Test
   public void ensureSyncIsRunOnAppResume() throws Exception {
      sendTagsAndImmediatelyBackgroundApp();
      blankActivityController.resume();
      threadAndTaskWait();

      assertEquals(3, ShadowOneSignalRestClient.requests.size());
      ShadowOneSignalRestClient.Request lastRequest = ShadowOneSignalRestClient.requests.get(2);
      assertEquals(REST_METHOD.PUT, lastRequest.method);
      assertEquals("value", lastRequest.payload.getJSONObject("tags").get("test"));
   }

   @Test
   @Config(sdk = 26)
   public void ensureNoConcurrentUpdateCallsWithSameData() throws Exception {
      sendTagsAndImmediatelyBackgroundApp();

      // Simulate a hung network connection when SyncJobService starts.
      ShadowOneSignalRestClient.freezeResponses = true;
      SyncJobService syncJobService = Robolectric.buildService(SyncJobService.class).create().get();
      syncJobService.onStartJob(null);
      threadAndTaskWait(); // Kicks off the Job service's background thread.

      // App is resumed, the SyncJobService is still waiting on a network response at this point.
      blankActivityController.resume();
      threadAndTaskWait();

      // Should only be 3 requests if there are no duplicates
      assertEquals(3, ShadowOneSignalRestClient.requests.size());
   }

   @Test
   @Config(sdk = 26, shadows = { ShadowGoogleApiClientCompatProxy.class, ShadowGMSLocationController.class })
   public void ensureSyncJobServiceRescheduleOnApiTimeout() throws Exception {
      ShadowGMSLocationController.apiFallbackTime = 0;
      shadowOf(RuntimeEnvironment.application).grantPermissions("android.permission.ACCESS_FINE_LOCATION");
      ShadowGoogleApiClientCompatProxy.skipOnConnected = true;

      OneSignalInit();
      threadAndTaskWait();

      assertEquals("com.onesignal.SyncJobService", getNextJob().getService().getClassName());
   }

   private void useAppFor2minThenBackground() throws Exception {
      time.advanceSystemAndElapsedTimeBy(0);
      // 1. Start app
      OneSignalInit();
      threadAndTaskWait();

      // 2. Wait 2 minutes
      time.advanceSystemAndElapsedTimeBy(2 * 60);

      // 3. Put app in background
      pauseActivity(blankActivityController);

      // 4. A SyncService should have been scheduled
      assertAndRunSyncService();
   }

   @Test
   @Config(sdk = 26)
   public void ensureSchedulingOfSyncJobServiceOnActivityPause_forPendingActiveTime() throws Exception {
      useAppFor2minThenBackground();

      // There should be a SyncJobService service scheduled
      assertNumberOfServicesAvailable(1);
      // A future job should be scheduled to finish the sync in case the process is killed
      //   for the on_focus call can be made.
      assertNextJob(SyncJobService.class, 0);

      // FIXME: Cleanup for upcoming unit test
      //  This is a one off scenario where a unit test fails after this one is run
      blankActivityController.resume();
      threadAndTaskWait();
   }

   @Test
   public void ensureSyncIsRunOnAppResume_forPendingActiveTime() throws Exception {
      useAppFor2minThenBackground();

      blankActivityController.resume();
      threadAndTaskWait();

      assertRestCalls(3);
      assertOnFocusAtIndex(2, 120);
   }

   @Test
   @Config(sdk = 26)
   public void ensureFailureOnPauseIsSentFromSyncService_forPendingActiveTime() throws Exception {
      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
      // 1. Start app
      time.advanceSystemAndElapsedTimeBy(0);
      OneSignalInit();
      threadAndTaskWait();

      // 2. Wait 2 minutes
      time.advanceSystemAndElapsedTimeBy(2 * 60);

      // 3. Put app in background, simulating network issue.
      ShadowOneSignalRestClient.failAll = true;
      pauseActivity(blankActivityController);

      assertAndRunSyncService();
      assertEquals(3, ShadowOneSignalRestClient.requests.size());

      // Simulate a hung network connection when SyncJobService starts.
      ShadowOneSignalRestClient.failAll = false;
      SyncJobService syncJobService = Robolectric.buildService(SyncJobService.class).create().get();
      syncJobService.onStartJob(null);
      threadAndTaskWait(); // Kicks off the Job service's background thread.

      assertRestCalls(4);
      assertOnFocusAtIndex(3, 120);

//      // FIXME: Cleanup for upcoming unit test
//      //  This is a one off scenario where a unit test fails after this one is run
//      blankActivityController.resume();
//      threadAndTaskWait();
   }

   @Test
   public void ensureNoRetriesForAndroidParamsOn403() throws Exception {
      ShadowOneSignalRestClient.failGetParams = true;
      ShadowOneSignalRestClient.failHttpCode = 403;

      OneSignalInit();
      threadAndTaskWait();

      assertEquals(1, ShadowOneSignalRestClient.requests.size());
   }

   @Test
   public void ensureNoRetriesForPlayerUpdatesOn403() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      ShadowOneSignalRestClient.failAll = true;
      ShadowOneSignalRestClient.failHttpCode = 403;
      OneSignal.sendTag("key", "value");
      threadAndTaskWait();

      // 1. Android Param success. 2. player create success. 3. On call to update which fails without retrying.
      assertEquals(3, ShadowOneSignalRestClient.requests.size());
   }

   @Test
   @Config(sdk = 26)
   public void ensureNoConcurrentUpdateCallsWithSameData_forPendingActiveTime() throws Exception {
      // useAppFor2minThenBackground();
      time.advanceSystemAndElapsedTimeBy(0);
      // 1. Start app
      OneSignalInit();
      threadAndTaskWait();

      // 2. Wait 2 minutes
      time.advanceSystemAndElapsedTimeBy(2 * 60);

      // 3. Put app in background
      ShadowOneSignalRestClient.freezeResponses = true;
      pauseActivity(blankActivityController);

      // 4. Simulate a hung network connection when SyncJobService starts.
      SyncJobService syncJobService = Robolectric.buildService(SyncJobService.class).create().get();
      syncJobService.onStartJob(null);
      threadAndTaskWait(); // Kicks off the Job service's background thread.

      // 5. App is resumed, the SyncJobService is still waiting on a network response at this point.
      blankActivityController.resume();
      threadAndTaskWait();

      // 6. Network connection now responding
      ShadowOneSignalRestClient.unFreezeResponses();
      threadAndTaskWait();

      assertEquals(3, ShadowOneSignalRestClient.requests.size());
   }

   @Test
   public void testMethodCalls_withSetAppIdCalledBeforeMethodCalls() throws Exception {
      OneSignal.setAppId(ONESIGNAL_APP_ID);

      getGetTagsHandler();
      OneSignal.sendTag("key", "value");
      OneSignal.sendTags("{\"key\": \"value\"}");
      OneSignal.deleteTag("key");
      OneSignal.deleteTags("[\"key1\", \"key2\"]");
      OneSignal.disablePush(false);
      OneSignal.promptLocation();
      OneSignal.postNotification("{}", new OneSignal.PostNotificationResponseHandler() {
         @Override
         public void onSuccess(JSONObject response) {}
         @Override
         public void onFailure(JSONObject response) {}
      });
      OneSignal.setNotificationOpenedHandler(null);
      OneSignal.setNotificationWillShowInForegroundHandler(null);
      threadAndTaskWait();

      // Permission subscription wont return until OneSignal init is done
      assertNull(OneSignal.getDeviceState());

      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      assertTrue(OneSignal.getDeviceState().isSubscribed());
   }

   @Test
   public void testMethodCalls_withInitWithContextCalledBeforeMethodCalls() throws Exception {
      OneSignal.initWithContext(blankActivity);

      getGetTagsHandler();
      OneSignal.sendTag("key", "value");
      OneSignal.sendTags("{\"key\": \"value\"}");
      OneSignal.deleteTag("key");
      OneSignal.deleteTags("[\"key1\", \"key2\"]");
      OneSignal.disablePush(false);
      OneSignal.promptLocation();
      OneSignal.postNotification("{}", new OneSignal.PostNotificationResponseHandler() {
         @Override
         public void onSuccess(JSONObject response) {}
         @Override
         public void onFailure(JSONObject response) {}
      });
      OneSignal.setNotificationOpenedHandler(null);
      OneSignal.setNotificationWillShowInForegroundHandler(null);
      threadAndTaskWait();

      // TODO change to assertNull(OneSignal.getPermissionSubscriptionState()); when privacy consent public set is removed
      assertFalse(OneSignal.getDeviceState().isSubscribed());

      OneSignal.setAppId(ONESIGNAL_APP_ID);
      threadAndTaskWait();

      assertTrue(OneSignal.getDeviceState().isSubscribed());
   }

   @Test
   public void testMethodCalls_withInitWithContextAndSetAppId() throws Exception {
      getGetTagsHandler();
      OneSignal.sendTag("key", "value");
      OneSignal.sendTags("{\"key\": \"value\"}");
      OneSignal.deleteTag("key");
      OneSignal.deleteTags("[\"key1\", \"key2\"]");
      OneSignal.disablePush(false);
      OneSignal.promptLocation();
      OneSignal.postNotification("{}", new OneSignal.PostNotificationResponseHandler() {
         @Override
         public void onSuccess(JSONObject response) {}
         @Override
         public void onFailure(JSONObject response) {}
      });
      OneSignal.setNotificationOpenedHandler(null);
      OneSignal.setNotificationWillShowInForegroundHandler(null);
      threadAndTaskWait();

      assertNull(OneSignal.getDeviceState());

      OneSignal.initWithContext(blankActivity);
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      threadAndTaskWait();

      assertTrue(OneSignal.getDeviceState().isSubscribed());
   }

   @Test
   public void testMethodCalls_withSetAppIdAndInitWithContext() throws Exception {
      getGetTagsHandler();
      OneSignal.sendTag("key", "value");
      OneSignal.sendTags("{\"key\": \"value\"}");
      OneSignal.deleteTag("key");
      OneSignal.deleteTags("[\"key1\", \"key2\"]");
      OneSignal.disablePush(false);
      OneSignal.promptLocation();
      OneSignal.postNotification("{}", new OneSignal.PostNotificationResponseHandler() {
         @Override
         public void onSuccess(JSONObject response) {}
         @Override
         public void onFailure(JSONObject response) {}
      });
      OneSignal.setNotificationOpenedHandler(null);
      OneSignal.setNotificationWillShowInForegroundHandler(null);
      threadAndTaskWait();

      assertNull(OneSignal.getDeviceState());

      OneSignal.initWithContext(blankActivity);
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      threadAndTaskWait();

      assertTrue(OneSignal.getDeviceState().isSubscribed());
   }

   // ####### DeleteTags Tests ######
   @Test
   public void testDeleteTagWithNonexistingKey() {
      OneSignalInit();
      OneSignal.deleteTag("int");
   }

   @Test
   public void testDeleteTags() throws Exception {
      OneSignalInit();
      OneSignal.sendTags("{\"str\": \"str1\", \"int\": 122, \"bool\": true}");
      OneSignal.deleteTag("int");
      getGetTagsHandler();
      threadAndTaskWait();

      assertFalse(lastGetTags.has("int"));
      lastGetTags = null;

      // Should only send the tag we added back.
      OneSignal.sendTags("{\"str\": \"str1\", \"int\": 122, \"bool\": true}");
      threadAndTaskWait();
      assertEquals("{\"int\":\"122\"}", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").toString());

      // Make sure a single delete works.
      OneSignal.deleteTag("int");
      getGetTagsHandler();
      threadAndTaskWait();
      assertFalse(lastGetTags.has("int"));

      // Delete all other tags, the 'tags' key should not exists in local storage.
      OneSignal.deleteTags(Arrays.asList("bool", "str"));
      threadAndTaskWait();

      flushBufferedSharedPrefs();
      final SharedPreferences prefs = blankActivity.getSharedPreferences(OneSignal.class.getSimpleName(), Context.MODE_PRIVATE);
      String syncValues = prefs.getString("ONESIGNAL_USERSTATE_SYNCVALYES_CURRENT_STATE", null);
      assertFalse(new JSONObject(syncValues).has("tags"));
   }


   @Test
   public void testDeleteTagsAfterSync() throws Exception {
      OneSignalInit();
      OneSignal.sendTags("{\"foo\": \"bar\", \"fuz\": \"baz\"}");
      threadAndTaskWait();
      assertEquals("bar", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").get("foo"));
      assertEquals("baz", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").get("fuz"));

      OneSignal.deleteTags("[\"foo\", \"fuz\"]");
      threadAndTaskWait();
      assertEquals("", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").get("foo"));
      assertEquals("", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").get("fuz"));

      getGetTagsHandler();
      threadAndTaskWait();

      assertEquals("{}", lastGetTags.toString());

      flushBufferedSharedPrefs();
      final SharedPreferences prefs = blankActivity.getSharedPreferences(OneSignal.class.getSimpleName(), Context.MODE_PRIVATE);
      JSONObject syncValues = new JSONObject(prefs.getString("ONESIGNAL_USERSTATE_SYNCVALYES_CURRENT_STATE", null));
      assertFalse(syncValues.has("tags"));
   }

   @Test
   public void testOmitDeletesOfNonExistingKeys() throws Exception {
      OneSignalInit();
      OneSignal.deleteTag("this_key_does_not_exist");
      threadAndTaskWait();

      assertFalse(ShadowOneSignalRestClient.lastPost.has("tags"));
   }

   // ####### End DeleteTags Tests ######
   // ####### GetTags Tests ########

   @Test
   public void testGetTagsWithNoTagsShouldBeNull() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      getGetTagsHandler();
      threadAndTaskWait();

      assertNull(lastGetTags);
      String lastUrl = ShadowOneSignalRestClient.lastUrl;
      assertEquals("?app_id=" + ONESIGNAL_APP_ID, lastUrl.substring(lastUrl.lastIndexOf("?")));
   }

   @Test
   public void testGetTagNullCheck() throws Exception {
      OneSignalInit();
      OneSignal.getTags(null);
   }

   @Test
   public void shouldGetTags() throws Exception {
      OneSignalInit();
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1\", \"test2\": \"value2\"}"));
      threadAndTaskWait();
      getGetTagsHandler();
      threadAndTaskWait();

      assertEquals("value1", lastGetTags.getString("test1"));
      assertEquals("value2", lastGetTags.getString("test2"));
   }

   @Test
   public void shouldGetTagsFromServerOnFirstCallAndMergeLocalAndRemote() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      ShadowOneSignalRestClient.setNextSuccessfulGETJSONResponse(new JSONObject() {{
         put("tags", new JSONObject() {{
            put("test1", "value1");
            put("test2", "value2");
         }});
      }});

      getGetTagsHandler();
      threadAndTaskWait();

      assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
      assertEquals("value1", lastGetTags.getString("test1"));
      assertEquals("value2", lastGetTags.getString("test2"));

      // Makes sure a 2nd call to GetTags correctly uses existing tags and merges new local changes.
      lastGetTags = null;
      OneSignal.sendTag("test3", "value3");
      getGetTagsHandler();
      threadAndTaskWait();
      assertEquals("value1", lastGetTags.getString("test1"));
      assertEquals("value2", lastGetTags.getString("test2"));
      assertEquals("value3", lastGetTags.getString("test3"));
      threadAndTaskWait();
      // Also ensure only 1 network call is made to just send the new tags only.
      assertEquals(4, ShadowOneSignalRestClient.networkCallCount);

      fastColdRestartApp();
      OneSignalInit();
      threadAndTaskWait();

      // Test that local pending changes are still applied but new changes made server side a respected.
      lastGetTags = null;
      ShadowOneSignalRestClient.failNextPut = true;
      OneSignal.deleteTag("test2");
      OneSignal.sendTag("test4", "value4");
      ShadowOneSignalRestClient.setNextSuccessfulGETJSONResponse(new JSONObject() {{
         put("tags", new JSONObject() {{
            put("test1", "value1");
            put("test2", "value2");
            put("test3", "ShouldOverride");
            put("test4", "RemoteShouldNotOverwriteLocalPending");
         }});
      }});
      getGetTagsHandler();
      threadAndTaskWait();
      assertEquals("value1", lastGetTags.getString("test1"));
      System.out.println("lastGetTags: " + lastGetTags);
      assertFalse(lastGetTags.has("test2"));
      assertEquals("ShouldOverride", lastGetTags.getString("test3"));
      assertEquals("value4", lastGetTags.getString("test4"));
      assertEquals(8, ShadowOneSignalRestClient.networkCallCount);

      assertEquals("{\"test2\":\"\",\"test4\":\"value4\"}",
                           ShadowOneSignalRestClient.lastPost.optJSONObject("tags").toString());
   }

   @Test
   public void getTagsDelayedAfterRegistering() throws Exception {
      // Set players GET response
      ShadowOneSignalRestClient.setNextSuccessfulGETJSONResponse(new JSONObject() {{
         put("tags", new JSONObject() {{
            put("test1", "value1");
         }});
      }});
      ShadowOneSignalRestClient.nextSuccessfulGETResponsePattern = Pattern.compile("players/.*");
      OneSignalInit();
      // need to wait for remote_params call -> privacy consent set to false
      threadAndTaskWait();
      getGetTagsHandler();
      threadAndTaskWait();

      assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
      assertEquals("value1", lastGetTags.getString("test1"));
      assertTrue(ShadowOneSignalRestClient.lastUrl.contains(ShadowOneSignalRestClient.pushUserId));
   }

   // ####### End GetTags Tests ########

   @Test
   public void testSetLanguageOnPlayerCreate() throws Exception {
      OneSignalInit();
      OneSignal.setLanguage("fr");
      threadAndTaskWait();

      ShadowOneSignalRestClient.Request lastRequest = ShadowOneSignalRestClient.requests.get(1);

      assertEquals("fr", lastRequest.payload.getString("language"));
   }

   @Test
   public void testSetLanguagePUTRequest() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      OneSignal.setLanguage("fr");
      threadAndTaskWait();

      ShadowOneSignalRestClient.Request lastRequest = ShadowOneSignalRestClient.requests.get(2);
      assertEquals("fr", lastRequest.payload.getString("language"));
   }

   @Test
   public void testSetLanguageOnSession() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      restartAppAndElapseTimeToNextSession(time);

      OneSignalInit();
      OneSignal.setLanguage("fr");
      threadAndTaskWait();

      ShadowOneSignalRestClient.Request lastRequest = ShadowOneSignalRestClient.requests.get(3);
      assertEquals("fr", lastRequest.payload.getString("language"));
   }

   private static class TestSetLanguageHandler implements OSSetLanguageCompletionHandler {
      private AtomicBoolean succeeded = new AtomicBoolean(false);
      private AtomicBoolean failed = new AtomicBoolean(false);

      @Override
      public void onSuccess(String results) { succeeded.set(true); }

      @Override
      public void onFailure(OSLanguageError error) { failed.set(true); }

      boolean getSucceeded() { return  succeeded.get(); }

      boolean getFailed() { return failed.get(); }
   }

   // Tests to make sure the onSuccess handler works
   @Test
   public void shouldSetLanguageWithResponse() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      TestSetLanguageHandler handler = new TestSetLanguageHandler();

      OneSignal.setLanguage("fr", handler);

      threadAndTaskWait();

      assertTrue(handler.getSucceeded());

      // now test to make sure the handler still fires for a call to
      // setLanguage() that modifies existing language

      handler = new TestSetLanguageHandler();

      OneSignal.setLanguage("es", handler);

      threadAndTaskWait();

      assertTrue(handler.getSucceeded());
   }

   // Tests to make sure that the onFailure callback works
   @Test
   public void shouldFailToSetLanguageWithResponse() throws Exception {
      TestSetLanguageHandler handler = new TestSetLanguageHandler();

      OneSignalInit();
      threadAndTaskWait();

      ShadowOneSignalRestClient.failMethod = "players";
      ShadowOneSignalRestClient.failHttpCode = 403;
      ShadowOneSignalRestClient.setNextFailureJSONResponse(new JSONObject() {{
         put("tags", "error");
      }});

      // Should fail because players call failed with tags
      OneSignal.setLanguage("fr", handler);
      threadAndTaskWait();

      assertTrue(handler.getFailed());
   }

   /**
    * Similar workflow to testLocationPermissionPromptWithPrivacyConsent()
    * We want to provide consent but make sure that session time tracking works properly
    */
   @Test
   @Config(sdk = 26)
   public void testSessionTimeTrackingOnPrivacyConsent() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);
      time.advanceSystemAndElapsedTimeBy(0);
      OneSignalInit();
      threadAndTaskWait();

      // Provide consent to OneSignal SDK amd forward time 2 minutes
      OneSignal.provideUserConsent(true);
      // TODO: This passed without a resume here, why?
      blankActivityController.resume();

      time.advanceSystemAndElapsedTimeBy(2 * 60);
      pauseActivity(blankActivityController);
      assertAndRunSyncService();

      // Check that time is tracked successfully by validating the "on_focus" endpoint
      assertRestCalls(3);
      assertOnFocusAtIndex(2, 120);
   }

   @Test
   public void gdprUserConsent() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);
      assertTrue(OneSignalPackagePrivateHelper.OneSignal_requiresUserPrivacyConsent());

      //privacy consent state should still be set to true (user consent required)
      OneSignalInit();
      threadAndTaskWait();

      //the delayed params should now be set
      assertNotNull(OneSignalPackagePrivateHelper.OneSignal_delayedInitParams());
      assertNull(OneSignalPackagePrivateHelper.OneSignal_appId());

      //test to make sure methods, such as PostNotification, don't execute without user consent
      OneSignal.PostNotificationResponseHandler handler = new OneSignal.PostNotificationResponseHandler() {
         @Override
         public void onSuccess(JSONObject response) {
            postNotificationSuccess = response;
         }

         @Override
         public void onFailure(JSONObject response) {
            postNotificationFailure = response;
         }
      };

      OneSignal.postNotification("{}", handler);
      threadAndTaskWait();
      assertNull(postNotificationSuccess);
      assertNull(postNotificationFailure);
      postNotificationSuccess = postNotificationFailure = null;

      OneSignal.provideUserConsent(true);

      assertNull(OneSignalPackagePrivateHelper.OneSignal_delayedInitParams());
      assertNotNull(OneSignalPackagePrivateHelper.OneSignal_appId());

      // Not testing input here, just that HTTP 200 fires a success.
      OneSignal.postNotification("{}", handler);
      threadAndTaskWait();
      assertNotNull(postNotificationSuccess);
      assertNull(postNotificationFailure);
      postNotificationSuccess = postNotificationFailure = null;
   }

   @Test
   public void gdprRevokeUserConsent() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);

      //privacy consent state should still be set to true (user consent required)
      OneSignalInit();

      OneSignal.provideUserConsent(true);

      threadAndTaskWait();

      OneSignal.provideUserConsent(false);

      threadAndTaskWait();

      //test to make sure methods, such as PostNotification, don't execute without user consent
      OneSignal.PostNotificationResponseHandler handler = new OneSignal.PostNotificationResponseHandler() {
         @Override
         public void onSuccess(JSONObject response) {
            postNotificationSuccess = response;
         }

         @Override
         public void onFailure(JSONObject response) {
            postNotificationFailure = response;
         }
      };

      OneSignal.postNotification("{}", handler);
      threadAndTaskWait();
      assertNull(postNotificationSuccess);
      assertNull(postNotificationFailure);
      postNotificationSuccess = postNotificationFailure = null;
   }

   @Test
   public void shouldReturnCorrectConsentRequiredStatus() throws JSONException {
      ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);

      OneSignalInit();

      assertTrue(OneSignal.requiresUserPrivacyConsent());

      OneSignal.provideUserConsent(true);

      assertFalse(OneSignal.requiresUserPrivacyConsent());
   }

   @Test
   public void shouldReturnCorrectConsentRequiredStatusWhenSetBeforeInit() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);
      OneSignal.provideUserConsent(true);
      OneSignalInit();
      threadAndTaskWait();

      assertTrue(OneSignal.userProvidedPrivacyConsent());

      fastColdRestartApp();
      OneSignalInit();
      threadAndTaskWait();

      assertTrue(OneSignal.userProvidedPrivacyConsent());
   }

   // Functions to add observers (like addSubscriptionObserver) should continue
   // to work even if privacy consent has not been granted.
   @Test
   public void shouldAddSubscriptionObserverIfConsentNotGranted() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);
      OneSignalInit();
      threadAndTaskWait();

      OSSubscriptionObserver subscriptionObserver = stateChanges -> {
         lastSubscriptionStateChanges = stateChanges;
         currentSubscription = stateChanges.getTo().isSubscribed();
      };
      OneSignal.addSubscriptionObserver(subscriptionObserver);
      lastSubscriptionStateChanges = null;
      // Make sure garbage collection doesn't nuke any observers.
      Runtime.getRuntime().gc();

      OneSignal.provideUserConsent(true);
      threadAndTaskWait();

      // make sure the subscription observer was fired
      assertTrue(lastSubscriptionStateChanges.getTo().isSubscribed());
      assertFalse(lastSubscriptionStateChanges.getFrom().isSubscribed());
   }

   @Test
   public void shouldAddPermissionObserverIfConsentNotGranted() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);
      OneSignalInit();
      threadAndTaskWait();

      OSPermissionObserver permissionObserver = new OSPermissionObserver() {
         @Override
         public void onOSPermissionChanged(OSPermissionStateChanges stateChanges) {
            lastPermissionStateChanges = stateChanges;
            currentPermission = stateChanges.getTo().areNotificationsEnabled();
         }
      };
      OneSignal.addPermissionObserver(permissionObserver);

      OneSignal.provideUserConsent(true);
      threadAndTaskWait();

      // make sure the permission observer was fired
      assertFalse(lastPermissionStateChanges.getFrom().areNotificationsEnabled());
      assertTrue(lastPermissionStateChanges.getTo().areNotificationsEnabled());
   }

   @Test
   public void shouldAddEmailSubscriptionObserverIfConsentNotGranted() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);
      OneSignalInit();
      OSEmailSubscriptionObserver subscriptionObserver = new OSEmailSubscriptionObserver() {
         @Override
         public void onOSEmailSubscriptionChanged(OSEmailSubscriptionStateChanges stateChanges) {
            lastEmailSubscriptionStateChanges = stateChanges;
         }
      };
      OneSignal.addEmailSubscriptionObserver(subscriptionObserver);

      OneSignal.provideUserConsent(true);
      threadAndTaskWait();

      String email = "josh@onesignal.com";
      OneSignal.setEmail(email);
      threadAndTaskWait();

      // make sure the email subscription observer was fired
      assertEquals(email, lastEmailSubscriptionStateChanges.getFrom().getEmailAddress());
      assertNull(lastEmailSubscriptionStateChanges.getFrom().getEmailUserId());
      assertEquals("b007f967-98cc-11e4-bed1-118f05be4522", lastEmailSubscriptionStateChanges.getTo().getEmailUserId());
      assertEquals(email, lastEmailSubscriptionStateChanges.getTo().getEmailAddress());
      assertTrue(lastEmailSubscriptionStateChanges.getTo().isSubscribed());
   }

   @Test
   public void shouldAddSMSSubscriptionObserverIfConsentNotGranted() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);
      OneSignalInit();
      threadAndTaskWait();

      OSSMSSubscriptionObserver subscriptionObserver = stateChanges -> lastSMSSubscriptionStateChanges = stateChanges;
      OneSignal.addSMSSubscriptionObserver(subscriptionObserver);

      OneSignal.provideUserConsent(true);
      threadAndTaskWait();

      assertNull(lastSMSSubscriptionStateChanges);

      OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
      threadAndTaskWait();

      // make sure the sms subscription observer was fired
      assertEquals(ONESIGNAL_SMS_NUMBER, lastSMSSubscriptionStateChanges.getFrom().getSMSNumber());
      assertNull(lastSMSSubscriptionStateChanges.getFrom().getSmsUserId());
      assertFalse(lastSMSSubscriptionStateChanges.getFrom().isSubscribed());
      assertEquals(ShadowOneSignalRestClient.smsUserId, lastSMSSubscriptionStateChanges.getTo().getSmsUserId());
      assertEquals(ONESIGNAL_SMS_NUMBER, lastSMSSubscriptionStateChanges.getTo().getSMSNumber());
      assertTrue(lastSMSSubscriptionStateChanges.getTo().isSubscribed());
   }

   /*
   // Can't get test to work from a app flow due to the main thread being locked one way or another in a robolectric env.
   // Running ActivityLifecycleListener.focusHandlerThread...advanceToNextPostedRunnable waits on the main thread.
   //    If it is put in its own thread then synchronized that is run when messages a runnable is added / removed hangs the main thread here too.
   @Test
   public void shouldNotDoubleCountFocusTime() throws Exception {
      System.out.println("TEST IS RUNNING ONE THREAD: " + Thread.currentThread());

      // Start app normally
      OneSignalInit();
      threadAndTaskWait();

      // Press home button after 30 sec
      blankActivityController.resume();
      advanceSystemTimeBy(30 * 1_000L);
      blankActivityController.pause();
      threadAndTaskWait();

      // Press home button after 30 more sec, with a network hang
      blankActivityController.resume();
      advanceSystemTimeBy(60 * 1_000L);
      ShadowOneSignalRestClient.interruptibleDelayNext = true;
      blankActivityController.pause();
      System.out.println("HERE1");
      threadAndTaskWait();
      System.out.println("HERE2"  + Thread.currentThread());

      // Open app and press home button again right away.
      blankActivityController.resume();
      System.out.println("HERE3: " + Thread.currentThread());
      blankActivityController.pause();
      System.out.println("HERE4");
      threadAndTaskWait();
      System.out.println("HERE5");

      ShadowOneSignalRestClient.interruptHTTPDelay();
      System.out.println("HERE6");

      threadWait();
      System.out.println("ShadowOneSignalRestClient.lastPost: " + ShadowOneSignalRestClient.lastPost);
      System.out.println("ShadowOneSignalRestClient.networkCallCount: " + ShadowOneSignalRestClient.networkCallCount);

      assertEquals(60, ShadowOneSignalRestClient.lastPost.getInt("active_time"));
      assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
   }
   */

   // ####### Unit test postNotification #####

   private static JSONObject postNotificationSuccess = null, postNotificationFailure = null;

   @Test
   public void testPostNotification() throws Exception {
      OneSignalInit();

      OneSignal.PostNotificationResponseHandler handler = new OneSignal.PostNotificationResponseHandler() {
         @Override
         public void onSuccess(JSONObject response) {
            postNotificationSuccess = response;
         }

         @Override
         public void onFailure(JSONObject response) {
            postNotificationFailure = response;
         }
      };

      // Not testing input here, just that HTTP 200 fires a success.
      OneSignal.postNotification("{}", handler);
      threadAndTaskWait();
      assertNotNull(postNotificationSuccess);
      assertNull(postNotificationFailure);
      postNotificationSuccess = postNotificationFailure = null;

      ShadowOneSignalRestClient.setNextSuccessfulJSONResponse(new JSONObject() {{
         put("id", "");
         put("recipients", 0);
         put("errors", new JSONArray() {{
            put("All included players are not subscribed");
         }});
      }});

      OneSignal.postNotification("{}", handler);
      assertNull(postNotificationSuccess);
      assertNotNull(postNotificationFailure);
   }

   @Test
   @Config(shadows = { ShadowRoboNotificationManager.class, ShadowBadgeCountUpdater.class, ShadowGenerateNotification.class })
   public void shouldCancelAndClearNotifications() throws Exception {
      ShadowRoboNotificationManager.notifications.clear();
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity.getApplicationContext());
      threadAndTaskWait();

      // Create 2 notifications
      Bundle bundle = getBaseNotifBundle();
      OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle);
      bundle = getBaseNotifBundle("UUID2");
      OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromFCMIntentService(blankActivity, bundle);
      threadAndTaskWait();

      // Test canceling
      Map<Integer, ShadowRoboNotificationManager.PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, ShadowRoboNotificationManager.PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
      ShadowRoboNotificationManager.PostedNotification postedNotification = postedNotifsIterator.next().getValue();

      OneSignal.removeNotification(postedNotification.id);
      threadAndTaskWait();
      assertEquals(1, ShadowBadgeCountUpdater.lastCount);
      assertEquals(1, ShadowRoboNotificationManager.notifications.size());

      OneSignal.clearOneSignalNotifications();
      threadAndTaskWait();
      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
      assertEquals(0, ShadowRoboNotificationManager.notifications.size());

      // Make sure they are marked dismissed.
      Cursor cursor = dbHelper.query(OneSignalPackagePrivateHelper.NotificationTable.TABLE_NAME, new String[] { "created_time" },
          OneSignalPackagePrivateHelper.NotificationTable.COLUMN_NAME_DISMISSED + " = 1", null, null, null, null);
      assertEquals(2, cursor.getCount());
      cursor.close();
   }

   // ####### Unit test toJSONObject methods
   @Test
   public void testOSNotificationToJSONObject() throws Exception {
      OSNotification osNotification = createTestOSNotification();

      JSONObject testJsonObj = osNotification.toJSONObject();

      assertEquals("msg_body", testJsonObj.optString("body"));
      JSONObject firstActionButton = (JSONObject)testJsonObj.optJSONArray("actionButtons").get(0);
      assertEquals("text", firstActionButton.optString("text"));

      JSONObject additionalData = testJsonObj.optJSONObject("additionalData");
      assertEquals("bar", additionalData.optString("foo"));
   }

   @Test
   public void testOSNotificationOpenResultToJSONObject() throws Exception {
      OSNotificationAction action = new OSNotificationAction(OSNotificationAction.ActionType.Opened, null);
      OSNotificationOpenedResult osNotificationOpenedResult = new OSNotificationOpenedResult(createTestOSNotification(), action);

      JSONObject testJsonObj = osNotificationOpenedResult.toJSONObject();

      JSONObject additionalData = testJsonObj.optJSONObject("notification").optJSONObject("additionalData");
      assertEquals("bar", additionalData.optString("foo"));

      JSONObject firstGroupedNotification = (JSONObject)testJsonObj.optJSONObject("notification").optJSONArray("groupedNotifications").get(0);
      assertEquals("collapseId1", firstGroupedNotification.optString("collapseId"));
   }

   // ####### Unit test JSONUtils methods
   @Test
   public void test_JSONUtils_toUnescapedEUIDString() throws Exception {
      // 1. Test when EUID is first in the json, and has ($) and (/), and ($) elsewhere

      // Set up the JSONObject to test with
      String jsonStringWithDollarAndSlash = "{" +
              "\"external_user_id\":\"$1$/abc/de$f/\"," +
              "\"app_id\":\"b4f7f966-d8cc-11e4-bed1-df8f05be55ba\"," +
              "\"timezone\":\"$Europe/London\"" +
              "}";
      JSONObject jsonWithDollarAndSlash = new JSONObject(jsonStringWithDollarAndSlash);

      // The expected string which escapes the "timezone" slash (/) only
      String expected_jsonStringWithDollarAndSlash = "{" +
              "\"external_user_id\":\"$1$/abc/de$f/\"," +
              "\"app_id\":\"b4f7f966-d8cc-11e4-bed1-df8f05be55ba\"," +
              "\"timezone\":\"$Europe\\/London\"" +
              "}";

      // The actual string result from calling JSONUtils.toUnescapedEUIDString()
      String actual_jsonStringWithDollarAndSlash = toUnescapedEUIDString(jsonWithDollarAndSlash);

      // These two strings should be equal
      assertEquals(expected_jsonStringWithDollarAndSlash, actual_jsonStringWithDollarAndSlash);

      // 2. Test when EUID is first in the json, and has no dollar nor slash

      String jsonStringWithEUID = "{" +
              "\"external_user_id\":\"123abc\"," +
              "\"app_id\":\"b4f7f966-d8cc-11e4-bed1-df8f05be55ba\"," +
              "\"timezone\":\"$Europe/London\"" +
              "}";
      JSONObject jsonWithEUID = new JSONObject(jsonStringWithEUID);

      String expected_jsonStringWithEUID = "{" +
              "\"external_user_id\":\"123abc\"," +
              "\"app_id\":\"b4f7f966-d8cc-11e4-bed1-df8f05be55ba\"," +
              "\"timezone\":\"$Europe\\/London\"" +
              "}";

      String actual_jsonStringWithEUID = toUnescapedEUIDString(jsonWithEUID);

      assertEquals(expected_jsonStringWithEUID, actual_jsonStringWithEUID);

      // 3. Test when there is no EUID is in the json

      String jsonStringWithoutEUID = "{" +
              "\"app_id\":\"b4f7f966-d8cc-11e4-bed1-df8f05be55ba\"," +
              "\"timezone\":\"Europe/London\"" +
              "}";
      JSONObject jsonWithoutEUID = new JSONObject(jsonStringWithoutEUID);

      String expected_jsonStringWithoutEUID = "{" +
              "\"app_id\":\"b4f7f966-d8cc-11e4-bed1-df8f05be55ba\"," +
              "\"timezone\":\"Europe\\/London\"" +
              "}";

      String actual_jsonStringWithoutEUID = toUnescapedEUIDString(jsonWithoutEUID);

      assertEquals(expected_jsonStringWithoutEUID, actual_jsonStringWithoutEUID);
   }

   @Test
   public void testNotificationOpenedProcessorHandlesEmptyIntent() {
      NotificationOpenedProcessor_processFromContext(blankActivity, new Intent());
   }

   @Test
   public void shouldHandleChromeNullNewSession() throws Exception {
      ShadowCustomTabsClient.nullNewSession = true;
      OneSignalInit();
      threadAndTaskWait();
   }

   private OSPermissionStateChanges lastPermissionStateChanges;
   private boolean currentPermission;
   // Firing right away to match iOS behavior for wrapper SDKs.
   @Test
   public void shouldFirePermissionObserverOnFirstAdd() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      OSPermissionObserver permissionObserver = new OSPermissionObserver() {
         @Override
         public void onOSPermissionChanged(OSPermissionStateChanges stateChanges) {
            lastPermissionStateChanges = stateChanges;
            currentPermission = stateChanges.getTo().areNotificationsEnabled();
         }
      };
      OneSignal.addPermissionObserver(permissionObserver);

      assertFalse(lastPermissionStateChanges.getFrom().areNotificationsEnabled());
      assertTrue(lastPermissionStateChanges.getTo().areNotificationsEnabled());
      // Test to make sure object was correct at the time of firing.
      assertTrue(currentPermission);
   }

   @Test
   public void shouldFirePermissionObserverWhenUserDisablesNotifications() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(new JSONObject()
              .put("unsubscribe_on_notifications_disabled", false)
      );
      OneSignalInit();
      threadAndTaskWait();

      OSPermissionObserver permissionObserver = stateChanges -> {
         lastPermissionStateChanges = stateChanges;
         currentPermission = stateChanges.getTo().areNotificationsEnabled();
      };
      OneSignal.addPermissionObserver(permissionObserver);
      lastPermissionStateChanges = null;
      // Make sure garbage collection doesn't nuke any observers.
      Runtime.getRuntime().gc();

      stopActivity(blankActivityController);
      ShadowNotificationManagerCompat.enabled = false;
      blankActivityController.resume();

      assertTrue(lastPermissionStateChanges.getFrom().areNotificationsEnabled());
      assertFalse(lastPermissionStateChanges.getTo().areNotificationsEnabled());
      // Test to make sure object was correct at the time of firing.
      assertFalse(currentPermission);
      // unsubscribeWhenNotificationsAreDisabled is not set so don't send notification_types.
      assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void shouldSetNotificationTypesToZeroWhenUnsubscribeWhenNotificationsAreDisabledIsEnabled() throws Exception {
      ShadowNotificationManagerCompat.enabled = false;
      ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(new JSONObject()
              .put("unsubscribe_on_notifications_disabled", true)
      );
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();

      assertEquals(0, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      pauseActivity(blankActivityController);
      ShadowNotificationManagerCompat.enabled = true;
      blankActivityController.resume();
      threadAndTaskWait();
      assertEquals(1, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
   }

   @Test
   @Config(shadows = {ShadowBadgeCountUpdater.class})
   public void shouldClearBadgesWhenPermissionIsDisabled() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      ShadowBadgeCountUpdater.lastCount = 1;

      pauseActivity(blankActivityController);
      ShadowNotificationManagerCompat.enabled = false;
      blankActivityController.resume();
      threadAndTaskWait();

      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
   }

   private OSSubscriptionStateChanges lastSubscriptionStateChanges;
   private boolean currentSubscription;
   @Test
   public void shouldFireSubscriptionObserverOnFirstAdd() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      OSSubscriptionObserver permissionObserver = new OSSubscriptionObserver() {
         @Override
         public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
            lastSubscriptionStateChanges = stateChanges;
            currentSubscription = stateChanges.getTo().isSubscribed();
         }
      };
      OneSignal.addSubscriptionObserver(permissionObserver);

      assertFalse(lastSubscriptionStateChanges.getFrom().isSubscribed());
      assertTrue(lastSubscriptionStateChanges.getTo().isSubscribed());
      // Test to make sure object was correct at the time of firing.
      assertTrue(currentSubscription);
   }

   @Test
   public void shouldFireSubscriptionObserverWhenUserDisablesNotifications() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(new JSONObject()
              .put("unsubscribe_on_notifications_disabled", false)
      );
      OneSignalInit();
      threadAndTaskWait();

      OSSubscriptionObserver subscriptionObserver = new OSSubscriptionObserver() {
         @Override
         public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
            lastSubscriptionStateChanges = stateChanges;
            currentSubscription = stateChanges.getTo().isSubscribed();
         }
      };
      OneSignal.addSubscriptionObserver(subscriptionObserver);
      lastSubscriptionStateChanges = null;
      // Make sure garbage collection doesn't nuke any observers.
      Runtime.getRuntime().gc();

      pauseActivity(blankActivityController);
      ShadowNotificationManagerCompat.enabled = false;
      blankActivityController.resume();
      threadAndTaskWait();

      assertTrue(lastSubscriptionStateChanges.getFrom().isSubscribed());
      assertFalse(lastSubscriptionStateChanges.getTo().isSubscribed());
      // Test to make sure object was correct at the time of firing.
      assertFalse(currentSubscription);
      // unsubscribeWhenNotificationsAreDisabled is not set so don't send notification_types.
      assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void shouldFireSubscriptionObserverWhenChangesHappen() throws Exception {
      OneSignalInit();
      OSSubscriptionObserver permissionObserver = new OSSubscriptionObserver() {
         @Override
         public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
            lastSubscriptionStateChanges = stateChanges;
            currentSubscription = stateChanges.getTo().isSubscribed();
         }
      };
      OneSignal.addSubscriptionObserver(permissionObserver);
      threadAndTaskWait();

      assertFalse(lastSubscriptionStateChanges.getFrom().isSubscribed());
      assertTrue(lastSubscriptionStateChanges.getTo().isSubscribed());
      // Test to make sure object was correct at the time of firing.
      assertTrue(currentSubscription);
      assertFalse(lastSubscriptionStateChanges.getTo().isPushDisabled());
      assertEquals(ShadowPushRegistratorFCM.regId, lastSubscriptionStateChanges.getTo().getPushToken());
      assertEquals(ShadowOneSignalRestClient.pushUserId, lastSubscriptionStateChanges.getTo().getUserId());
   }

   @Test
   public void shouldNotFireSubscriptionObserverWhenChangesHappenIfRemoved() throws Exception {
      OneSignalInit();
      OSSubscriptionObserver permissionObserver = new OSSubscriptionObserver() {
         @Override
         public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
            lastSubscriptionStateChanges = stateChanges;
            currentSubscription = stateChanges.getTo().isSubscribed();
         }
      };
      OneSignal.addSubscriptionObserver(permissionObserver);
      lastSubscriptionStateChanges = null;
      OneSignal.removeSubscriptionObserver(permissionObserver);
      threadAndTaskWait();

      assertFalse(currentSubscription);
      assertNull(lastSubscriptionStateChanges);
   }

   @Test
   public void shouldFireEmailSubscriptionObserverOnSetEmail() throws Exception {
      OneSignalInit();
      OSEmailSubscriptionObserver subscriptionObserver = new OSEmailSubscriptionObserver() {
         @Override
         public void onOSEmailSubscriptionChanged(OSEmailSubscriptionStateChanges stateChanges) {
            lastEmailSubscriptionStateChanges = stateChanges;
         }
      };
      OneSignal.addEmailSubscriptionObserver(subscriptionObserver);
      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      assertNull(lastEmailSubscriptionStateChanges.getFrom().getEmailUserId());
      assertEquals("b007f967-98cc-11e4-bed1-118f05be4522", lastEmailSubscriptionStateChanges.getTo().getEmailUserId());
      assertEquals("josh@onesignal.com", lastEmailSubscriptionStateChanges.getTo().getEmailAddress());
      assertTrue(lastEmailSubscriptionStateChanges.getTo().isSubscribed());
   }

   @Test
   public void shouldFireSMSSubscriptionObserverOnSetSMS() throws Exception {
      OneSignalInit();
      OSSMSSubscriptionObserver subscriptionObserver = stateChanges -> lastSMSSubscriptionStateChanges = stateChanges;
      OneSignal.addSMSSubscriptionObserver(subscriptionObserver);
      OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
      threadAndTaskWait();

      assertNull(lastSMSSubscriptionStateChanges.getFrom().getSmsUserId());
      assertEquals(SMS_USER_ID, lastSMSSubscriptionStateChanges.getTo().getSmsUserId());
      assertEquals(ONESIGNAL_SMS_NUMBER, lastSMSSubscriptionStateChanges.getTo().getSMSNumber());
      assertTrue(lastSMSSubscriptionStateChanges.getTo().isSubscribed());
   }

   @Test
   public void shouldFireEmailSubscriptionObserverOnLogoutEmail() throws Exception {
      OneSignalInit();
      OSEmailSubscriptionObserver subscriptionObserver = new OSEmailSubscriptionObserver() {
         @Override
         public void onOSEmailSubscriptionChanged(OSEmailSubscriptionStateChanges stateChanges) {
            lastEmailSubscriptionStateChanges = stateChanges;
         }
      };
      OneSignal.addEmailSubscriptionObserver(subscriptionObserver);
      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      OneSignal.logoutEmail();
      threadAndTaskWait();

      assertEquals("b007f967-98cc-11e4-bed1-118f05be4522", lastEmailSubscriptionStateChanges.getFrom().getEmailUserId());
      assertEquals("josh@onesignal.com", lastEmailSubscriptionStateChanges.getFrom().getEmailAddress());

      assertFalse(lastEmailSubscriptionStateChanges.getTo().isSubscribed());
      assertNull(lastEmailSubscriptionStateChanges.getTo().getEmailUserId());
      assertNull(lastEmailSubscriptionStateChanges.getTo().getEmailAddress());
   }

   @Test
   public void shouldNotFireEmailSubscriptionObserverOnAppRestart() throws Exception {
      OneSignalInit();
      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      OSEmailSubscriptionObserver subscriptionObserver = new OSEmailSubscriptionObserver() {
         @Override
         public void onOSEmailSubscriptionChanged(OSEmailSubscriptionStateChanges stateChanges) {
            lastEmailSubscriptionStateChanges = stateChanges;
         }
      };
      OneSignal.addEmailSubscriptionObserver(subscriptionObserver);
      threadAndTaskWait();
      assertNotNull(lastEmailSubscriptionStateChanges);

      restartAppAndElapseTimeToNextSession(time);

      OneSignalInit();
      threadAndTaskWait();
      lastEmailSubscriptionStateChanges = null;
      OneSignal.addEmailSubscriptionObserver(subscriptionObserver);
      threadAndTaskWait();

      assertNull(lastEmailSubscriptionStateChanges);
   }

   @Test
   public void shouldNotFireSMSSubscriptionObserverOnAppRestart() throws Exception {
      OneSignalInit();
      OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
      threadAndTaskWait();

      OSSMSSubscriptionObserver subscriptionObserver = stateChanges -> lastSMSSubscriptionStateChanges = stateChanges;
      OneSignal.addSMSSubscriptionObserver(subscriptionObserver);
      threadAndTaskWait();
      assertNotNull(lastSMSSubscriptionStateChanges);

      restartAppAndElapseTimeToNextSession(time);
      lastSMSSubscriptionStateChanges = null;

      OneSignalInit();
      threadAndTaskWait();
      OneSignal.addSMSSubscriptionObserver(subscriptionObserver);
      threadAndTaskWait();

      assertNull(lastSMSSubscriptionStateChanges);
   }

   @Test
   public void shouldNotThrowWhenRemovingWeakReferenceObservableThatHasBeenGarbageCollected() {
      OSObservable<Object, Object> observer = new OSObservable<>("", false);
      WeakReference<Object> weakObject = new WeakReference<>(new Object());
      observer.addObserver(weakObject.get());
      Runtime.getRuntime().gc(); // Force cleaning up WeakReference above
      observer.removeObserver(weakObject.get());
   }

   @Test
   public void shouldGetCorrectCurrentEmailSubscriptionState() throws Exception {
      OneSignalInit();
      OSDeviceState deviceState = OneSignal.getDeviceState();

      assertNotNull(deviceState);
      assertNull(deviceState.getEmailUserId());
      assertNull(deviceState.getEmailAddress());
      assertFalse(deviceState.isEmailSubscribed());

      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();
      deviceState = OneSignal.getDeviceState();

      assertEquals("b007f967-98cc-11e4-bed1-118f05be4522", deviceState.getEmailUserId());
      assertEquals("josh@onesignal.com", deviceState.getEmailAddress());
      assertTrue(deviceState.isEmailSubscribed());
   }

   @Test
   public void shouldGetCorrectCurrentSMSSubscriptionState() throws Exception {
      OneSignalInit();
      OSDeviceState deviceState = OneSignal.getDeviceState();

      assertNotNull(deviceState);
      assertNull(deviceState.getSMSUserId());
      assertNull(deviceState.getSMSNumber());
      assertFalse(deviceState.isSMSSubscribed());

      OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
      threadAndTaskWait();
      deviceState = OneSignal.getDeviceState();

      assertEquals(SMS_USER_ID, deviceState.getSMSUserId());
      assertEquals(ONESIGNAL_SMS_NUMBER, deviceState.getSMSNumber());
      assertTrue(deviceState.isSMSSubscribed());
   }

   @Test
   public void shouldGetEmailUserIdAfterAppRestart() throws Exception {
      OneSignalInit();
      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      restartAppAndElapseTimeToNextSession(time);

      OneSignalInit();
      OSDeviceState deviceState = OneSignal.getDeviceState();
      assertEquals("josh@onesignal.com", deviceState.getEmailAddress());
      assertNotNull(deviceState.getEmailUserId());
   }

   @Test
   public void shouldGetSMSUserIdAfterAppRestart() throws Exception {
      OneSignalInit();
      OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
      threadAndTaskWait();

      restartAppAndElapseTimeToNextSession(time);

      OneSignalInit();
      OSDeviceState deviceState = OneSignal.getDeviceState();
      assertEquals(ONESIGNAL_SMS_NUMBER, deviceState.getSMSNumber());
      assertNotNull(deviceState.getSMSUserId());
   }

   @Test
   public void shouldReturnCorrectGetPermissionSubscriptionState() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      OSDeviceState deviceState = OneSignal.getDeviceState();
      assertTrue(deviceState.areNotificationsEnabled());
      assertTrue(deviceState.isSubscribed());
   }

   @Test
   public void testDeviceStateHasEmailAddress() throws Exception {
      String testEmail = "test@onesignal.com";

      assertNull(OneSignal.getDeviceState());

      OneSignalInit();
      threadAndTaskWait();

      OSDeviceState device = OneSignal.getDeviceState();
      assertNull(device.getEmailAddress());

      OneSignal.setEmail(testEmail);
      threadAndTaskWait();

      // Device is a snapshot, last value should not change
      assertNull(device.getEmailAddress());
      // Retrieve new user device
      assertEquals(testEmail, OneSignal.getDeviceState().getEmailAddress());
   }

   @Test
   public void testDeviceStateHasSMSAddress() throws Exception {
      assertNull(OneSignal.getDeviceState());

      OneSignalInit();
      threadAndTaskWait();

      OSDeviceState device = OneSignal.getDeviceState();
      assertNull(device.getSMSNumber());

      OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
      threadAndTaskWait();

      // Device is a snapshot, last value should not change
      assertNull(device.getSMSNumber());
      // Retrieve new user device
      assertEquals(ONESIGNAL_SMS_NUMBER, OneSignal.getDeviceState().getSMSNumber());
   }

   @Test
   public void testDeviceStateHasEmailId() throws Exception {
      String testEmail = "test@onesignal.com";

      assertNull(OneSignal.getDeviceState());

      OneSignalInit();
      threadAndTaskWait();

      OSDeviceState device = OneSignal.getDeviceState();
      assertNull(device.getEmailUserId());

      OneSignal.setEmail(testEmail);
      threadAndTaskWait();

      // Device is a snapshot, last value should not change
      assertNull(device.getEmailUserId());
      // Retrieve new user device
      assertNotNull(OneSignal.getDeviceState().getEmailUserId());
   }

   @Test
   public void testDeviceStateHasSMSId() throws Exception {
      assertNull(OneSignal.getDeviceState());

      OneSignalInit();
      threadAndTaskWait();

      OSDeviceState device = OneSignal.getDeviceState();
      assertNull(device.getSMSUserId());

      OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
      threadAndTaskWait();

      // Device is a snapshot, last value should not change
      assertNull(device.getSMSUserId());
      // Retrieve new user device
      assertEquals(SMS_USER_ID, OneSignal.getDeviceState().getSMSUserId());
   }

   @Test
   public void testDeviceStateHasUserId() throws Exception {
      assertNull(OneSignal.getDeviceState());

      OneSignalInit();
      threadAndTaskWait();

      assertNotNull(OneSignal.getDeviceState().getUserId());
   }

   @Test
   public void testDeviceStateHasPushToken() throws Exception {
      assertNull(OneSignal.getDeviceState());

      OneSignalInit();
      threadAndTaskWait();

      assertNotNull(OneSignal.getDeviceState().getPushToken());
   }

   @Test
   public void testDeviceStateAreNotificationsEnabled() throws Exception {
      assertNull(OneSignal.getDeviceState());

      OneSignalInit();
      threadAndTaskWait();

      OSDeviceState device = OneSignal.getDeviceState();
      assertTrue(device.areNotificationsEnabled());

      fastColdRestartApp();

      ShadowNotificationManagerCompat.enabled = false;

      OneSignalInit();
      threadAndTaskWait();

      // Device is a snapshot, last value should not change
      assertTrue(device.areNotificationsEnabled());
      // Retrieve new user device
      assertFalse(OneSignal.getDeviceState().areNotificationsEnabled());
   }

   @Test
   public void testDeviceStateIsPushDisabled() throws Exception {
      assertNull(OneSignal.getDeviceState());

      OneSignalInit();
      threadAndTaskWait();

      assertFalse(OneSignal.getDeviceState().isPushDisabled());
   }

   @Test
   public void testDeviceStateIsSubscribed() throws Exception {
      assertNull(OneSignal.getDeviceState());

      OneSignalInit();
      threadAndTaskWait();

      OSDeviceState device = OneSignal.getDeviceState();
      assertTrue(device.isSubscribed());

      fastColdRestartApp();

      ShadowNotificationManagerCompat.enabled = false;

      OneSignalInit();
      threadAndTaskWait();

      // Device is a snapshot, last value should not change
      assertTrue(device.isSubscribed());
      // Retrieve new user device
      assertFalse(OneSignal.getDeviceState().isSubscribed());
   }

   @Test
   public void shouldSendPurchases() throws Exception {
      OneSignalInit();
      OneSignal.setEmail("josh@onesignal.com");
      OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
      threadAndTaskWait();

      JSONObject purchase = new JSONObject();
      purchase.put("sku", "com.test.sku");
      JSONArray purchases = new JSONArray();
      purchases.put(purchase);

      OneSignalPackagePrivateHelper.OneSignal_sendPurchases(purchases, false, null);
      threadAndTaskWait();

      String expectedPayload = "{\"app_id\":\"b4f7f966-d8cc-11e4-bed1-df8f05be55ba\",\"purchases\":[{\"sku\":\"com.test.sku\"}]}";
      ShadowOneSignalRestClient.Request pushPurchase = ShadowOneSignalRestClient.requests.get(5);
      assertEquals("players/" + PUSH_USER_ID + "/on_purchase", pushPurchase.url);
      assertEquals(expectedPayload, pushPurchase.payload.toString());

      ShadowOneSignalRestClient.Request emailPurchase = ShadowOneSignalRestClient.requests.get(6);
      assertEquals("players/" + EMAIL_USER_ID + "/on_purchase", emailPurchase.url);
      assertEquals(expectedPayload, emailPurchase.payload.toString());

      ShadowOneSignalRestClient.Request smsPurchase = ShadowOneSignalRestClient.requests.get(7);
      assertEquals("players/" + SMS_USER_ID + "/on_purchase", smsPurchase.url);
      assertEquals(expectedPayload, smsPurchase.payload.toString());
   }

   @Test
   @Config(shadows = { ShadowFirebaseAnalytics.class })
   public void shouldSendFirebaseAnalyticsNotificationOpen() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(new JSONObject().put("fba", true));
      OneSignalInit();
      threadAndTaskWait();

      JSONObject openPayload = new JSONObject();
      openPayload.put("title", "Test title");
      openPayload.put("alert", "Test Msg");
      openPayload.put("custom", new JSONObject("{ \"i\": \"UUID\" }"));
      OneSignal_handleNotificationOpen(blankActivity, new JSONArray().put(openPayload), ONESIGNAL_NOTIFICATION_ID);

      assertEquals("os_notification_opened", ShadowFirebaseAnalytics.lastEventString);
      Bundle expectedBundle = new Bundle();
      expectedBundle.putString("notification_id", "UUID");
      expectedBundle.putString("medium", "notification");
      expectedBundle.putString("source", "OneSignal");
      expectedBundle.putString("campaign", "Test title");
      assertEquals(expectedBundle.toString(), ShadowFirebaseAnalytics.lastEventBundle.toString());

      // Assert that another open isn't trigger later when the unprocessed opens are fired
      ShadowFirebaseAnalytics.lastEventString = null;
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationOpenedHandler(getNotificationOpenedHandler());
      assertNull(ShadowFirebaseAnalytics.lastEventString);
   }

   @Test
   @Config(shadows = { ShadowFirebaseAnalytics.class, ShadowGenerateNotification.class })
   public void shouldSendFirebaseAnalyticsNotificationReceived() throws Exception {
      ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(new JSONObject().put("fba", true));
      OneSignalInit();
      threadAndTaskWait();

      JSONObject openPayload = new JSONObject();
      openPayload.put("title", "Test title");
      openPayload.put("alert", "Test Msg");
      openPayload.put("custom", new JSONObject("{ \"i\": \"UUID\" }"));
      NotificationBundleProcessor_Process(blankActivity, false, openPayload);

      assertEquals("os_notification_received", ShadowFirebaseAnalytics.lastEventString);
      Bundle expectedBundle = new Bundle();
      expectedBundle.putString("notification_id", "UUID");
      expectedBundle.putString("medium", "notification");
      expectedBundle.putString("source", "OneSignal");
      expectedBundle.putString("campaign", "Test title");
      assertEquals(expectedBundle.toString(), ShadowFirebaseAnalytics.lastEventBundle.toString());

      // Assert that another receive isn't trigger later when the unprocessed receives are fired
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationWillShowInForegroundHandler(notificationReceivedEvent -> {

      });
      OneSignal.setNotificationOpenedHandler(getNotificationOpenedHandler());
      threadAndTaskWait();

      ShadowFirebaseAnalytics.lastEventString = null;
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      OneSignal.setNotificationOpenedHandler(getNotificationOpenedHandler());
      assertNull(ShadowFirebaseAnalytics.lastEventString);
   }

   @Test
   public void testGetTagsQueuesCallbacks() throws Exception {
      final BlockingQueue<Boolean> queue = new ArrayBlockingQueue<>(2);

      // Allows us to validate that both handlers get executed independently
      class DebugGetTagsHandler implements OneSignal.OSGetTagsHandler {
         @Override
         public void tagsAvailable(JSONObject tags) {
            queue.offer(true);
         }
      }

      DebugGetTagsHandler first = new DebugGetTagsHandler();
      DebugGetTagsHandler second = new DebugGetTagsHandler();

      OneSignalInit();
      threadAndTaskWait();

      OneSignal.sendTag("test", "value");
      threadAndTaskWait();

      OneSignal.getTags(first);
      OneSignal.getTags(second);
      threadAndTaskWait();

      assertTrue(queue.take());
      assertTrue(queue.take());
   }

   @Test
   public void testNestedGetTags() throws Exception {
      final BlockingQueue<Boolean> queue = new ArrayBlockingQueue<>(2);

      // Validates that nested getTags calls won't throw a ConcurrentModificationException
      class DebugGetTagsHandler implements OneSignal.OSGetTagsHandler {
         @Override
         public void tagsAvailable(JSONObject tags) {
            OneSignal.getTags(new OneSignal.OSGetTagsHandler() {
               @Override
               public void tagsAvailable(JSONObject tags) {
                  queue.offer(true);
               }
            });
         }
      }

      OneSignalInit();
      threadAndTaskWait();

      OneSignal.sendTag("test", "value");
      threadAndTaskWait();

      DebugGetTagsHandler first = new DebugGetTagsHandler();
      DebugGetTagsHandler second = new DebugGetTagsHandler();
      OneSignal.getTags(first);
      OneSignal.getTags(second);
      threadAndTaskWait();

      assertTrue(queue.take());
      assertTrue(queue.take());
   }

   /**
    * Created a AndroidManifest with 2 activities, 1 with the orientation config and 1 without
    * Using this AndroidManifest setup to test that a config setting is detectable
    */
   @Test
   public void testAndroidManifestConfigChangeFlags_orientationFlag() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      // Set ActivityInfo.CONFIG_ORIENTATION configChanges flag
      OneSignalShadowPackageManager.configChanges = ActivityInfo.CONFIG_ORIENTATION;

      // Verify BlankActivity has orientation flag
      boolean blankHasFlag = OneSignalPackagePrivateHelper.hasConfigChangeFlag(blankActivity, ActivityInfo.CONFIG_ORIENTATION);
      assertTrue(blankHasFlag);

      // Go to MainActivity
      Intent mainIntent = new Intent(blankActivity, MainActivity.class);
      Activity mainActivity = Robolectric.buildActivity(MainActivity.class).newIntent(mainIntent).create().get();

      // Set no configChanges flags
      OneSignalShadowPackageManager.configChanges = 0;

      // Verify MainActivity has no orientation flag
      boolean mainHasFlag = OneSignalPackagePrivateHelper.hasConfigChangeFlag(mainActivity, ActivityInfo.CONFIG_ORIENTATION);
      assertFalse(mainHasFlag);
   }

   // ####### Unit test log listener ########
   @Test
   public void testAddLogListener() throws Exception {
      final String[] lastMessage = new String[1];
      OneSignal.addLogListener(event -> lastMessage[0] = event.getEntry());

      OneSignal_Log(OneSignal.LOG_LEVEL.DEBUG, "test");
      assertEquals(lastMessage[0], "test");
   }

   @Test
   public void testRemoveLogListener() {
      final String[] lastMessage = new String[1];
      OneSignalLogListener listener = event -> lastMessage[0] = event.getEntry();
      OneSignal.addLogListener(listener);

      OneSignal.removeLogListener(listener);
      OneSignal_Log(OneSignal.LOG_LEVEL.DEBUG, "test");
      assertNull(lastMessage[0]);
   }

   @Test
   public void testNestedAddLogListenerDoesNotThrow() {
      OneSignalLogListener listener2 = event -> OneSignal.addLogListener(event2 -> {});

      OneSignal.addLogListener(listener2);

      OneSignal_Log(OneSignal.LOG_LEVEL.DEBUG, "test");
   }

   @Test
   public void testNestedRemoveLogListenerDoesNotThrow() {
      final OneSignalLogListener[] listener = new OneSignalLogListener[1];

      listener[0] = event -> OneSignal.removeLogListener(listener[0]);
      OneSignal.addLogListener(listener[0]);

      OneSignal_Log(OneSignal.LOG_LEVEL.DEBUG, "test");
   }

   // ####### Unit test helper methods ########

   private static OSNotification createTestOSNotification() throws Exception {
      OSNotification.ActionButton actionButton = new OSNotification.ActionButton("id", "text", null);
      List<OSNotification.ActionButton> actionButtons = new ArrayList<>();
      actionButtons.add(actionButton);

      List<OSNotification> groupedNotifications = new ArrayList<>();

      OSNotification groupedNotification = new OneSignalPackagePrivateHelper.OSTestNotification.OSTestNotificationBuilder()
              .setCollapseId("collapseId1")
              .build();

      groupedNotifications.add(groupedNotification);

      return new OneSignalPackagePrivateHelper.OSTestNotification.OSTestNotificationBuilder()
              .setBody("msg_body")
              .setAdditionalData(new JSONObject("{\"foo\": \"bar\"}"))
              .setActionButtons(actionButtons)
              .setGroupedNotifications(groupedNotifications)
              .build();
   }

   private void OneSignalInit() {
      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
      ShadowOSUtils.subscribableStatus = 1;
      OneSignal_setTime(time);
      OneSignal_setTrackerFactory(trackerFactory);
      OneSignal_setSessionManager(sessionManager);
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      OneSignal.initWithContext(blankActivity);
      blankActivityController.resume();
   }

   // For some reason Roboelctric does not automatically add this when it reads the AndroidManifest.xml
   //    Also it seems it has to be done in the test itself instead of the setup process.
   private static void AddLauncherIntentFilter() {
      Intent launchIntent = new Intent(Intent.ACTION_MAIN);
      launchIntent.setPackage("com.onesignal.example");
      launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
      ResolveInfo resolveInfo = new ResolveInfo();
      resolveInfo.activityInfo = new ActivityInfo();
      resolveInfo.activityInfo.packageName = "com.onesignal.example";
      resolveInfo.activityInfo.name = "MainActivity";

      shadowOf(blankActivity.getPackageManager()).addResolveInfoForIntent(launchIntent, resolveInfo);
   }
}
