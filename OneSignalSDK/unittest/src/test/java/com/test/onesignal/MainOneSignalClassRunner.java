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
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;

import com.onesignal.BuildConfig;
import com.onesignal.OSEmailSubscriptionObserver;
import com.onesignal.OSEmailSubscriptionState;
import com.onesignal.OSEmailSubscriptionStateChanges;
import com.onesignal.OSNotification;
import com.onesignal.OSNotificationAction;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OSNotificationPayload;
import com.onesignal.OSPermissionObserver;
import com.onesignal.OSPermissionStateChanges;
import com.onesignal.OSPermissionSubscriptionState;
import com.onesignal.OSSubscriptionObserver;
import com.onesignal.OSSubscriptionStateChanges;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalDbHelper;
import com.onesignal.ShadowAdvertisingIdProviderGPS;
import com.onesignal.ShadowBadgeCountUpdater;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowFirebaseAnalytics;
import com.onesignal.ShadowGoogleApiClientBuilder;
import com.onesignal.ShadowGoogleApiClientCompatProxy;
import com.onesignal.ShadowFusedLocationApiWrapper;
import com.onesignal.ShadowJobService;
import com.onesignal.ShadowLocationGMS;
import com.onesignal.ShadowLocationUpdateListener;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignal;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.ShadowPushRegistratorGCM;
import com.onesignal.ShadowRoboNotificationManager;
import com.onesignal.StaticResetHelper;
import com.onesignal.SyncJobService;
import com.onesignal.SyncService;
import com.onesignal.example.BlankActivity;
import com.onesignal.OneSignal.ChangeTagsUpdateHandler;

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
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.android.controller.ActivityController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.onesignal.OneSignalPackagePrivateHelper.GcmBroadcastReceiver_processBundle;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_Process;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationOpenedProcessor_processFromContext;
import static com.onesignal.OneSignalPackagePrivateHelper.bundleAsJSONObject;
import static com.test.onesignal.GenerateNotificationRunner.getBaseNotifBundle;

import static com.test.onesignal.TestHelpers.afterTestCleanup;
import static com.test.onesignal.TestHelpers.fastAppRestart;
import static com.test.onesignal.TestHelpers.flushBufferedSharedPrefs;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.Is.is;

import static org.junit.Assert.assertThat;

import static org.robolectric.Shadows.shadowOf;

import static com.onesignal.ShadowOneSignalRestClient.REST_METHOD;

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
// Enable to ensure test order to consistency debug flaky test.
// @FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MainOneSignalClassRunner {

   private static final String ONESIGNAL_APP_ID = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";
   @SuppressLint("StaticFieldLeak")
   private static Activity blankActivity;
   private static String callBackUseId, getCallBackRegId;
   private static String notificationOpenedMessage;
   private static JSONObject lastGetTags;
   private static ActivityController<BlankActivity> blankActivityController;
   
   private static void GetIdsAvailable() {
      OneSignal.idsAvailable(new OneSignal.IdsAvailableHandler() {
         @Override
         public void idsAvailable(String userId, String registrationId) {
            callBackUseId = userId;
            getCallBackRegId = registrationId;
         }
      });
   }

   private static OneSignal.NotificationOpenedHandler getNotificationOpenedHandler() {
      return new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(OSNotificationOpenResult openedResult) {
            notificationOpenedMessage = openedResult.notification.payload.body;
         }
      };
   }

   private static void GetTags() {
      OneSignal.getTags(new OneSignal.GetTagsHandler() {
         @Override
         public void tagsAvailable(JSONObject tags) {
            lastGetTags = tags;
         }
      });
   }

   private static void cleanUp() {
      callBackUseId = getCallBackRegId = null;

      notificationOpenedMessage = null;
      lastGetTags = null;

      TestHelpers.beforeTestInitAndCleanup();
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

      cleanUp();
   }

   @After
   public void afterEachTest() throws Exception {
      afterTestCleanup();
   }

   @AfterClass
   public static void afterEverything() {
      cleanUp();
   }


   @Test
   public void testInitFromApplicationContext() throws Exception {
      // Application.onCreate
      OneSignal.init(RuntimeEnvironment.application, "123456789", ONESIGNAL_APP_ID);
      threadAndTaskWait();
      assertNotNull(ShadowOneSignalRestClient.lastPost);

      ShadowOneSignalRestClient.lastPost = null;
      restartAppAndElapseTimeToNextSession();

      // Restart app, should not send onSession automatically
      OneSignal.init(RuntimeEnvironment.application, "123456789", ONESIGNAL_APP_ID);
      threadAndTaskWait();
      assertNull(ShadowOneSignalRestClient.lastPost);

      // Starting of first Activity should trigger onSession
      blankActivityController.resume();
      threadAndTaskWait();
      assertNotNull(ShadowOneSignalRestClient.lastPost);
   }

   @Test
   public void testOnSessionCalledOnlyOncePer30Sec() throws Exception {
      // Will call create
      ShadowSystemClock.setCurrentTimeMillis(60 * 60 * 1000);
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      assertEquals("players", ShadowOneSignalRestClient.lastUrl);

      // Shouldn't call on_session if just resuming app with a short delay
      blankActivityController.pause();
      threadAndTaskWait();
      ShadowOneSignalRestClient.lastUrl = null;
      blankActivityController.resume();
      threadAndTaskWait();
      assertNull(ShadowOneSignalRestClient.lastUrl);

      // Or when restarting the app quickly.
      ShadowOneSignalRestClient.lastPost = null;
      fastAppRestart();
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      threadAndTaskWait();
      assertTrue(ShadowOneSignalRestClient.lastUrl.matches(".*android_params.js.*"));

      blankActivityController.pause();
      threadAndTaskWait();
      ShadowSystemClock.setCurrentTimeMillis(121 * 60 * 1000);
      ShadowOneSignalRestClient.lastUrl = null;
      blankActivityController.resume();
      threadAndTaskWait();
      assertTrue(ShadowOneSignalRestClient.lastUrl.matches("players/.*/on_session"));
      assertEquals("{\"app_id\":\"b2f7f966-d8cc-11e4-bed1-df8f05be55ba\"}", ShadowOneSignalRestClient.lastPost.toString());
   }

   @Test
   public void testAlwaysUseRemoteProjectNumberOverLocal() throws Exception {
      ShadowSystemClock.setCurrentTimeMillis(60 * 60 * 1000);

      OneSignalInit();
      threadAndTaskWait();
      assertEquals("87654321", ShadowPushRegistratorGCM.lastProjectNumber);

      // A 2nd init call
      OneSignalInit();

      blankActivityController.pause();
      threadAndTaskWait();
      ShadowSystemClock.setCurrentTimeMillis(121 * 60 * 1000);
      blankActivityController.resume();
      threadAndTaskWait();

      // Make sure when we try to register again before our on_session call it is with the remote
      // project number instead of the local one.
      assertEquals("87654321", ShadowPushRegistratorGCM.lastProjectNumber);
   }

   @Test
   public void testPutStillCalledOnChanges() throws Exception {
      // Will call create
      ShadowSystemClock.setCurrentTimeMillis(60 * 60 * 1000);
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      assertEquals("players", ShadowOneSignalRestClient.lastUrl);

      // Shouldn't call on_session if just resuming app with a short delay
      blankActivityController.pause();
      threadAndTaskWait();
      ShadowOneSignalRestClient.lastUrl = null;
      blankActivityController.resume();
      threadAndTaskWait();
      assertNull(ShadowOneSignalRestClient.lastUrl);
      assertEquals(2, ShadowOneSignalRestClient.networkCallCount);

      ShadowOSUtils.carrierName = "test2";

      // Should make PUT call with changes on app restart
      ShadowOneSignalRestClient.lastPost = null;
      fastAppRestart();
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      threadAndTaskWait();

      assertEquals(4, ShadowOneSignalRestClient.networkCallCount);
      GetIdsAvailable();
      assertEquals("players/" + callBackUseId, ShadowOneSignalRestClient.lastUrl);
      assertEquals("{\"carrier\":\"test2\",\"app_id\":\"b2f7f966-d8cc-11e4-bed1-df8f05be55ba\"}", ShadowOneSignalRestClient.lastPost.toString());
   }


   @Test
   public void testPutCallsMadeWhenUserStateChangesOnAppResume() throws Exception {
      // Will call create
      ShadowSystemClock.setCurrentTimeMillis(60 * 60 * 1000);
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      assertEquals("players", ShadowOneSignalRestClient.lastUrl);
   }

   @Test
   public void testOpenFromNotificationWhenAppIsDead() throws Exception {
      OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Robo test message\", \"custom\": { \"i\": \"UUID\" } }]"), false);

      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());

      threadAndTaskWait();

      assertEquals("Robo test message", notificationOpenedMessage);
   }

   @Test
   public void testAndroidParamsProjectNumberOverridesLocal() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      assertThat(ShadowPushRegistratorGCM.lastProjectNumber, not("123456789"));
   }
   
   @Test
   public void testNullProjectNumberSetsErrorType() throws Exception {
      // Get call will not return a Google project number if it hasn't been entered on the OneSignal dashboard.
      ShadowOneSignalRestClient.nextSuccessResponse = "{\"awl_list\": {}}";
      // Don't fire the mock callback, it will be done from the real class.
      ShadowPushRegistratorGCM.skipComplete = true;
      
      OneSignal.init(blankActivity, null, ONESIGNAL_APP_ID);
      threadAndTaskWait();
      
      assertEquals(-6, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
   }
   
   @Test
   @Config(shadows = {ShadowRoboNotificationManager.class}, sdk = 26)
   public void testNotificationChannelListPayload() throws Exception {
      NotificationChannelManagerRunner testHelper = new NotificationChannelManagerRunner().setContext(blankActivity);
      JSONObject payloadList = testHelper.createBasicChannelListPayload();
   
      JSONObject androidParams = testHelper.createBasicChannelListPayload();
      androidParams.put("awl_list", new JSONObject());
      // Get call will not return a Google project number if it hasn't been entered on the OneSignal dashboard.
      ShadowOneSignalRestClient.nextSuccessResponse = androidParams.toString();
      
      // Don't fire the mock callback, it will be done from the real class.
//      ShadowPushRegistratorGCM.skipComplete = true;
      
      OneSignal.init(blankActivity, null, ONESIGNAL_APP_ID);
      threadAndTaskWait();
   
      testHelper.assertChannelsForBasicChannelList();
   }

   @Test
   public void shouldCorrectlyRemoveOpenedHandlerAndFireMissedOnesWhenAddedBack() throws Exception {
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());
      threadAndTaskWait();

      OneSignal.removeNotificationOpenedHandler();
      OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Robo test message\", \"custom\": { \"i\": \"UUID\" } }]"), false);
      assertNull(notificationOpenedMessage);

      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());
      assertEquals("Robo test message", notificationOpenedMessage);
   }

   @Test
   public void shouldNotFireNotificationOpenAgainAfterAppRestart() throws Exception {
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());

      threadAndTaskWait();

      Bundle bundle = getBaseNotifBundle();
      OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      threadAndTaskWait();

      notificationOpenedMessage = null;

      // Restart app - Should omit notification_types
      StaticResetHelper.restSetStaticFields();
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());

      threadAndTaskWait();

      assertEquals(null, notificationOpenedMessage);
   }

   @Test
   public void testOpenFromNotificationWhenAppIsInBackground() throws Exception {
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());
      assertNull(notificationOpenedMessage);

      OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false);
      assertEquals("Test Msg", notificationOpenedMessage);
      threadAndTaskWait();
   }

   @Test
   public void testOpeningLauncherActivity() throws Exception {
      AddLauncherIntentFilter();

      // From app launching normally
      assertNotNull(shadowOf(blankActivity).getNextStartedActivity());

      OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false);

      assertNotNull(shadowOf(blankActivity).getNextStartedActivity());
      assertNull(shadowOf(blankActivity).getNextStartedActivity());
   }

   @Test
   public void testOpeningLaunchUrl() throws Exception {
      // Removes app launch
      shadowOf(blankActivity).getNextStartedActivity();

      // No OneSignal init here to test case where it is located in an Activity.

      OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\", \"u\": \"http://google.com\" } }]"), false);

      Intent intent = shadowOf(blankActivity).getNextStartedActivity();
      assertEquals("android.intent.action.VIEW", intent.getAction());
      assertEquals("http://google.com", intent.getData().toString());
      assertNull(shadowOf(blankActivity).getNextStartedActivity());
   }

   @Config(manifest="AndroidManifestDefaultOpenDisabled.xml")
   @Test
   public void testOpeningLaunchUrlWithDisableDefault() throws Exception {
      // Removes app launch
      shadowOf(blankActivity).getNextStartedActivity();

      // No OneSignal init here to test case where it is located in an Activity.

      OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\", \"u\": \"http://google.com\" } }]"), false);
      assertNull(shadowOf(blankActivity).getNextStartedActivity());
   }

   @Config(manifest="AndroidManifestDefaultOpenDisabled.xml")
   @Test
   public void testDisableOpeningLauncherActivityOnNotifiOpen() throws Exception {
      // From app launching normally
      assertNotNull(shadowOf(blankActivity).getNextStartedActivity());
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());
      assertNull(notificationOpenedMessage);

      OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false);

      assertNull(shadowOf(blankActivity).getNextStartedActivity());
      assertEquals("Test Msg", notificationOpenedMessage);
   }

   private static String notificationReceivedBody;
   private static int androidNotificationId;
   @Test
   public void testNotificationReceivedWhenAppInFocus() throws Exception {
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler(), new OneSignal.NotificationReceivedHandler() {
         @Override
         public void notificationReceived(OSNotification notification) {
            androidNotificationId = notification.androidNotificationId;
            notificationReceivedBody = notification.payload.body;
         }
      });
      threadAndTaskWait();

      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);

      Bundle bundle = getBaseNotifBundle();
      boolean processResult = GcmBroadcastReceiver_processBundle(blankActivity, bundle);
      threadAndTaskWait();
      assertEquals(null, notificationOpenedMessage);
      assertFalse(processResult);
      // NotificationBundleProcessor.Process(...) will be called if processResult is true as a service
      NotificationBundleProcessor_Process(blankActivity, false, bundleAsJSONObject(bundle), null);
      assertEquals("Robo test message", notificationReceivedBody);
      assertFalse(0 == androidNotificationId);

      // Don't fire for duplicates
      notificationOpenedMessage = null;
      notificationReceivedBody = null;
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.None);
      assertNull(notificationOpenedMessage);

      GcmBroadcastReceiver_processBundle(blankActivity, bundle);
      threadAndTaskWait();
      assertEquals(null, notificationOpenedMessage);
      assertEquals(null, notificationReceivedBody);

      // Test that only NotificationReceivedHandler fires
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.None);
      bundle = getBaseNotifBundle("UUID2");
      notificationOpenedMessage = null;
      notificationReceivedBody = null;

      GcmBroadcastReceiver_processBundle(blankActivity, bundle);
      threadAndTaskWait();
      assertEquals(null, notificationOpenedMessage);
      assertNull(notificationOpenedMessage);
      assertEquals("Robo test message", notificationReceivedBody);
   }

   @Test
   @Config(shadows = {ShadowBadgeCountUpdater.class})
   public void testBadgeClearOnFirstStart() throws Exception {
      ShadowBadgeCountUpdater.lastCount = -1;

      // First run should set badge to 0
      OneSignal.init(RuntimeEnvironment.application, "123456789", ONESIGNAL_APP_ID);
      threadAndTaskWait();
      assertEquals(0, ShadowBadgeCountUpdater.lastCount);

      // Resume should have no effect on badges.
      ShadowBadgeCountUpdater.lastCount = -1;
      blankActivityController.resume();
      threadAndTaskWait();
      assertEquals(-1, ShadowBadgeCountUpdater.lastCount);

      // Nor an app restart
      StaticResetHelper.restSetStaticFields();
      OneSignal.init(RuntimeEnvironment.application, "123456789", ONESIGNAL_APP_ID);
      threadAndTaskWait();
      assertEquals(-1, ShadowBadgeCountUpdater.lastCount);
   }

   @Test
   public void testUnsubscribeStatusShouldBeSetIfGCMErrored() throws Exception {
      ShadowPushRegistratorGCM.fail = true;
      OneSignalInit();
      threadAndTaskWait();
      assertEquals(-7, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
   }

   @Test
   public void testInvalidGoogleProjectNumberWithSuccessfulRegisterResponse() throws Exception {
      GetIdsAvailable();
      // A more real test would be "missing support library" but bad project number is an easier setup
      //   and is testing the same logic.
      ShadowPushRegistratorGCM.fail = true;
      OneSignalInitWithBadProjectNum();
      threadAndTaskWait();
      Robolectric.getForegroundThreadScheduler().runOneTask();

      assertEquals(-7, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
      // Test that idsAvailable still fires
      assertEquals(ShadowOneSignalRestClient.pushUserId, callBackUseId);
      assertNull(getCallBackRegId); // Since GCM registration failed, this should be null

      // We now get a push token after the device registers with Onesignal,
      //    the idsAvailable callback should fire a 2nd time with a registrationId automatically
      ShadowPushRegistratorGCM.manualFireRegisterForPush();
      threadAndTaskWait();
      assertEquals(ShadowPushRegistratorGCM.regId, getCallBackRegId);
   }

   @Test
   public void testGMSErrorsAfterSuccessfulSubscribeDoNotUnsubscribeTheDevice() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));

      ShadowOneSignalRestClient.lastPost = null;
      restartAppAndElapseTimeToNextSession();

      ShadowPushRegistratorGCM.fail = true;
      OneSignalInit();
      threadAndTaskWait();
      assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void testInvalidGoogleProjectNumberWithFailedRegisterResponse() throws Exception {
      // Ensures lower number notification_types do not over right higher numbered ones.
      ShadowPushRegistratorGCM.fail = true;
      GetIdsAvailable();
      OneSignalInitWithBadProjectNum();

      threadAndTaskWait();
      Robolectric.getForegroundThreadScheduler().runOneTask();
      assertEquals(-7, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Test that idsAvailable still fires
      assertEquals(ShadowOneSignalRestClient.pushUserId, callBackUseId);
   }

   @Test
   public void testUnsubcribedShouldMakeRegIdNullToIdsAvailable() throws Exception {
      GetIdsAvailable();
      OneSignalInit();
      threadAndTaskWait();
      assertEquals(ShadowPushRegistratorGCM.regId, ShadowOneSignalRestClient.lastPost.getString("identifier"));

      Robolectric.getForegroundThreadScheduler().runOneTask();
      assertEquals(ShadowPushRegistratorGCM.regId, getCallBackRegId);

      OneSignal.setSubscription(false);
      GetIdsAvailable();
      threadAndTaskWait();
      assertNull(getCallBackRegId);
   }

   @Test
   public void testSetSubscriptionShouldNotOverrideSubscribeError() throws Exception {
      OneSignalInitWithBadProjectNum();
      threadAndTaskWait();

      // Should not try to update server
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.setSubscription(true);
      assertNull(ShadowOneSignalRestClient.lastPost);

      // Restart app - Should omit notification_types
      restartAppAndElapseTimeToNextSession();
      OneSignalInitWithBadProjectNum();
      threadAndTaskWait();
      assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void shouldNotResetSubscriptionOnSession() throws Exception {
      OneSignalInit();
      OneSignal.setSubscription(false);
      threadAndTaskWait();
      assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      restartAppAndElapseTimeToNextSession();

      OneSignalInit();
      threadAndTaskWait();
      assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void shouldSetSubscriptionCorrectlyEvenAfterFirstOneSignalRestInitFail() throws Exception {
      // Failed to register with OneSignal but SetSubscription was called with false
      ShadowOneSignalRestClient.failAll = true;
      OneSignalInit();
      OneSignal.setSubscription(false);
      threadAndTaskWait();
      ShadowOneSignalRestClient.failAll = false;


      // Restart app - Should send unsubscribe with create player call.
      restartAppAndElapseTimeToNextSession();
      OneSignalInit();
      threadAndTaskWait();
      assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Restart app again - Value synced last time so don't send again.
      restartAppAndElapseTimeToNextSession();
      OneSignalInit();
      threadAndTaskWait();
      assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void shouldUpdateNotificationTypesCorrectlyEvenWhenSetSubscriptionIsCalledInAnErrorState() throws Exception {
      ShadowPushRegistratorGCM.fail = true;
      OneSignalInit();
      threadAndTaskWait();
      OneSignal.setSubscription(true);

      // Restart app - Should send subscribe with on_session call.
      fastAppRestart();
      ShadowPushRegistratorGCM.fail = false;
      OneSignalInit();
      threadAndTaskWait();
      assertEquals(1, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
   }


   @Test
   public void shouldAllowMultipleSetSubscription() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      OneSignal.setSubscription(false);
      threadAndTaskWait();

      assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Should not resend same value
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.setSubscription(false);
      assertNull(ShadowOneSignalRestClient.lastPost);



      OneSignal.setSubscription(true);
      threadAndTaskWait();
      assertEquals(1, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Should not resend same value
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.setSubscription(true);
      threadAndTaskWait();
      assertNull(ShadowOneSignalRestClient.lastPost);
   }

   private static boolean userIdWasNull = false;
   @Test
   public void shouldNotFireIdsAvailableWithoutUserId() throws Exception {
      ShadowOneSignalRestClient.failNext = true;
      ShadowPushRegistratorGCM.fail = true;

      OneSignal.idsAvailable(new OneSignal.IdsAvailableHandler() {
         @Override
         public void idsAvailable(String userId, String registrationId) {
            if (userId == null)
               userIdWasNull = true;
         }
      });

      OneSignalInit();
      assertFalse(userIdWasNull);
      threadAndTaskWait();
   }

   @Test
   public void testGCMTimeOutThenSuccessesLater() throws Exception {
      // Init with a bad connection to Google.
      ShadowPushRegistratorGCM.fail = true;
      OneSignalInit();
      threadAndTaskWait();
      assertFalse(ShadowOneSignalRestClient.lastPost.has("identifier"));

      // Registers for GCM after a retry
      ShadowPushRegistratorGCM.fail = false;
      ShadowPushRegistratorGCM.manualFireRegisterForPush();
      threadAndTaskWait();
      assertEquals(ShadowPushRegistratorGCM.regId, ShadowOneSignalRestClient.lastPost.getString("identifier"));

      // Cold restart app, should not send the same identifier again.
      ShadowOneSignalRestClient.lastPost = null;
      restartAppAndElapseTimeToNextSession();
      OneSignalInit();
      threadAndTaskWait();
      assertFalse(ShadowOneSignalRestClient.lastPost.has("identifier"));
   }

   @Test
   public void testChangeAppId() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      int normalCreateFieldCount = ShadowOneSignalRestClient.lastPost.length();
      fastAppRestart();
      OneSignal.init(blankActivity, "123456789", "99f7f966-d8cc-11e4-bed1-df8f05be55b2");
      threadAndTaskWait();

      assertEquals(normalCreateFieldCount, ShadowOneSignalRestClient.lastPost.length());
   }

   @Test
   public void testUserDeletedFromServer() throws Exception {
      // First cold boot normal
      OneSignalInit();
      threadAndTaskWait();

      int normalCreateFieldCount = ShadowOneSignalRestClient.lastPost.length();
      ShadowOneSignalRestClient.lastPost = null;

      // Developer deletes user, cold boots apps should resend all fields
      restartAppAndElapseTimeToNextSession();
      ShadowOneSignalRestClient.failNext = true;
      ShadowOneSignalRestClient.failResponse = "{\"errors\":[\"Device type  is not a valid device_type. Valid options are: 0 = iOS, 1 = Android, 2 = Amazon, 3 = WindowsPhone(MPNS), 4 = ChromeApp, 5 = ChromeWebsite, 6 = WindowsPhone(WNS), 7 = Safari(APNS), 8 = Firefox\"]}";
      OneSignalInit();
      threadAndTaskWait();

      assertEquals(normalCreateFieldCount, ShadowOneSignalRestClient.lastPost.length());


      // Developer deletes users again from dashboard while app is running.
      ShadowOneSignalRestClient.lastPost = null;
      ShadowOneSignalRestClient.failNext = true;
      ShadowOneSignalRestClient.failResponse = "{\"errors\":[\"No user with this id found\"]}";
      OneSignal.sendTag("key1", "value1");
      threadAndTaskWait();

      assertEquals(normalCreateFieldCount, ShadowOneSignalRestClient.lastPost.length() - 1);
   }

   @Test
   public void testOfflineCrashes() throws Exception {
      ConnectivityManager connectivityManager = (ConnectivityManager)RuntimeEnvironment.application.getSystemService(Context.CONNECTIVITY_SERVICE);
      ShadowConnectivityManager shadowConnectivityManager = shadowOf(connectivityManager);
      shadowConnectivityManager.setActiveNetworkInfo(null);

      OneSignalInit();
      threadAndTaskWait();

      OneSignal.sendTag("key", "value");
      threadAndTaskWait();

      OneSignal.setSubscription(false);
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
   public void shouldSetEmail() throws Exception {
      OneSignalInit();
      String email = "josh@onesignal.com";

      OneSignal.setEmail(email);
      threadAndTaskWait();

      assertEquals(4, ShadowOneSignalRestClient.networkCallCount);

      JSONObject pushPost = ShadowOneSignalRestClient.requests.get(1).payload;
      assertEquals(email, pushPost.getString("email"));
      assertEquals(1, pushPost.getInt("device_type"));

      JSONObject emailPost = ShadowOneSignalRestClient.requests.get(2).payload;
      assertEquals(email, emailPost.getString("identifier"));
      assertEquals(11, emailPost.getInt("device_type"));
      assertEquals(ShadowOneSignalRestClient.pushUserId, emailPost.getString("device_player_id"));

      JSONObject pushPut = ShadowOneSignalRestClient.requests.get(3).payload;
      assertEquals(ShadowOneSignalRestClient.emailUserId, pushPut.getString("parent_player_id"));
      assertFalse(pushPut.has("identifier"));
   }

   @Test
   public void shouldSendTagsToEmailBeforeCreate() throws Exception {
      OneSignalInit();
      OneSignal.setEmail("josh@onesignal.com");
      JSONObject tagsJson = new JSONObject("{\"test1\": \"value1\", \"test2\": \"value2\"}");
      OneSignal.sendTags(tagsJson);
      threadAndTaskWait();

      assertEquals(4, ShadowOneSignalRestClient.networkCallCount);

      ShadowOneSignalRestClient.Request emailPost = ShadowOneSignalRestClient.requests.get(2);
      assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPost.method);
      assertEquals("josh@onesignal.com", emailPost.payload.get("identifier"));
      assertEquals(tagsJson.toString(), emailPost.payload.getJSONObject("tags").toString());
   }

   @Test
   public void shouldWaitBeforeCreateEmailIfPushCreateFails() throws Exception {
      ShadowOneSignalRestClient.failPosts = true;

      OneSignalInit();
      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      // Assert we are sending / retry for the push player first.
      assertEquals(5, ShadowOneSignalRestClient.networkCallCount);
      for(int i = 1; i < ShadowOneSignalRestClient.networkCallCount; i++) {
         ShadowOneSignalRestClient.Request emailPost = ShadowOneSignalRestClient.requests.get(i);
         assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPost.method);
         assertEquals(1, emailPost.payload.getInt("device_type"));
      }

      // Turn off fail mocking, call sendTags to trigger another retry
      ShadowOneSignalRestClient.failPosts = false;
      OneSignal.sendTag("test", "test");
      threadAndTaskWait();

      // Should now POST to create device_type 11 (email)
      ShadowOneSignalRestClient.Request emailPost = ShadowOneSignalRestClient.requests.get(6);
      assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPost.method);
      assertEquals("josh@onesignal.com", emailPost.payload.get("identifier"));
   }

   @Test
   public void shouldSendTagsToEmailAfterCreate() throws Exception {
      OneSignalInit();
      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      JSONObject tagsJson = new JSONObject("{\"test1\": \"value1\", \"test2\": \"value2\"}");
      OneSignal.sendTags(tagsJson);
      threadAndTaskWait();

      assertEquals(6, ShadowOneSignalRestClient.networkCallCount);

      ShadowOneSignalRestClient.Request emailPut = ShadowOneSignalRestClient.requests.get(5);
      assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, emailPut.method);
      assertEquals("players/b007f967-98cc-11e4-bed1-118f05be4522", emailPut.url);
      assertEquals(tagsJson.toString(), emailPut.payload.getJSONObject("tags").toString());
   }

   @Test
   public void shouldSetEmailWithAuthHash() throws Exception {
      OneSignalInit();
      String email = "josh@onesignal.com";
      String mockEmailHash = new String(new char[64]).replace('\0', '0');

      OneSignal.setEmail(email, mockEmailHash);
      threadAndTaskWait();

      JSONObject emailPost = ShadowOneSignalRestClient.requests.get(2).payload;
      assertEquals(email, emailPost.getString("identifier"));
      assertEquals(11, emailPost.getInt("device_type"));
      assertEquals(mockEmailHash, emailPost.getString("email_auth_hash"));
   }

   private class TestEmailUpdateHandler implements OneSignal.EmailUpdateHandler {
      boolean emailFiredSuccess = false;
      OneSignal.EmailUpdateError emailFiredFailure = null;

      @Override
      public void onSuccess() {
         emailFiredSuccess = true;
      }

      @Override
      public void onFailure(OneSignal.EmailUpdateError error) {
         emailFiredFailure = error;
      }
   }

   @Test
   public void shouldFireOnSuccessOfEmailUpdate() throws Exception {
      OneSignalInit();
      TestEmailUpdateHandler testEmailUpdateHandler = new TestEmailUpdateHandler();
      OneSignal.setEmail("josh@onesignal.com", testEmailUpdateHandler);
      assertFalse(testEmailUpdateHandler.emailFiredSuccess);
      threadAndTaskWait();

      assertTrue(testEmailUpdateHandler.emailFiredSuccess);
      assertNull(testEmailUpdateHandler.emailFiredFailure);
   }

   @Test
   public void shouldFireOnSuccessOfEmailEvenWhenNoChanges() throws Exception {
      OneSignalInit();
      String email = "josh@onesignal.com";
      OneSignal.setEmail(email);
      threadAndTaskWait();

      TestEmailUpdateHandler testEmailUpdateHandler = new TestEmailUpdateHandler();
      OneSignal.setEmail(email, testEmailUpdateHandler);
      threadAndTaskWait();

      assertTrue(testEmailUpdateHandler.emailFiredSuccess);
      assertNull(testEmailUpdateHandler.emailFiredFailure);
   }

   @Test
   public void shouldFireOnFailureOfEmailUpdateOnNetworkFailure() throws Exception {
      OneSignalInit();
      TestEmailUpdateHandler testEmailUpdateHandler = new TestEmailUpdateHandler();
      OneSignal.setEmail("josh@onesignal.com", testEmailUpdateHandler);
      ShadowOneSignalRestClient.failAll = true;
      threadAndTaskWait();

      assertFalse(testEmailUpdateHandler.emailFiredSuccess);
      assertEquals(OneSignal.EmailErrorType.NETWORK, testEmailUpdateHandler.emailFiredFailure.getType());
   }

   @Test
   public void shouldFireOnSuccessOnlyAfterNetworkCallAfterLogout() throws Exception {
      OneSignalInit();
      emailSetThenLogout();
      TestEmailUpdateHandler testEmailUpdateHandler = new TestEmailUpdateHandler();
      OneSignal.setEmail("josh@onesignal.com", testEmailUpdateHandler);
      assertFalse(testEmailUpdateHandler.emailFiredSuccess);
      threadAndTaskWait();

      assertTrue(testEmailUpdateHandler.emailFiredSuccess);
      assertNull(testEmailUpdateHandler.emailFiredFailure);
   }

   // Should create a new email instead of updating existing player record when no auth hash
   @Test
   public void shouldDoPostOnEmailChange() throws Exception {
      OneSignalInit();

      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      String newMockEmailPlayerId = "c007f967-98cc-11e4-bed1-118f05be4533";
      ShadowOneSignalRestClient.emailUserId = newMockEmailPlayerId;
      String newEmail = "different@email.com";
      OneSignal.setEmail(newEmail);
      threadAndTaskWait();

      ShadowOneSignalRestClient.Request emailPost = ShadowOneSignalRestClient.requests.get(5);
      assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPost.method);
      assertEquals(newEmail, emailPost.payload.get("identifier"));

      ShadowOneSignalRestClient.Request playerPut = ShadowOneSignalRestClient.requests.get(6);
      assertEquals(newMockEmailPlayerId, playerPut.payload.get("parent_player_id"));
   }

   // Should update player with new email instead of creating a new one when auth hash is provided
   @Test
   public void shouldUpdateEmailWhenAuthHashIsUsed() throws Exception {
      OneSignalInit();
      String email = "josh@onesignal.com";
      String mockEmailHash = new String(new char[64]).replace('\0', '0');

      OneSignal.setEmail(email, mockEmailHash);
      threadAndTaskWait();
      OneSignal.setEmail("different@email.com", mockEmailHash);
      threadAndTaskWait();

      ShadowOneSignalRestClient.Request pushPut = ShadowOneSignalRestClient.requests.get(4);
      assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, pushPut.method);
      assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511", pushPut.url);
      assertEquals("different@email.com", pushPut.payload.get("email"));

      ShadowOneSignalRestClient.Request emailPut = ShadowOneSignalRestClient.requests.get(5);
      assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, emailPut.method);
      assertEquals("players/b007f967-98cc-11e4-bed1-118f05be4522", emailPut.url);
      assertEquals("different@email.com", emailPut.payload.get("identifier"));
   }

   @Test
   public void shouldSendEmailAuthHashWithLogout() throws Exception {
      OneSignalInit();
      String mockEmailHash = new String(new char[64]).replace('\0', '0');
      OneSignal.setEmail("josh@onesignal.com", mockEmailHash);
      threadAndTaskWait();

      OneSignal.logoutEmail();
      threadAndTaskWait();

      ShadowOneSignalRestClient.Request emailPut = ShadowOneSignalRestClient.requests.get(4);
      assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511/email_logout", emailPut.url);
      assertEquals(mockEmailHash, emailPut.payload.get("email_auth_hash"));
   }

   private void emailSetThenLogout() throws Exception {
      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      OneSignal.logoutEmail();
      threadAndTaskWait();
   }

   @Test
   public void shouldLogoutOfEmail() throws Exception {
      OneSignalInit();

      emailSetThenLogout();

      ShadowOneSignalRestClient.Request logoutEmailPost = ShadowOneSignalRestClient.requests.get(4);
      assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511/email_logout", logoutEmailPost.url);
      assertEquals("b007f967-98cc-11e4-bed1-118f05be4522", logoutEmailPost.payload.get("parent_player_id"));
      assertEquals("b2f7f966-d8cc-11e4-bed1-df8f05be55ba", logoutEmailPost.payload.get("app_id"));
   }

   @Test
   public void shouldFireOnSuccessOfLogoutEmail() throws Exception {
      OneSignalInit();
      TestEmailUpdateHandler testEmailUpdateHandler = new TestEmailUpdateHandler();

      OneSignalInit();
      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();
      OneSignal.logoutEmail(testEmailUpdateHandler);
      threadAndTaskWait();

      assertTrue(testEmailUpdateHandler.emailFiredSuccess);
      assertNull(testEmailUpdateHandler.emailFiredFailure);
   }

   @Test
   public void shouldFireOnFailureOfLogoutEmailOnNetworkFailure() throws Exception {
      OneSignalInit();
      TestEmailUpdateHandler testEmailUpdateHandler = new TestEmailUpdateHandler();

      OneSignalInit();
      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      ShadowOneSignalRestClient.failAll = true;
      OneSignal.logoutEmail(testEmailUpdateHandler);
      threadAndTaskWait();

      assertFalse(testEmailUpdateHandler.emailFiredSuccess);
      assertEquals(OneSignal.EmailErrorType.NETWORK, testEmailUpdateHandler.emailFiredFailure.getType());
   }

   @Test
   public void shouldCreateNewEmailAfterLogout() throws Exception {
      OneSignalInit();

      emailSetThenLogout();

      String newMockEmailPlayerId = "c007f967-98cc-11e4-bed1-118f05be4533";
      ShadowOneSignalRestClient.emailUserId = newMockEmailPlayerId;
      OneSignal.setEmail("different@email.com");
      threadAndTaskWait();


      // Update Push record's email field.
      ShadowOneSignalRestClient.Request putPushEmail = ShadowOneSignalRestClient.requests.get(5);
      assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, putPushEmail.method);
      assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511", putPushEmail.url);
      assertEquals("different@email.com", putPushEmail.payload.get("email"));

      // Create new Email record
      ShadowOneSignalRestClient.Request emailPost = ShadowOneSignalRestClient.requests.get(6);
      assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPost.method);
      assertEquals("different@email.com", emailPost.payload.get("identifier"));

      // Update Push record's parent_player_id
      ShadowOneSignalRestClient.Request playerPut2 = ShadowOneSignalRestClient.requests.get(7);
      assertEquals(newMockEmailPlayerId, playerPut2.payload.get("parent_player_id"));
   }

   @Test
   public void shouldSendOnSessionToEmail() throws Exception {
      OneSignalInit();
      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      restartAppAndElapseTimeToNextSession();
      OneSignalInit();
      threadAndTaskWait();

      ShadowOneSignalRestClient.Request emailPost = ShadowOneSignalRestClient.requests.get(6);
      assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPost.method);
      assertEquals("players/b007f967-98cc-11e4-bed1-118f05be4522/on_session", emailPost.url);
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

      GetTags();
      threadAndTaskWait();

      assertEquals("value1", lastGetTags.getString("test1"));
      assertEquals("value2", lastGetTags.getString("test2"));
      assertEquals(4, ShadowOneSignalRestClient.networkCallCount);
   }

   @Test
   public void shouldNotAttemptToSendTagsBeforeGettingPlayerId() throws Exception {
      ShadowPushRegistratorGCM.skipComplete = true;
      OneSignalInit();
      GetIdsAvailable();
      threadAndTaskWait();

      assertEquals(1, ShadowOneSignalRestClient.networkCallCount);

      // Should not attempt to make a network call yet as we don't have a player_id
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1\"}"));
      threadAndTaskWait();

      assertEquals(1, ShadowOneSignalRestClient.networkCallCount);

      ShadowPushRegistratorGCM.fireLastCallback();
      threadAndTaskWait();

      assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      assertNotNull(callBackUseId);
   }

   private class TestChangeTagsUpdateHandler implements ChangeTagsUpdateHandler {
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

      // should fail because there is no OneSignal player ID
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
   public void shouldCreatePlayerAfterDelayedTokenFromApplicationOnCreate() throws Exception {
      ShadowPushRegistratorGCM.skipComplete = true;
      OneSignal.init(blankActivity.getApplicationContext(), "123456789", ONESIGNAL_APP_ID);
      blankActivityController.resume();
      threadAndTaskWait();

      ShadowPushRegistratorGCM.fireLastCallback();
      threadAndTaskWait();

      ShadowOneSignalRestClient.Request createPlayer = ShadowOneSignalRestClient.requests.get(1);
      assertEquals(2, ShadowOneSignalRestClient.requests.size());
      assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, createPlayer.method);
      assertEquals("players", createPlayer.url);
      assertEquals("b2f7f966-d8cc-11e4-bed1-df8f05be55ba", createPlayer.payload.get("app_id"));
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

      GetTags();
      threadAndTaskWait();

      assertEquals("{}", lastGetTags.toString());
   }

   @Test
   public void testSendTagNonStringValues() throws Exception {
      OneSignalInit();
      OneSignal.sendTags("{\"int\": 122, \"bool\": true, \"null\": null, \"array\": [123], \"object\": {}}");
      GetTags();
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

      for(int a = 0; a < 10; a++) {
         List<Thread> threadList = new ArrayList<>(30);
         for (int i = 0; i < 30; i++) {
            Thread lastThread = newSendTagTestThread(Thread.currentThread(), i);
            lastThread.start();
            threadList.add(lastThread);
            assertFalse(failedCurModTest);
         }

         for(Thread thread : threadList)
            thread.join();
         assertFalse(failedCurModTest);
      }

      OneSignalInit();

      for(int a = 0; a < 10; a++) {
         List<Thread> threadList = new ArrayList<>(30);
         for (int i = 0; i < 30; i++) {
            Thread lastThread = newSendTagSetZeroThread(Thread.currentThread(), i);
            lastThread.start();
            threadList.add(lastThread);
            assertFalse(failedCurModTest);
         }

         for(Thread thread : threadList)
            thread.join();
         assertFalse(failedCurModTest);
      }

      threadAndTaskWait();

      JSONObject tags = ShadowOneSignalRestClient.lastPost.getJSONObject("tags");
      //assert the tags...which should all be 0
      for(int a = 0; a < 10; a++) {
         for (int i = 0; i < 30; i++) {
            assertEquals("0",tags.getString("key"+i));
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
      ShadowApplication.getInstance().grantPermissions("android.permission.ACCESS_FINE_LOCATION");
      ShadowFusedLocationApiWrapper.lat = 1.0d;
      ShadowFusedLocationApiWrapper.log = 2.0d;
      ShadowFusedLocationApiWrapper.accuracy = 3.0f;
      ShadowFusedLocationApiWrapper.time = 12345L;

      //queue up a bunch of actions and check that the queue gains size before init
      // ----- START QUEUE ------
      OneSignal.syncHashedEmail("test@test.com");

      for(int a = 0; a < 500; a++) {
         OneSignal.sendTag("a"+a,String.valueOf(a));
      }

      OneSignal.getTags(new OneSignal.GetTagsHandler() {
         @Override
         public void tagsAvailable(JSONObject tags) {
            //assert that the first 10 tags sent were available
            try {
               for(int a = 0; a < 10; a++) {
                  assertEquals(String.valueOf(a),tags.get("a"+a));
               }
            }
            catch (Exception e) {
               e.printStackTrace();
            }
         }
      });

      final AtomicBoolean callbackFired = new AtomicBoolean(false);
      OneSignal.IdsAvailableHandler idsAvailableHandler = new OneSignal.IdsAvailableHandler() {
         @Override
         public void idsAvailable(String userId, String registrationId) {
            //Assert the userId being returned
            callbackFired.set(true);
            assertEquals(ShadowOneSignalRestClient.pushUserId, userId);
         }
      };

      OneSignal.idsAvailable(idsAvailableHandler);
      System.gc(); //make sure the IdsAvailableHandler is retained...


      // ----- END QUEUE ------

      //there should be 503 pending operations in the queue
      assertEquals(503, OneSignal.taskQueueWaitingForInit.size());

      OneSignalInit(); //starts the pending tasks executor

      // ---- EXECUTOR STILL RUNNING -----
      //these operations should be sent straight to the executor which is still running...
      OneSignal.sendTag("a499","5");
      OneSignal.sendTag("a498","4");
      OneSignal.sendTag("a497","3");
      OneSignal.sendTag("a496","2");
      OneSignal.sendTag("a495","1");
      OneSignal.syncHashedEmail("test1@test.com");

      OneSignal.getTags(new OneSignal.GetTagsHandler() {
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
      assertEquals(0, OneSignal.taskQueueWaitingForInit.size());

      threadAndTaskWait();

      //Assert that the queued up operations ran in correct order
      // and that the correct user state was POSTed and synced
      assertTrue(callbackFired.get()); //check if the callback got fired

      //assert the hashed email which should be test1@test.com, NOT test@test.com
      assertEquals("94fba03762323f286d7c3ca9e001c541", ShadowOneSignalRestClient.lastPost.getString("em_m"));
      assertEquals("c31ddeb0a3d6cc32d82b494336d9f27444904fd7", ShadowOneSignalRestClient.lastPost.getString("em_s"));

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
      ShadowApplication.getInstance().grantPermissions("android.permission.ACCESS_FINE_LOCATION");
      ShadowFusedLocationApiWrapper.lat = 1.0d;
      ShadowFusedLocationApiWrapper.log = 2.0d;
      ShadowFusedLocationApiWrapper.accuracy = 3.0f;
      ShadowFusedLocationApiWrapper.time = 12345L;

      OneSignalInit(); //starts the pending tasks executor

      OneSignal.syncHashedEmail("test@test.com");

      for(int a = 0; a < 5; a++)
         OneSignal.sendTag("a" + a, String.valueOf(a));

      //the queue should be empty since we already initialized the SDK
      assertEquals(0, OneSignal.taskQueueWaitingForInit.size());

      threadAndTaskWait();

      //Assert that the queued up operations ran in correct order
      // and that the correct user state was POSTed and synced

      //assert the hashed email which should be test1@test.com, NOT test@test.com
      assertEquals("b642b4217b34b1e8d3bd915fc65c4452", ShadowOneSignalRestClient.lastPost.getString("em_m"));
      assertEquals("a6ad00ac113a19d953efb91820d8788e2263b28a", ShadowOneSignalRestClient.lastPost.getString("em_s"));

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
      return new Thread(new Runnable() {
         @Override
         public void run() {
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
         }
      });
   }

   @Test
   public void shouldSaveToSyncIfKilledAndSyncOnNextAppStart() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      
      OneSignal.sendTag("key", "value");
      // Pause Activity and trigger delayed runnable that will trigger out of focus logic
      blankActivityController.pause();
      OneSignalPackagePrivateHelper.runFocusRunnables();

      // Network call for android params and player create should have been made.
      assertEquals(2, ShadowOneSignalRestClient.networkCallCount);

      // App closed and re-opened.
      fastAppRestart();
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
      OneSignalPackagePrivateHelper.runFocusRunnables();
      assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      fastAppRestart();
      threadAndTaskWait();
   
      // Tags did not get synced so SyncService should be scheduled
      AlarmManager alarmManager = (AlarmManager)RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
      ShadowAlarmManager shadowAlarmManager = shadowOf(alarmManager);
      assertEquals(1, shadowAlarmManager.getScheduledAlarms().size());
      assertEquals(SyncService.class, shadowOf(shadowOf(shadowAlarmManager.getNextScheduledAlarm().operation).getSavedIntent()).getIntentClass());
      shadowAlarmManager.getScheduledAlarms().clear();
   
      // Test running the service
      Robolectric.buildService(SyncService.class).startCommand(0, 0);
      threadAndTaskWait();
      assertEquals("value", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("key"));
      assertEquals(3, ShadowOneSignalRestClient.networkCallCount);

      // Test starting app
      OneSignalInit();
      threadAndTaskWait();
      
      // No new changes, don't schedule another restart.
      // App is swiped away
      blankActivityController.pause();
      OneSignalPackagePrivateHelper.runFocusRunnables();
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
      OneSignalPackagePrivateHelper.runFocusRunnables();
   }

   @Test
   public void ensureSchedulingOfSyncJobServiceOnActivityPause() throws Exception {
      sendTagsAndImmediatelyBackgroundApp();

      // A future job should be scheduled to finish the sync.
      JobScheduler jobScheduler = (JobScheduler)blankActivity.getSystemService(Context.JOB_SCHEDULER_SERVICE);
      assertEquals("com.onesignal.SyncJobService", jobScheduler.getAllPendingJobs().get(0).getService().getClassName());
   }

   @Test
   public void ensureSyncJobIsCanceledOnAppResume() throws Exception {
      sendTagsAndImmediatelyBackgroundApp();
      blankActivityController.resume();

      // Jobs should no longer be not be scheduled
      JobScheduler jobScheduler = (JobScheduler) blankActivity.getSystemService(Context.JOB_SCHEDULER_SERVICE);
      assertEquals(0, jobScheduler.getAllPendingJobs().size());
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
   @Config(sdk = 26, shadows = { ShadowGoogleApiClientCompatProxy.class, ShadowLocationGMS.class })
   public void ensureSyncJobServiceRescheduleOnApiTimeout() throws Exception {
      ShadowApplication.getInstance().grantPermissions("android.permission.ACCESS_FINE_LOCATION");
      ShadowGoogleApiClientCompatProxy.skipOnConnected = true;

      OneSignalInit();
      threadAndTaskWait();

      JobScheduler jobScheduler = (JobScheduler)blankActivity.getSystemService(Context.JOB_SCHEDULER_SERVICE);
      assertEquals("com.onesignal.SyncJobService", jobScheduler.getAllPendingJobs().get(0).getService().getClassName());
   }

   private void useAppFor2minThenBackground() throws Exception {
      // 1. Start app
      OneSignalInit();
      threadAndTaskWait();

      // 2. Wait 2 minutes
      ShadowSystemClock.setCurrentTimeMillis(120_000);

      // 3. Put app in background
      blankActivityController.pause();
      OneSignalPackagePrivateHelper.runFocusRunnables();
   }

   @Test
   @Config(sdk = 26)
   public void ensureSchedulingOfSyncJobServiceOnActivityPause_forPendingActiveTime() throws Exception {
      useAppFor2minThenBackground();

      // A future job should be scheduled to finish the sync.
      JobScheduler jobScheduler = (JobScheduler)blankActivity.getSystemService(Context.JOB_SCHEDULER_SERVICE);
      assertEquals("com.onesignal.SyncJobService", jobScheduler.getAllPendingJobs().get(0).getService().getClassName());
   }

   private static void assertSuccessfulOnFocus(ShadowOneSignalRestClient.Request request) {
      assertThat(request.method, is(REST_METHOD.POST));
      assertThat(request.url, containsString("focus"));
      assertThat(request.payload.optInt("active_time"), is(120));
   }

   @Test
   public void ensureSyncIsRunOnAppResume_forPendingActiveTime() throws Exception {
      useAppFor2minThenBackground();

      blankActivityController.resume();
      threadAndTaskWait();

      assertThat(ShadowOneSignalRestClient.requests.size(), is(3));
      assertSuccessfulOnFocus(ShadowOneSignalRestClient.requests.get(2));
   }

   @Test
   @Config(sdk = 26)
   public void ensureFailureOnPauseIsSentFromSyncService_forPendingActiveTime() throws Exception {
      // 1. Start app
      OneSignalInit();
      threadAndTaskWait();

      // 2. Wait 2 minutes
      ShadowSystemClock.setCurrentTimeMillis(120_000);

      // 3. Put app in background, simulating network issue.
      ShadowOneSignalRestClient.failAll = true;
      blankActivityController.pause();
      threadAndTaskWait();
      assertEquals(3, ShadowOneSignalRestClient.requests.size());


      // Simulate a hung network connection when SyncJobService starts.
      ShadowOneSignalRestClient.failAll = false;
      SyncJobService syncJobService = Robolectric.buildService(SyncJobService.class).create().get();
      syncJobService.onStartJob(null);
      threadAndTaskWait(); // Kicks off the Job service's background thread.

      assertEquals(4, ShadowOneSignalRestClient.requests.size());
      assertSuccessfulOnFocus(ShadowOneSignalRestClient.requests.get(3));
   }


   @Test
   @Config(sdk = 26)
   public void ensureNoConcurrentUpdateCallsWithSameData_forPendingActiveTime() throws Exception {
      //useAppFor2minThenBackground();

      // 1. Start app
      OneSignalInit();
      threadAndTaskWait();

      // 2. Wait 2 minutes
      ShadowSystemClock.setCurrentTimeMillis(120_000);

      // 3. Put app in background
      ShadowOneSignalRestClient.freezeResponses = true;
      blankActivityController.pause();
      OneSignalPackagePrivateHelper.runFocusRunnables();

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
   public void testMethodCallsBeforeInit() throws Exception {
      GetTags();
      OneSignal.sendTag("key", "value");
      OneSignal.sendTags("{\"key\": \"value\"}");
      OneSignal.deleteTag("key");
      OneSignal.deleteTags("[\"key1\", \"key2\"]");
      OneSignal.setSubscription(false);
      OneSignal.enableVibrate(false);
      OneSignal.enableSound(false);
      OneSignal.promptLocation();
      OneSignal.postNotification("{}", new OneSignal.PostNotificationResponseHandler() {
         @Override
         public void onSuccess(JSONObject response) {}
         @Override
         public void onFailure(JSONObject response) {}
      });

      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
      OneSignal.removeNotificationOpenedHandler();
      OneSignal.removeNotificationReceivedHandler();

      OneSignal.startInit(blankActivity).init();

      // Checks that Notification setting worked.
      Field OneSignal_mInitBuilder = OneSignal.class.getDeclaredField("mInitBuilder");
      OneSignal_mInitBuilder.setAccessible(true);
      OneSignal.Builder builder = (OneSignal.Builder)OneSignal_mInitBuilder.get(null);
      Field OneSignal_Builder_mDisplayOption = builder.getClass().getDeclaredField("mDisplayOption");
      OneSignal_Builder_mDisplayOption.setAccessible(true);
      OneSignal.OSInFocusDisplayOption inFocusDisplayOption = (OneSignal.OSInFocusDisplayOption)OneSignal_Builder_mDisplayOption.get(builder);
      assertEquals(inFocusDisplayOption, OneSignal.OSInFocusDisplayOption.Notification);

      threadAndTaskWait();
   }

   // ####### DeleteTags Tests ######
   @Test
   public void testDeleteTagWithNonexistingKey() throws Exception {
      OneSignalInit();
      OneSignal.deleteTag("int");
   }
   
   @Test
   public void testDeleteTags() throws Exception {
      OneSignalInit();
      OneSignal.sendTags("{\"str\": \"str1\", \"int\": 122, \"bool\": true}");
      OneSignal.deleteTag("int");
      GetTags();
      threadAndTaskWait();

      assertFalse(lastGetTags.has("int"));
      lastGetTags = null;

      // Should only send the tag we added back.
      OneSignal.sendTags("{\"str\": \"str1\", \"int\": 122, \"bool\": true}");
      threadAndTaskWait();
      assertEquals("{\"int\":\"122\"}", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").toString());

      // Make sure a single delete works.
      OneSignal.deleteTag("int");
      GetTags();
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

      GetTags();
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


   // ####### GetTags Tests ########
   @Test
   public void testGetTagsWithNoTagsShouldBeNull() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      
      GetTags();
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
      GetTags();
      threadAndTaskWait();

      assertEquals("value1", lastGetTags.getString("test1"));
      assertEquals("value2", lastGetTags.getString("test2"));
   }

   @Test
   public void shouldGetTagsFromServerOnFirstCallAndMergeLocalAndRemote() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      ShadowOneSignalRestClient.nextSuccessfulGETResponse = "{\"tags\": {\"test1\": \"value1\", \"test2\": \"value2\"}}";
      GetTags();
      threadAndTaskWait();

      assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
      assertEquals("value1", lastGetTags.getString("test1"));
      assertEquals("value2", lastGetTags.getString("test2"));

      // Makes sure a 2nd call to GetTags correctly uses existing tags and merges new local changes.
      lastGetTags = null;
      OneSignal.sendTag("test3", "value3");
      GetTags();
      threadAndTaskWait();
      assertEquals("value1", lastGetTags.getString("test1"));
      assertEquals("value2", lastGetTags.getString("test2"));
      assertEquals("value3", lastGetTags.getString("test3"));
      threadAndTaskWait();
      // Also ensure only 1 network call is made to just send the new tags only.
      assertEquals(4, ShadowOneSignalRestClient.networkCallCount);

      fastAppRestart();
      OneSignalInit();
      threadAndTaskWait();

      // Test that local pending changes are still applied but new changes made server side a respected.
      lastGetTags = null;
      ShadowOneSignalRestClient.failNextPut = true;
      OneSignal.deleteTag("test2");
      OneSignal.sendTag("test4", "value4");
      ShadowOneSignalRestClient.nextSuccessfulGETResponse = "{\"tags\": {\"test1\": \"value1\", \"test2\": \"value2\",\"test3\": \"ShouldOverride\",\"test4\": \"RemoteShouldNotOverwriteLocalPending\"}}";
      GetTags();
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
      ShadowOneSignalRestClient.nextSuccessfulGETResponse = "{\"tags\": {\"test1\": \"value1\"}}";

      OneSignalInit();
      GetTags();
      threadAndTaskWait();

      assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
      assertEquals("value1", lastGetTags.getString("test1"));
      assertTrue(ShadowOneSignalRestClient.lastUrl.contains(ShadowOneSignalRestClient.pushUserId));
   }


   @Test
   public void syncHashedEmailTest() throws Exception {
      OneSignalInit();
      // Casing should be forced to lower.
      OneSignal.syncHashedEmail("Test@tEst.CoM");
      threadAndTaskWait();
      assertEquals("b642b4217b34b1e8d3bd915fc65c4452" ,ShadowOneSignalRestClient.lastPost.getString("em_m"));
      assertEquals("a6ad00ac113a19d953efb91820d8788e2263b28a" ,ShadowOneSignalRestClient.lastPost.getString("em_s"));

      // Test email update
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.syncHashedEmail("test@test2.com");
      threadAndTaskWait();
      assertEquals("3e1163777d25d2b935057c3ae393efee" ,ShadowOneSignalRestClient.lastPost.getString("em_m"));
      assertEquals("69e9ca5af84bc88bc185136cd6f782ee889be5c8" ,ShadowOneSignalRestClient.lastPost.getString("em_s"));

      // Test trim on email
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.syncHashedEmail(" test@test2.com ");
      threadAndTaskWait();
      assertNull(ShadowOneSignalRestClient.lastPost);

      // Test invalid email.
      OneSignal.syncHashedEmail("aaaaaa");
      threadAndTaskWait();
      assertNull(ShadowOneSignalRestClient.lastPost);

      // Test invalid email.
      OneSignal.syncHashedEmail(null);
      threadAndTaskWait();
      assertNull(ShadowOneSignalRestClient.lastPost);
   }

   // ####### on_focus Tests ########

   @Test
   public void sendsOnFocus() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      ShadowSystemClock.setCurrentTimeMillis(60 * 1000);

      blankActivityController.pause();
      threadAndTaskWait();
      assertEquals(60, ShadowOneSignalRestClient.lastPost.getInt("active_time"));
      assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
   }

   @Test
   public void sendsOnFocusToEmail() throws Exception {
      OneSignalInit();
      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      blankActivityController.resume();
      ShadowSystemClock.setCurrentTimeMillis(60 * 1000);
      blankActivityController.pause();
      threadAndTaskWait();

      assertEquals(6, ShadowOneSignalRestClient.networkCallCount);

      ShadowOneSignalRestClient.Request postPush = ShadowOneSignalRestClient.requests.get(4);
      assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511/on_focus", postPush.url);
      assertEquals(60, postPush.payload.getInt("active_time"));

      ShadowOneSignalRestClient.Request postEmail = ShadowOneSignalRestClient.requests.get(5);
      assertEquals("players/b007f967-98cc-11e4-bed1-118f05be4522/on_focus", postEmail.url);
      assertEquals(60, postEmail.payload.getInt("active_time"));
   }

   @Test
   public void gdprUserConsent() throws Exception {
      OneSignal.setRequiresUserPrivacyConsent(true);

      //check to ensure that the privacy consent status can never go from TRUE -> FALSE
      OneSignal.setRequiresUserPrivacyConsent(false);
      assertTrue(OneSignalPackagePrivateHelper.OneSignal_requiresUserPrivacyConsent());

      //privacy consent state should still be set to true (user consent required)
      OneSignalInit();

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
      OneSignal.setRequiresUserPrivacyConsent(true);

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
   public void shouldReturnCorrectConsentRequiredStatus() throws Exception {
      OneSignal.setRequiresUserPrivacyConsent(true);

      OneSignalInit();

      assertTrue(OneSignal.requiresUserPrivacyConsent());

      OneSignal.provideUserConsent(true);

      assertFalse(OneSignal.requiresUserPrivacyConsent());
   }

   @Test
   public void shouldReturnCorrectConsentRequiredStatusWhenSetBeforeInit() throws Exception {
      OneSignal.setRequiresUserPrivacyConsent(true);
      OneSignal.provideUserConsent(true);
      OneSignalInit();
      threadAndTaskWait();

      assertTrue(OneSignal.userProvidedPrivacyConsent());

      fastAppRestart();
      OneSignalInit();
      threadAndTaskWait();

      assertTrue(OneSignal.userProvidedPrivacyConsent());
   }


   // Functions to add observers (like addSubscriptionObserver) should continue
   // to work even if privacy consent has not been granted.
   @Test
   public void shouldAddSubscriptionObserverIfConsentNotGranted() throws Exception {
      OneSignal.setRequiresUserPrivacyConsent(true);
      OneSignalInit();
      threadAndTaskWait();

      OSSubscriptionObserver subscriptionObserver = new OSSubscriptionObserver() {
         @Override
         public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
            lastSubscriptionStateChanges = stateChanges;
            currentSubscription = stateChanges.getTo().getSubscribed();
         }
      };
      OneSignal.addSubscriptionObserver(subscriptionObserver);
      lastSubscriptionStateChanges = null;
      // Make sure garbage collection doesn't nuke any observers.
      Runtime.getRuntime().gc();

      OneSignal.provideUserConsent(true);
      threadAndTaskWait();

      // make sure the subscription observer was fired
      assertTrue(lastSubscriptionStateChanges.getTo().getSubscribed());
      assertFalse(lastSubscriptionStateChanges.getFrom().getSubscribed());
   }

   @Test
   public void shouldAddPermissionObserverIfConsentNotGranted() throws Exception {
      OneSignal.setRequiresUserPrivacyConsent(true);
      OneSignalInit();
      threadAndTaskWait();

      OSPermissionObserver permissionObserver = new OSPermissionObserver() {
         @Override
         public void onOSPermissionChanged(OSPermissionStateChanges stateChanges) {
            lastPermissionStateChanges = stateChanges;
            currentPermission = stateChanges.getTo().getEnabled();
         }
      };
      OneSignal.addPermissionObserver(permissionObserver);

      OneSignal.provideUserConsent(true);
      threadAndTaskWait();

      // make sure the permission observer was fired
      assertFalse(lastPermissionStateChanges.getFrom().getEnabled());
      assertTrue(lastPermissionStateChanges.getTo().getEnabled());
   }

   @Test
   public void shouldAddEmailSubscriptionObserverIfConsentNotGranted() throws Exception {
      OneSignal.setRequiresUserPrivacyConsent(true);
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

      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      // make sure the email subscription observer was fired
      assertNull(lastEmailSubscriptionStateChanges.getFrom().getEmailUserId());
      assertEquals("b007f967-98cc-11e4-bed1-118f05be4522", lastEmailSubscriptionStateChanges.getTo().getEmailUserId());
      assertEquals("josh@onesignal.com", lastEmailSubscriptionStateChanges.getTo().getEmailAddress());
      assertTrue(lastEmailSubscriptionStateChanges.getTo().getSubscribed());
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
      ShadowSystemClock.setCurrentTimeMillis(30 * 1000);
      blankActivityController.pause();
      threadAndTaskWait();

      // Press home button after 30 more sec, with a network hang
      blankActivityController.resume();
      ShadowSystemClock.setCurrentTimeMillis(60 * 1000);
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

   // ####### Unit Test Location       ########

   @Test
   @Config(shadows = {ShadowGoogleApiClientBuilder.class, ShadowGoogleApiClientCompatProxy.class, ShadowFusedLocationApiWrapper.class})
   public void shouldUpdateAllLocationFieldsWhenTimeStampChanges() throws Exception {
      ShadowApplication.getInstance().grantPermissions("android.permission.ACCESS_COARSE_LOCATION");
      OneSignalInit();
      threadAndTaskWait();
      assertEquals(1.0, ShadowOneSignalRestClient.lastPost.getDouble("lat"));
      assertEquals(2.0, ShadowOneSignalRestClient.lastPost.getDouble("long"));
      assertEquals(3.0, ShadowOneSignalRestClient.lastPost.getDouble("loc_acc"));
      assertEquals(0.0, ShadowOneSignalRestClient.lastPost.getDouble("loc_type"));

      ShadowOneSignalRestClient.lastPost = null;
      ShadowFusedLocationApiWrapper.lat = 30d;
      ShadowFusedLocationApiWrapper.log = 2.0d;
      ShadowFusedLocationApiWrapper.accuracy = 5.0f;
      ShadowFusedLocationApiWrapper.time = 2L;
      restartAppAndElapseTimeToNextSession();
      OneSignalInit();
      threadAndTaskWait();

      assertEquals(30.0, ShadowOneSignalRestClient.lastPost.getDouble("lat"));
      assertEquals(2.0, ShadowOneSignalRestClient.lastPost.getDouble("long"));
      assertEquals(5.0, ShadowOneSignalRestClient.lastPost.getDouble("loc_acc"));
      assertEquals(0.0, ShadowOneSignalRestClient.lastPost.getDouble("loc_type"));
   }

   @Test
   @Config(shadows = {ShadowOneSignal.class})
   @SuppressWarnings("unchecked") // getDeclaredMethod
   public void testLocationTimeout() throws Exception {
      //ShadowApplication.getInstance().grantPermissions(new String[]{"android.permission.YOUR_PERMISSION"});

      OneSignalInit();
      threadAndTaskWait();

      Class klass = Class.forName("com.onesignal.LocationGMS");
      Method method = klass.getDeclaredMethod("startFallBackThread");
      method.setAccessible(true);
      method.invoke(null);
      method = klass.getDeclaredMethod("fireFailedComplete");
      method.setAccessible(true);
      method.invoke(null);
      threadAndTaskWait();

      assertFalse(ShadowOneSignal.messages.contains("GoogleApiClient timedout"));
   }
   
   @Test
   @Config(shadows = {
            ShadowGoogleApiClientBuilder.class,
            ShadowGoogleApiClientCompatProxy.class,
            ShadowFusedLocationApiWrapper.class },
         sdk = 19)
   public void testLocationSchedule() throws Exception {
      ShadowApplication.getInstance().grantPermissions("android.permission.ACCESS_FINE_LOCATION");
      ShadowFusedLocationApiWrapper.lat = 1.0d; ShadowFusedLocationApiWrapper.log = 2.0d;
      ShadowFusedLocationApiWrapper.accuracy = 3.0f;
      ShadowFusedLocationApiWrapper.time = 12345L;

      // location if we have permission
      OneSignalInit();
      threadAndTaskWait();
      assertEquals(1.0, ShadowOneSignalRestClient.lastPost.optDouble("lat"));
      assertEquals(2.0, ShadowOneSignalRestClient.lastPost.optDouble("long"));
      assertEquals(3.0, ShadowOneSignalRestClient.lastPost.optDouble("loc_acc"));
      assertEquals(1, ShadowOneSignalRestClient.lastPost.optInt("loc_type"));
   
      // Checking make sure an update is scheduled.
      AlarmManager alarmManager = (AlarmManager)RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
      assertEquals(1, shadowOf(alarmManager).getScheduledAlarms().size());
      Intent intent = shadowOf(shadowOf(alarmManager).getNextScheduledAlarm().operation).getSavedIntent();
      assertEquals(SyncService.class, shadowOf(intent).getIntentClass());

      // Setting up a new point and testing it is sent
      Location fakeLocation = new Location("UnitTest");
      fakeLocation.setLatitude(1.1d);
      fakeLocation.setLongitude(2.2d);
      fakeLocation.setAccuracy(3.3f);
      fakeLocation.setTime(12346L);
      ShadowLocationUpdateListener.provideFakeLocation(fakeLocation);

      Robolectric.buildService(SyncService.class, intent).startCommand(0, 0);
      threadAndTaskWait();
      assertEquals(1.1d, ShadowOneSignalRestClient.lastPost.optDouble("lat"));
      assertEquals(2.2d, ShadowOneSignalRestClient.lastPost.optDouble("long"));
      assertEquals(3.3f, ShadowOneSignalRestClient.lastPost.opt("loc_acc"));
      
      assertEquals(false, ShadowOneSignalRestClient.lastPost.opt("loc_bg"));
      assertEquals("11111111-2222-3333-4444-555555555555", ShadowOneSignalRestClient.lastPost.opt("ad_id"));
   
      // Testing loc_bg
      blankActivityController.pause();
      threadAndTaskWait();
      fakeLocation.setTime(12347L);
      ShadowLocationUpdateListener.provideFakeLocation(fakeLocation);
      Robolectric.buildService(SyncService.class, intent).startCommand(0, 0);
      threadAndTaskWait();
      assertEquals(1.1d, ShadowOneSignalRestClient.lastPost.optDouble("lat"));
      assertEquals(2.2d, ShadowOneSignalRestClient.lastPost.optDouble("long"));
      assertEquals(3.3f, ShadowOneSignalRestClient.lastPost.opt("loc_acc"));
      assertEquals(true, ShadowOneSignalRestClient.lastPost.opt("loc_bg"));
      assertEquals(1, ShadowOneSignalRestClient.lastPost.optInt("loc_type"));
      assertEquals("11111111-2222-3333-4444-555555555555", ShadowOneSignalRestClient.lastPost.opt("ad_id"));
   }
   
   @Test
   @Config(shadows = {
            ShadowGoogleApiClientBuilder.class,
            ShadowGoogleApiClientCompatProxy.class,
            ShadowFusedLocationApiWrapper.class },
         sdk = 19)
   public void testLocationFromSyncAlarm() throws Exception {
      ShadowApplication.getInstance().grantPermissions("android.permission.ACCESS_COARSE_LOCATION");

      ShadowFusedLocationApiWrapper.lat = 1.1d;
      ShadowFusedLocationApiWrapper.log = 2.1d;
      ShadowFusedLocationApiWrapper.accuracy = 3.1f;
      ShadowFusedLocationApiWrapper.time = 12346L;

      OneSignalInit();
      threadAndTaskWait();

      fastAppRestart();
      AlarmManager alarmManager = (AlarmManager)RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
      shadowOf(alarmManager).getScheduledAlarms().clear();
      ShadowOneSignalRestClient.lastPost = null;

      ShadowFusedLocationApiWrapper.lat = 1.0;
      ShadowFusedLocationApiWrapper.log = 2.0d;
      ShadowFusedLocationApiWrapper.accuracy = 3.0f;
      ShadowFusedLocationApiWrapper.time = 12345L;

      blankActivityController.pause();
      Robolectric.buildService(SyncService.class, new Intent()).startCommand(0, 0);
      threadAndTaskWait();

      assertEquals(1.0, ShadowOneSignalRestClient.lastPost.optDouble("lat"));
      assertEquals(2.0, ShadowOneSignalRestClient.lastPost.optDouble("long"));
      assertEquals(3.0, ShadowOneSignalRestClient.lastPost.optDouble("loc_acc"));
      assertEquals(0, ShadowOneSignalRestClient.lastPost.optInt("loc_type"));
      assertEquals(12345L, ShadowOneSignalRestClient.lastPost.optInt("loc_time_stamp"));
      assertEquals(true, ShadowOneSignalRestClient.lastPost.opt("loc_bg"));
   
      // Checking make sure an update is scheduled.
      alarmManager = (AlarmManager)RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
      assertEquals(1, shadowOf(alarmManager).getScheduledAlarms().size());
      Intent intent = shadowOf(shadowOf(alarmManager).getNextScheduledAlarm().operation).getSavedIntent();
      assertEquals(SyncService.class, shadowOf(intent).getIntentClass());
      shadowOf(alarmManager).getScheduledAlarms().clear();
   }

   @Test
   @Config(shadows = {ShadowGoogleApiClientBuilder.class, ShadowGoogleApiClientCompatProxy.class, ShadowFusedLocationApiWrapper.class})
   public void shouldSendLocationToEmailRecord() throws Exception {
      ShadowApplication.getInstance().grantPermissions("android.permission.ACCESS_COARSE_LOCATION");

      OneSignalInit();
      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      JSONObject postEmailPayload = ShadowOneSignalRestClient.requests.get(2).payload;
      assertEquals(11, postEmailPayload.getInt("device_type"));
      assertEquals(1.0, postEmailPayload.getDouble("lat"));
      assertEquals(2.0, postEmailPayload.getDouble("long"));
      assertEquals(3.0, postEmailPayload.getDouble("loc_acc"));
      assertEquals(0.0, postEmailPayload.getDouble("loc_type"));
   }

   @Test
   @Config(shadows = {ShadowGoogleApiClientBuilder.class, ShadowGoogleApiClientCompatProxy.class, ShadowFusedLocationApiWrapper.class})
   public void shouldRegisterWhenPromptingAfterInit() throws Exception {
      ShadowApplication.getInstance().grantPermissions("android.permission.ACCESS_COARSE_LOCATION");
      ShadowGoogleApiClientCompatProxy.skipOnConnected = true;

      // Test promptLocation right after init race condition
      OneSignalInit();
      OneSignal.promptLocation();

      ShadowGoogleApiClientBuilder.connectionCallback.onConnected(null);
      threadAndTaskWait();

      ShadowOneSignalRestClient.Request request = ShadowOneSignalRestClient.requests.get(1);
      assertEquals(REST_METHOD.POST, request.method);
      assertEquals(1, request.payload.get("device_type"));
      assertEquals(ShadowPushRegistratorGCM.regId, request.payload.get("identifier"));
   }

   @Test
   @Config(shadows = {ShadowGoogleApiClientBuilder.class, ShadowGoogleApiClientCompatProxy.class, ShadowFusedLocationApiWrapper.class})
   public void shouldCallOnSessionEvenIfSyncJobStarted() throws Exception {
      ShadowApplication.getInstance().grantPermissions("android.permission.ACCESS_COARSE_LOCATION");

      OneSignalInit();
      threadAndTaskWait();

      restartAppAndElapseTimeToNextSession();
      ShadowGoogleApiClientCompatProxy.skipOnConnected = true;
      OneSignalInit();

      // TODO: Other, this sync seems to not omit PUT when there isn't anything to change....
      SyncJobService syncJobService = Robolectric.buildService(SyncJobService.class).create().get();
      syncJobService.onStartJob(null);
      Thread.sleep(1_000); // Short sleep to wait for the Thread in the job to run
      OneSignalPackagePrivateHelper.runAllNetworkRunnables();
      ShadowGoogleApiClientBuilder.connectionCallback.onConnected(null);
      threadAndTaskWait();

      ShadowOneSignalRestClient.Request request = ShadowOneSignalRestClient.requests.get(3);
      assertEquals(REST_METHOD.POST, request.method);
      assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511/on_session", request.url);
   }

   @Test
   public void testAppl() throws Exception {
      shadowOf(blankActivity.getPackageManager()).addPackage("org.robolectric.default");

      OneSignalInit();
      threadAndTaskWait();
      String baseKey = "pkgs";
      assertEquals(1, ShadowOneSignalRestClient.lastPost.getJSONArray(baseKey + "_a").length());

      flushBufferedSharedPrefs();
      final SharedPreferences prefs = blankActivity.getSharedPreferences(OneSignal.class.getSimpleName(), Context.MODE_PRIVATE);
      JSONObject syncValues = new JSONObject(prefs.getString("ONESIGNAL_USERSTATE_SYNCVALYES_CURRENT_STATE", null));
      assertFalse(syncValues.has(baseKey + "_a"));
      assertEquals(1, syncValues.getJSONArray(baseKey).length());

      JSONObject toSyncValues = new JSONObject(prefs.getString("ONESIGNAL_USERSTATE_SYNCVALYES_TOSYNC_STATE", null));
      assertFalse(toSyncValues.has(baseKey + "_a"));
      assertEquals(1, toSyncValues.getJSONArray(baseKey).length());

      restartAppAndElapseTimeToNextSession();
      ShadowOneSignalRestClient.lastPost = null;
      shadowOf(blankActivity.getPackageManager()).addPackage("org.test.app2");
      OneSignalInit();
      threadAndTaskWait();
      assertEquals(1, ShadowOneSignalRestClient.lastPost.getJSONArray(baseKey + "_a").length());

      restartAppAndElapseTimeToNextSession();
      ShadowOneSignalRestClient.lastPost = null;
      shadowOf(blankActivity.getPackageManager()).removePackage("org.test.app2");
      OneSignalInit();
      threadAndTaskWait();
      assertEquals(1, ShadowOneSignalRestClient.lastPost.getJSONArray(baseKey + "_d").length());

      restartAppAndElapseTimeToNextSession();
      ShadowOneSignalRestClient.lastPost = null;
      OneSignalInit();
      threadAndTaskWait();

      System.out.println("ShadowOneSignalRestClient.lastPost: " + ShadowOneSignalRestClient.lastPost);

      assertFalse(ShadowOneSignalRestClient.lastPost.has(baseKey + "_d"));
      assertFalse(ShadowOneSignalRestClient.lastPost.has(baseKey + "_a"));
   }

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


      ShadowOneSignalRestClient.nextSuccessResponse = "{\"id\":\"\",\"recipients\":0,\"errors\":[\"All included players are not subscribed\"]}";
      OneSignal.postNotification("{}", handler);
      assertNull(postNotificationSuccess);
      assertNotNull(postNotificationFailure);
   }


   @Test
   @Config(shadows = { ShadowRoboNotificationManager.class, ShadowBadgeCountUpdater.class })
   public void shouldCancelAndClearNotifications() throws Exception {
      ShadowRoboNotificationManager.notifications.clear();
      OneSignalInitFromApplication();
      threadAndTaskWait();

      // Create 2 notifications
      Bundle bundle = getBaseNotifBundle();
      OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);
      bundle = getBaseNotifBundle("UUID2");
      OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      // Test canceling
      Map<Integer, ShadowRoboNotificationManager.PostedNotification> postedNotifs = ShadowRoboNotificationManager.notifications;
      Iterator<Map.Entry<Integer, ShadowRoboNotificationManager.PostedNotification>> postedNotifsIterator = postedNotifs.entrySet().iterator();
      ShadowRoboNotificationManager.PostedNotification postedNotification = postedNotifsIterator.next().getValue();

      OneSignal.cancelNotification(postedNotification.id);
      assertEquals(1, ShadowBadgeCountUpdater.lastCount);
      assertEquals(1, ShadowRoboNotificationManager.notifications.size());

      OneSignal.clearOneSignalNotifications();
      assertEquals(0, ShadowBadgeCountUpdater.lastCount);
      assertEquals(0, ShadowRoboNotificationManager.notifications.size());

      // Make sure they are marked dismissed.
      SQLiteDatabase readableDb = OneSignalDbHelper.getInstance(blankActivity).getReadableDatabase();
      Cursor cursor = readableDb.query(OneSignalPackagePrivateHelper.NotificationTable.TABLE_NAME, new String[] { "created_time" },
          OneSignalPackagePrivateHelper.NotificationTable.COLUMN_NAME_DISMISSED + " = 1", null, null, null, null);
      assertEquals(2, cursor.getCount());
      cursor.close();
   }


   // ####### Unit test toJSONObject methods
   @Test
   public void testOSNotificationToJSONObject() throws Exception {
      OSNotification osNotification = createTestOSNotification();

      JSONObject testJsonObj = osNotification.toJSONObject();

      assertEquals("msg_body", testJsonObj.optJSONObject("payload").optString("body"));
      JSONObject firstActionButton = (JSONObject)testJsonObj.optJSONObject("payload").optJSONArray("actionButtons").get(0);
      assertEquals("text", firstActionButton.optString("text"));

      JSONObject additionalData = testJsonObj.optJSONObject("payload").optJSONObject("additionalData");
      assertEquals("bar", additionalData.optString("foo"));
   }

   @Test
   public void testOSNotificationOpenResultToJSONObject() throws Exception {
      OSNotificationOpenResult osNotificationOpenResult = new OSNotificationOpenResult();
      osNotificationOpenResult.notification = createTestOSNotification();
      osNotificationOpenResult.action = new OSNotificationAction();
      osNotificationOpenResult.action.type = OSNotificationAction.ActionType.Opened;

      JSONObject testJsonObj = osNotificationOpenResult.toJSONObject();

      JSONObject additionalData = testJsonObj.optJSONObject("notification").optJSONObject("payload").optJSONObject("additionalData");
      assertEquals("bar", additionalData.optString("foo"));
      
      JSONObject firstGroupedNotification = (JSONObject)testJsonObj.optJSONObject("notification").optJSONArray("groupedNotifications").get(0);
      assertEquals("collapseId1", firstGroupedNotification.optString("collapseId"));
   }
   
   @Test
   public void testNotificationOpenedProcessorHandlesEmptyIntent() {
      NotificationOpenedProcessor_processFromContext(blankActivity, new Intent());
   }
   
   @Test
   public void shouldOpenChromeTab() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      
      assertTrue(ShadowCustomTabsClient.bindCustomTabsServiceCalled);
      assertTrue(ShadowCustomTabsSession.lastURL.toString().contains("https://onesignal.com/android_frame.html?app_id=b2f7f966-d8cc-11e4-bed1-df8f05be55ba&user_id=a2f7f967-e8cc-11e4-bed1-118f05be4511&ad_id=11111111-2222-3333-4444-555555555555&cbs_id="));
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
            currentPermission = stateChanges.getTo().getEnabled();
         }
      };
      OneSignal.addPermissionObserver(permissionObserver);
      
      assertFalse(lastPermissionStateChanges.getFrom().getEnabled());
      assertTrue(lastPermissionStateChanges.getTo().getEnabled());
      // Test to make sure object was correct at the time of firing.
      assertTrue(currentPermission);
   }
   
   @Test
   public void shouldFirePermissionObserverWhenUserDisablesNotifications() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      
      OSPermissionObserver permissionObserver = new OSPermissionObserver() {
         @Override
         public void onOSPermissionChanged(OSPermissionStateChanges stateChanges) {
            lastPermissionStateChanges = stateChanges;
            currentPermission = stateChanges.getTo().getEnabled();
         }
      };
      OneSignal.addPermissionObserver(permissionObserver);
      lastPermissionStateChanges = null;
      // Make sure garbage collection doesn't nuke any observers.
      Runtime.getRuntime().gc();
      
      blankActivityController.pause();
      threadAndTaskWait();
      ShadowNotificationManagerCompat.enabled = false;
      blankActivityController.resume();
      threadAndTaskWait();
      
      assertTrue(lastPermissionStateChanges.getFrom().getEnabled());
      assertFalse(lastPermissionStateChanges.getTo().getEnabled());
      // Test to make sure object was correct at the time of firing.
      assertFalse(currentPermission);
      // unsubscribeWhenNotificationsAreDisabled is not set so don't send notification_types.
      assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }
   
   @Test
   public void shouldSetNotificationTypesToZeroWhenUnsubscribeWhenNotificationsAreDisabledIsEnabled() throws Exception {
      ShadowNotificationManagerCompat.enabled = false;
      OneSignal.startInit(blankActivity).unsubscribeWhenNotificationsAreDisabled(true).init();
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID);
      threadAndTaskWait();
      
      assertEquals(0, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
      
      blankActivityController.pause();
      threadAndTaskWait();
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
      ShadowBadgeCountUpdater.updateCount(1, blankActivity);
   
      blankActivityController.pause();
      threadAndTaskWait();
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
            currentSubscription = stateChanges.getTo().getSubscribed();
         }
      };
      OneSignal.addSubscriptionObserver(permissionObserver);
      
      assertFalse(lastSubscriptionStateChanges.getFrom().getSubscribed());
      assertTrue(lastSubscriptionStateChanges.getTo().getSubscribed());
      // Test to make sure object was correct at the time of firing.
      assertTrue(currentSubscription);
   }
   
   @Test
   public void shouldFireSubscriptionObserverWhenUserDisablesNotifications() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
   
      OSSubscriptionObserver subscriptionObserver = new OSSubscriptionObserver() {
         @Override
         public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
            lastSubscriptionStateChanges = stateChanges;
            currentSubscription = stateChanges.getTo().getSubscribed();
         }
      };
      OneSignal.addSubscriptionObserver(subscriptionObserver);
      lastSubscriptionStateChanges = null;
      // Make sure garbage collection doesn't nuke any observers.
      Runtime.getRuntime().gc();
      
      blankActivityController.pause();
      threadAndTaskWait();
      ShadowNotificationManagerCompat.enabled = false;
      blankActivityController.resume();
      threadAndTaskWait();
      
      assertTrue(lastSubscriptionStateChanges.getFrom().getSubscribed());
      assertFalse(lastSubscriptionStateChanges.getTo().getSubscribed());
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
            currentSubscription = stateChanges.getTo().getSubscribed();
         }
      };
      OneSignal.addSubscriptionObserver(permissionObserver);
      threadAndTaskWait();
      
      assertFalse(lastSubscriptionStateChanges.getFrom().getSubscribed());
      assertTrue(lastSubscriptionStateChanges.getTo().getSubscribed());
      // Test to make sure object was correct at the time of firing.
      assertTrue(currentSubscription);
      assertTrue(lastSubscriptionStateChanges.getTo().getUserSubscriptionSetting());
      assertEquals(ShadowPushRegistratorGCM.regId, lastSubscriptionStateChanges.getTo().getPushToken());
      assertEquals(ShadowOneSignalRestClient.pushUserId, lastSubscriptionStateChanges.getTo().getUserId());
   }
   
   @Test
   public void shouldNotFireSubscriptionObserverWhenChangesHappenIfRemoved() throws Exception {
      OneSignalInit();
      OSSubscriptionObserver permissionObserver = new OSSubscriptionObserver() {
         @Override
         public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
            lastSubscriptionStateChanges = stateChanges;
            currentSubscription = stateChanges.getTo().getSubscribed();
         }
      };
      OneSignal.addSubscriptionObserver(permissionObserver);
      lastSubscriptionStateChanges = null;
      OneSignal.removeSubscriptionObserver(permissionObserver);
      threadAndTaskWait();
      
      assertFalse(currentSubscription);
      assertNull(lastSubscriptionStateChanges);
   }

   private OSEmailSubscriptionStateChanges lastEmailSubscriptionStateChanges;

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
      assertTrue(lastEmailSubscriptionStateChanges.getTo().getSubscribed());
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

      assertFalse(lastEmailSubscriptionStateChanges.getTo().getSubscribed());
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

      restartAppAndElapseTimeToNextSession();

      OneSignalInit();
      threadAndTaskWait();
      lastEmailSubscriptionStateChanges = null;
      OneSignal.addEmailSubscriptionObserver(subscriptionObserver);
      threadAndTaskWait();

      assertNull(lastEmailSubscriptionStateChanges);
   }

   @Test
   public void shouldGetCorrectCurrentEmailSubscriptionState() throws Exception {
      OneSignalInit();
      OSEmailSubscriptionState emailSubscriptionState = OneSignal.getPermissionSubscriptionState().getEmailSubscriptionStatus();

      assertNull(emailSubscriptionState.getEmailUserId());
      assertNull(emailSubscriptionState.getEmailAddress());
      assertFalse(emailSubscriptionState.getSubscribed());

      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();
      emailSubscriptionState = OneSignal.getPermissionSubscriptionState().getEmailSubscriptionStatus();

      assertEquals("b007f967-98cc-11e4-bed1-118f05be4522", emailSubscriptionState.getEmailUserId());
      assertEquals("josh@onesignal.com", emailSubscriptionState.getEmailAddress());
      assertTrue(emailSubscriptionState.getSubscribed());
   }

   @Test
   public void shouldGetEmailUserIdAfterAppRestart() throws Exception {
      OneSignalInit();
      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      restartAppAndElapseTimeToNextSession();

      OneSignalInit();
      OSEmailSubscriptionState emailSubscriptionState = OneSignal.getPermissionSubscriptionState().getEmailSubscriptionStatus();
      assertEquals("josh@onesignal.com", emailSubscriptionState.getEmailAddress());
      assertNotNull(emailSubscriptionState.getEmailUserId());
   }
   
   @Test
   public void shouldReturnCorrectGetPermissionSubscriptionState() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      OSPermissionSubscriptionState permissionSubscriptionState = OneSignal.getPermissionSubscriptionState();
      assertTrue(permissionSubscriptionState.getPermissionStatus().getEnabled());
      assertTrue(permissionSubscriptionState.getSubscriptionStatus().getSubscribed());
   }

   @Test
   public void shouldSendPurchases() throws Exception {
      OneSignalInit();
      OneSignal.setEmail("josh@onesignal.com");
      threadAndTaskWait();

      JSONObject purchase = new JSONObject();
      purchase.put("sku", "com.test.sku");
      JSONArray purchases = new JSONArray();
      purchases.put(purchase);

      OneSignalPackagePrivateHelper.OneSignal_sendPurchases(purchases, false, null);
      threadAndTaskWait();

      String expectedPayload = "{\"app_id\":\"b2f7f966-d8cc-11e4-bed1-df8f05be55ba\",\"purchases\":[{\"sku\":\"com.test.sku\"}]}";
      ShadowOneSignalRestClient.Request pushPurchase = ShadowOneSignalRestClient.requests.get(4);
      assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511/on_purchase", pushPurchase.url);
      assertEquals(expectedPayload, pushPurchase.payload.toString());

      ShadowOneSignalRestClient.Request emailPurchase = ShadowOneSignalRestClient.requests.get(5);
      assertEquals("players/b007f967-98cc-11e4-bed1-118f05be4522/on_purchase", emailPurchase.url);
      assertEquals(expectedPayload, emailPurchase.payload.toString());
   }

   @Test
   @Config(shadows = { ShadowFirebaseAnalytics.class })
   public void shouldSendFirebaseAnalyticsNotificationOpen() throws Exception {
      ShadowOneSignalRestClient.paramExtras = new JSONObject().put("fba", true);
      OneSignalInit();
      threadAndTaskWait();

      JSONObject openPayload = new JSONObject();
      openPayload.put("title", "Test title");
      openPayload.put("alert", "Test Msg");
      openPayload.put("custom", new JSONObject("{ \"i\": \"UUID\" }"));
      OneSignal.handleNotificationOpen(blankActivity, new JSONArray().put(openPayload), false);

      assertEquals("os_notification_opened", ShadowFirebaseAnalytics.lastEventString);
      Bundle expectedBundle = new Bundle();
      expectedBundle.putString("notification_id", "UUID");
      expectedBundle.putString("medium", "notification");
      expectedBundle.putString("source", "OneSignal");
      expectedBundle.putString("campaign", "Test title");
      assertEquals(expectedBundle.toString(), ShadowFirebaseAnalytics.lastEventBundle.toString());

      // Assert that another open isn't trigger later when the unprocessed opens are fired
      ShadowFirebaseAnalytics.lastEventString = null;
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());
      assertNull(ShadowFirebaseAnalytics.lastEventString);
   }

   @Test
   @Config(shadows = { ShadowFirebaseAnalytics.class })
   public void shouldSendFirebaseAnalyticsNotificationReceived() throws Exception {
      ShadowOneSignalRestClient.paramExtras = new JSONObject().put("fba", true);
      OneSignalInit();
      threadAndTaskWait();

      JSONObject openPayload = new JSONObject();
      openPayload.put("title", "Test title");
      openPayload.put("alert", "Test Msg");
      openPayload.put("custom", new JSONObject("{ \"i\": \"UUID\" }"));
      NotificationBundleProcessor_Process(blankActivity, false, openPayload, null);

      assertEquals("os_notification_received", ShadowFirebaseAnalytics.lastEventString);
      Bundle expectedBundle = new Bundle();
      expectedBundle.putString("notification_id", "UUID");
      expectedBundle.putString("medium", "notification");
      expectedBundle.putString("source", "OneSignal");
      expectedBundle.putString("campaign", "Test title");
      assertEquals(expectedBundle.toString(), ShadowFirebaseAnalytics.lastEventBundle.toString());

      // Assert that another receive isn't trigger later when the unprocessed receives are fired
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler(), new OneSignal.NotificationReceivedHandler() {
         @Override
         public void notificationReceived(OSNotification notification) {
         }
      });
      threadAndTaskWait();

      ShadowFirebaseAnalytics.lastEventString = null;
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());
      assertNull(ShadowFirebaseAnalytics.lastEventString);
   }

   // ####### Unit test helper methods ########

   private static OSNotification createTestOSNotification() throws Exception {
      OSNotification osNotification = new OSNotification();

      osNotification.payload = new OSNotificationPayload();
      osNotification.payload.body = "msg_body";
      osNotification.payload.additionalData = new JSONObject("{\"foo\": \"bar\"}");
      osNotification.payload.actionButtons = new ArrayList<>();
      OSNotificationPayload.ActionButton actionButton = new OSNotificationPayload.ActionButton();
      actionButton.text = "text";
      actionButton.id = "id";
      osNotification.payload.actionButtons.add(actionButton);

      osNotification.displayType = OSNotification.DisplayType.None;

      osNotification.groupedNotifications = new ArrayList<>();
      OSNotificationPayload groupedPayload = new OSNotificationPayload();
      groupedPayload.collapseId = "collapseId1";
      osNotification.groupedNotifications.add(groupedPayload);

      return osNotification;
   }

   private static void threadAndTaskWait() throws Exception {
      TestHelpers.threadAndTaskWait();
   }

   private void OneSignalInit() {
      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.NONE);
      ShadowOSUtils.subscribableStatus = 1;
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID);
      blankActivityController.resume();
   }

   private void OneSignalInitFromApplication() {
      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.NONE);
      OneSignal.init(blankActivity.getApplicationContext(), "123456789", ONESIGNAL_APP_ID);
   }

   private void OneSignalInitWithBadProjectNum() {
      ShadowOSUtils.subscribableStatus = -6;
      OneSignal.init(blankActivity, "NOT A VALID Google project number", ONESIGNAL_APP_ID);
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

   private static int sessionCountOffset = 1;
   private static void restartAppAndElapseTimeToNextSession() {
      flushBufferedSharedPrefs();
      StaticResetHelper.restSetStaticFields();
      ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis() + 1_000 * 31 * sessionCountOffset++);
   }
}