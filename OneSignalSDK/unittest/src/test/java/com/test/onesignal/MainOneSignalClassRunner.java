/**
 * Modified MIT License
 *
 * Copyright 2016 OneSignal
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
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.os.Bundle;

import com.onesignal.BuildConfig;
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
import com.onesignal.ShadowGoogleApiClientBuilder;
import com.onesignal.ShadowGoogleApiClientCompatProxy;
import com.onesignal.ShadowFusedLocationApiWrapper;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignal;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.ShadowPushRegistratorGPS;
import com.onesignal.ShadowRoboNotificationManager;
import com.onesignal.StaticResetHelper;
import com.onesignal.SyncService;
import com.onesignal.example.BlankActivity;

import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ActivityController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.onesignal.OneSignalPackagePrivateHelper.GcmBroadcastReceiver_processBundle;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_Process;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationOpenedProcessor_processFromContext;
import static com.onesignal.OneSignalPackagePrivateHelper.bundleAsJSONObject;
import static com.test.onesignal.GenerateNotificationRunner.getBaseNotifBundle;
import static org.robolectric.Shadows.shadowOf;

@Config(packageName = "com.onesignal.example",
        shadows = {ShadowOneSignalRestClient.class,
                   ShadowPushRegistratorGPS.class,
                   ShadowOSUtils.class,
                   ShadowAdvertisingIdProviderGPS.class,
                   ShadowCustomTabsClient.class, ShadowCustomTabsSession.class,
                   ShadowNotificationManagerCompat.class},
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

      TestHelpers.betweenTestsCleanup();
   }

   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;

      Field OneSignal_CurrentSubscription = OneSignal.class.getDeclaredField("subscribableStatus");
      OneSignal_CurrentSubscription.setAccessible(true);

      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
      StaticResetHelper.saveStaticValues();
   }

   @Before
   public void beforeEachTest() throws Exception {
      blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
      blankActivity = blankActivityController.get();
      RemoveDisableNotificationOpenedToManifest();

      cleanUp();
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
      Assert.assertNotNull(ShadowOneSignalRestClient.lastPost);

      ShadowOneSignalRestClient.lastPost = null;
      restartAppAndElapseTimeToNextSession();

      // Restart app, should not send onSession automatically
      OneSignal.init(RuntimeEnvironment.application, "123456789", ONESIGNAL_APP_ID);
      threadAndTaskWait();
      Assert.assertNull(ShadowOneSignalRestClient.lastPost);

      // Starting of first Activity should trigger onSession
      blankActivityController.resume();
      threadAndTaskWait();
      Assert.assertNotNull(ShadowOneSignalRestClient.lastPost);
   }

   @Test
   public void testOnSessionCalledOnlyOncePer30Sec() throws Exception {
      // Will call create
      ShadowSystemClock.setCurrentTimeMillis(60 * 60 * 1000);
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      Assert.assertEquals("players", ShadowOneSignalRestClient.lastUrl);

      // Shouldn't call on_session if just resuming app with a short delay
      blankActivityController.pause();
      threadAndTaskWait();
      ShadowOneSignalRestClient.lastUrl = null;
      blankActivityController.resume();
      threadAndTaskWait();
      Assert.assertNull(ShadowOneSignalRestClient.lastUrl);

      // Or when restarting the app quickly.
      ShadowOneSignalRestClient.lastPost = null;
      StaticResetHelper.restSetStaticFields();
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      threadAndTaskWait();
      Assert.assertNull(ShadowOneSignalRestClient.lastUrl);

      blankActivityController.pause();
      threadAndTaskWait();
      ShadowSystemClock.setCurrentTimeMillis(121 * 60 * 1000);
      ShadowOneSignalRestClient.lastUrl = null;
      blankActivityController.resume();
      threadAndTaskWait();
      Assert.assertTrue(ShadowOneSignalRestClient.lastUrl.matches("players/.*/on_session"));
      Assert.assertEquals("{\"app_id\":\"b2f7f966-d8cc-11e4-bed1-df8f05be55ba\"}", ShadowOneSignalRestClient.lastPost.toString());
   }

   @Test
   public void testAlwaysUseRemoteProjectNumberOverLocal() throws Exception {
      ShadowSystemClock.setCurrentTimeMillis(60 * 60 * 1000);

      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals("87654321", ShadowPushRegistratorGPS.lastProjectNumber);

      // A 2nd init call
      OneSignalInit();

      blankActivityController.pause();
      threadAndTaskWait();
      ShadowSystemClock.setCurrentTimeMillis(121 * 60 * 1000);
      blankActivityController.resume();
      threadAndTaskWait();

      // Make sure when we try to register again before our on_session call it is with the remote
      // project number instead of the local one.
      Assert.assertEquals("87654321", ShadowPushRegistratorGPS.lastProjectNumber);
   }

   @Test
   public void testPutStillCalledOnChanges() throws Exception {
      // Will call create
      ShadowSystemClock.setCurrentTimeMillis(60 * 60 * 1000);
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      Assert.assertEquals("players", ShadowOneSignalRestClient.lastUrl);

      // Shouldn't call on_session if just resuming app with a short delay
      blankActivityController.pause();
      threadAndTaskWait();
      ShadowOneSignalRestClient.lastUrl = null;
      blankActivityController.resume();
      threadAndTaskWait();
      Assert.assertNull(ShadowOneSignalRestClient.lastUrl);
      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);

      ShadowOSUtils.carrierName = "test2";

      // Should make PUT call with changes on app restart
      ShadowOneSignalRestClient.lastPost = null;
      StaticResetHelper.restSetStaticFields();
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      threadAndTaskWait();

      Assert.assertEquals(4, ShadowOneSignalRestClient.networkCallCount);
      GetIdsAvailable();
      Assert.assertEquals("players/" + callBackUseId, ShadowOneSignalRestClient.lastUrl);
      Assert.assertEquals("{\"carrier\":\"test2\",\"app_id\":\"b2f7f966-d8cc-11e4-bed1-df8f05be55ba\"}", ShadowOneSignalRestClient.lastPost.toString());
   }


   @Test
   public void testPutCallsMadeWhenUserStateChangesOnAppResume() throws Exception {
      // Will call create
      ShadowSystemClock.setCurrentTimeMillis(60 * 60 * 1000);
      OneSignalInit();
      threadAndTaskWait();
      blankActivityController.resume();
      Assert.assertEquals("players", ShadowOneSignalRestClient.lastUrl);
   }

   @Test
   public void testOpenFromNotificationWhenAppIsDead() throws Exception {
      OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Robo test message\", \"custom\": { \"i\": \"UUID\" } }]"), false);

      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());

      threadAndTaskWait();

      Assert.assertEquals("Robo test message", notificationOpenedMessage);
   }

   @Test
   public void testAndroidParamsProjectNumberOverridesLocal() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      
      Assert.assertNotSame("123456789", ShadowPushRegistratorGPS.lastProjectNumber);
   }
   
   @Test
   public void testNullProjectNumberSetsErrorType() throws Exception {
      // Get call will not return a Google project number if it hasn't been entered on the OneSignal dashboard.
      ShadowOneSignalRestClient.nextSuccessResponse = "{\"awl_list\": {}}";
      // Don't fire the mock callback, it will be done from the real class.
      ShadowPushRegistratorGPS.skipComplete = true;
      
      OneSignal.init(blankActivity, null, ONESIGNAL_APP_ID);
      threadAndTaskWait();
      
      Assert.assertEquals(-6, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
   }

   @Test
   public void shouldCorrectlyRemoveOpenedHandlerAndFireMissedOnesWhenAddedBack() throws Exception {
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());
      threadAndTaskWait();

      OneSignal.removeNotificationOpenedHandler();
      OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Robo test message\", \"custom\": { \"i\": \"UUID\" } }]"), false);
      Assert.assertNull(notificationOpenedMessage);

      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());
      Assert.assertEquals("Robo test message", notificationOpenedMessage);
   }

   @Test
   public void shouldNotFireNotificationOpenAgainAfterAppRestart() throws Exception {
      AddLauncherIntentFilter();
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

      Assert.assertEquals(null, notificationOpenedMessage);
   }

   @Test
   public void testOpenFromNotificationWhenAppIsInBackground() throws Exception {
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());
      Assert.assertNull(notificationOpenedMessage);

      OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false);
      Assert.assertEquals("Test Msg", notificationOpenedMessage);
      threadAndTaskWait();
   }

   @Test
   public void testOpeningLauncherActivity() throws Exception {
      AddLauncherIntentFilter();

      // From app launching normally
      Assert.assertNotNull(shadowOf(blankActivity).getNextStartedActivity());

      OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false);

      Assert.assertNotNull(shadowOf(blankActivity).getNextStartedActivity());
      Assert.assertNull(shadowOf(blankActivity).getNextStartedActivity());
   }

   @Test
   public void testOpeningLaunchUrl() throws Exception {
      // Removes app launch
      shadowOf(blankActivity).getNextStartedActivity();

      // No OneSignal init here to test case where it is located in an Activity.

      OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\", \"u\": \"http://google.com\" } }]"), false);

      Intent intent = shadowOf(blankActivity).getNextStartedActivity();
      Assert.assertEquals("android.intent.action.VIEW", intent.getAction());
      Assert.assertEquals("http://google.com", intent.getData().toString());
      Assert.assertNull(shadowOf(blankActivity).getNextStartedActivity());
   }

   @Test
   public void testOpeningLaunchUrlWithDisableDefault() throws Exception {
      AddDisableNotificationOpenedToManifest();

      // Removes app launch
      shadowOf(blankActivity).getNextStartedActivity();

      // No OneSignal init here to test case where it is located in an Activity.

      OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\", \"u\": \"http://google.com\" } }]"), false);
      Assert.assertNull(shadowOf(blankActivity).getNextStartedActivity());
   }

   @Test
   public void testDisableOpeningLauncherActivityOnNotifiOpen() throws Exception {
      AddDisableNotificationOpenedToManifest();
      AddLauncherIntentFilter();

      // From app launching normally
      Assert.assertNotNull(shadowOf(blankActivity).getNextStartedActivity());
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, getNotificationOpenedHandler());
      Assert.assertNull(notificationOpenedMessage);

      OneSignal.handleNotificationOpen(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false);

      Assert.assertNull(shadowOf(blankActivity).getNextStartedActivity());
      Assert.assertEquals("Test Msg", notificationOpenedMessage);
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
      threadAndTaskWait(); threadAndTaskWait();
      Assert.assertEquals(null, notificationOpenedMessage);
      Assert.assertFalse(processResult);
      // NotificationBundleProcessor.Process(...) will be called if processResult is true as a service
      NotificationBundleProcessor_Process(blankActivity, false, bundleAsJSONObject(bundle), null);
      Assert.assertEquals("Robo test message", notificationReceivedBody);
      Assert.assertFalse(0 == androidNotificationId);

      // Don't fire for duplicates
      notificationOpenedMessage = null;
      notificationReceivedBody = null;
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.None);
      Assert.assertNull(notificationOpenedMessage);

      GcmBroadcastReceiver_processBundle(blankActivity, bundle);
      threadAndTaskWait();
      Assert.assertEquals(null, notificationOpenedMessage);
      Assert.assertEquals(null, notificationReceivedBody);

      // Test that only NotificationReceivedHandler fires
      OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.None);
      bundle = getBaseNotifBundle("UUID2");
      notificationOpenedMessage = null;
      notificationReceivedBody = null;

      GcmBroadcastReceiver_processBundle(blankActivity, bundle);
      threadAndTaskWait(); threadAndTaskWait();
      Assert.assertEquals(null, notificationOpenedMessage);
      Assert.assertNull(notificationOpenedMessage);
      Assert.assertEquals("Robo test message", notificationReceivedBody);
   }

   @Test
   @Config(shadows = {ShadowBadgeCountUpdater.class})
   public void testBadgeClearOnFirstStart() throws Exception {
      ShadowBadgeCountUpdater.lastCount = -1;

      // First run should set badge to 0
      OneSignal.init(RuntimeEnvironment.application, "123456789", ONESIGNAL_APP_ID);
      threadAndTaskWait();
      Assert.assertEquals(0, ShadowBadgeCountUpdater.lastCount);

      // Resume should have no effect on badges.
      ShadowBadgeCountUpdater.lastCount = -1;
      blankActivityController.resume();
      threadAndTaskWait();
      Assert.assertEquals(-1, ShadowBadgeCountUpdater.lastCount);

      // Nor an app restart
      StaticResetHelper.restSetStaticFields();
      OneSignal.init(RuntimeEnvironment.application, "123456789", ONESIGNAL_APP_ID);
      threadAndTaskWait();
      Assert.assertEquals(-1, ShadowBadgeCountUpdater.lastCount);
   }

   @Test
   public void testUnsubscribeStatusShouldBeSetIfGCMErrored() throws Exception {
      ShadowPushRegistratorGPS.fail = true;
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(-7, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
   }

   @Test
   public void testInvalidGoogleProjectNumberWithSuccessfulRegisterResponse() throws Exception {
      GetIdsAvailable();
      // A more real test would be "missing support library" but bad project number is an easier setup
      //   and is testing the same logic.
      OneSignalInitWithBadProjectNum();

      threadAndTaskWait();
      Robolectric.getForegroundThreadScheduler().runOneTask();
      Assert.assertEquals(-6, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Test that idsAvailable still fires
      Assert.assertEquals(ShadowOneSignalRestClient.testUserId, callBackUseId);
   }

   @Test
   public void testGMSErrorsAfterSuccessfulSubscribeDoNotUnsubscribeTheDevice() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));

      ShadowOneSignalRestClient.lastPost = null;
      restartAppAndElapseTimeToNextSession();

      ShadowPushRegistratorGPS.fail = true;
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void testInvalidGoogleProjectNumberWithFailedRegisterResponse() throws Exception {
      // Ensures lower number notification_types do not over right higher numbered ones.
      ShadowPushRegistratorGPS.fail = true;
      GetIdsAvailable();
      OneSignalInitWithBadProjectNum();

      threadAndTaskWait();
      Robolectric.getForegroundThreadScheduler().runOneTask();
      Assert.assertEquals(-6, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Test that idsAvailable still fires
      Assert.assertEquals(ShadowOneSignalRestClient.testUserId, callBackUseId);
   }

   @Test
   public void testUnsubcribedShouldMakeRegIdNullToIdsAvailable() throws Exception {
      GetIdsAvailable();
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(ShadowPushRegistratorGPS.regId, ShadowOneSignalRestClient.lastPost.getString("identifier"));

      Robolectric.getForegroundThreadScheduler().runOneTask();
      Assert.assertEquals(ShadowPushRegistratorGPS.regId, getCallBackRegId);

      OneSignal.setSubscription(false);
      GetIdsAvailable();
      threadAndTaskWait();
      Assert.assertNull(getCallBackRegId);
   }

   @Test
   public void testSetSubscriptionShouldNotOverrideSubscribeError() throws Exception {
      OneSignalInitWithBadProjectNum();
      threadAndTaskWait();

      // Should not try to update server
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.setSubscription(true);
      Assert.assertNull(ShadowOneSignalRestClient.lastPost);

      // Restart app - Should omit notification_types
      restartAppAndElapseTimeToNextSession();
      OneSignalInitWithBadProjectNum();
      threadAndTaskWait();
      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void shouldNotResetSubscriptionOnSession() throws Exception {
      OneSignalInit();
      OneSignal.setSubscription(false);
      threadAndTaskWait();
      Assert.assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      restartAppAndElapseTimeToNextSession();

      OneSignalInit();
      threadAndTaskWait();
      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
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
      Assert.assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Restart app again - Value synced last time so don't send again.
      restartAppAndElapseTimeToNextSession();
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void shouldUpdateNotificationTypesCorrectlyEvenWhenSetSubscriptionIsCalledInAnErrorState() throws Exception {
      OneSignalInitWithBadProjectNum();
      threadAndTaskWait();
      OneSignal.setSubscription(true);

      // Restart app - Should send subscribe with on_session call.
      StaticResetHelper.restSetStaticFields();
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(1, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
   }


   @Test
   public void shouldAllowMultipleSetSubscription() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      OneSignal.setSubscription(false);
      threadAndTaskWait();

      Assert.assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Should not resend same value
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.setSubscription(false);
      Assert.assertNull(ShadowOneSignalRestClient.lastPost);



      OneSignal.setSubscription(true);
      threadAndTaskWait();
      Assert.assertEquals(1, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Should not resend same value
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.setSubscription(true);
      threadAndTaskWait();
      Assert.assertNull(ShadowOneSignalRestClient.lastPost);
   }

   private static boolean userIdWasNull = false;
   @Test
   public void shouldNotFireIdsAvailableWithoutUserId() throws Exception {
      ShadowOneSignalRestClient.failNext = true;
      ShadowPushRegistratorGPS.fail = true;

      OneSignal.idsAvailable(new OneSignal.IdsAvailableHandler() {
         @Override
         public void idsAvailable(String userId, String registrationId) {
            if (userId == null)
               userIdWasNull = true;
         }
      });

      OneSignalInit();
      Assert.assertFalse(userIdWasNull);
      threadAndTaskWait();
   }

   @Test
   public void testGCMTimeOutThenSuccessesLater() throws Exception {
      // Init with a bad connection to Google.
      ShadowPushRegistratorGPS.fail = true;
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("identifier"));

      // Registers for GCM after a retry
      ShadowPushRegistratorGPS.fail = false;
      ShadowPushRegistratorGPS.manualFireRegisterForPush();
      threadAndTaskWait();
      Assert.assertEquals(ShadowPushRegistratorGPS.regId, ShadowOneSignalRestClient.lastPost.getString("identifier"));

      // Cold restart app, should not send the same identifier again.
      ShadowOneSignalRestClient.lastPost = null;
      restartAppAndElapseTimeToNextSession();
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("identifier"));
   }

   @Test
   public void testChangeAppId() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      int normalCreateFieldCount = ShadowOneSignalRestClient.lastPost.length();
      StaticResetHelper.restSetStaticFields();
      OneSignal.init(blankActivity, "123456789", "99f7f966-d8cc-11e4-bed1-df8f05be55b2");
      threadAndTaskWait();

      Assert.assertEquals(normalCreateFieldCount, ShadowOneSignalRestClient.lastPost.length());
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

      Assert.assertEquals(normalCreateFieldCount, ShadowOneSignalRestClient.lastPost.length());


      // Developer deletes users again from dashboard while app is running.
      ShadowOneSignalRestClient.lastPost = null;
      ShadowOneSignalRestClient.failNext = true;
      ShadowOneSignalRestClient.failResponse = "{\"errors\":[\"No user with this id found\"]}";
      OneSignal.sendTag("key1", "value1");
      threadAndTaskWait();

      Assert.assertEquals(normalCreateFieldCount, ShadowOneSignalRestClient.lastPost.length() - 1);
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
      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      Assert.assertEquals(ONESIGNAL_APP_ID, ShadowOneSignalRestClient.lastPost.getString("app_id"));
      Assert.assertEquals("value1", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("test1"));
      Assert.assertEquals("value2", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("test2"));

      // Should omit sending repeated tags
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1\", \"test2\": \"value2\"}"));
      threadAndTaskWait();
      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      Assert.assertNull(ShadowOneSignalRestClient.lastPost);

      // Should only send changed and new tags
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1.5\", \"test2\": \"value2\", \"test3\": \"value3\"}"));
      threadAndTaskWait();
      Assert.assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
      JSONObject sentTags = ShadowOneSignalRestClient.lastPost.getJSONObject("tags");
      Assert.assertEquals("value1.5", sentTags.getString("test1"));
      Assert.assertFalse(sentTags.has(("test2")));
      Assert.assertEquals("value3", sentTags.getString("test3"));
   }

   @Test
   public void shouldNotSendTagOnRepeats() throws Exception {
      OneSignalInit();
      OneSignal.sendTag("test1", "value1");
      threadAndTaskWait();
      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      Assert.assertEquals(ONESIGNAL_APP_ID, ShadowOneSignalRestClient.lastPost.getString("app_id"));
      Assert.assertEquals("value1", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("test1"));

      // Should only send new tag
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.sendTag("test2", "value2");
      threadAndTaskWait();
      Assert.assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
      Assert.assertEquals("value2", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("test2"));

      // Should not resend first tags
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.sendTag("test1", "value1");
      threadAndTaskWait();
      Assert.assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
      Assert.assertNull(ShadowOneSignalRestClient.lastPost);
   }

   @Test
   public void shouldSendTagsWithRequestBatching() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1\"}"));
      OneSignal.sendTags(new JSONObject("{\"test2\": \"value2\"}"));

      GetTags();
      threadAndTaskWait();
      threadAndTaskWait();

      Assert.assertEquals("value1", lastGetTags.getString("test1"));
      Assert.assertEquals("value2", lastGetTags.getString("test2"));
      Assert.assertEquals(4, ShadowOneSignalRestClient.networkCallCount);
   }

   @Test
   public void shouldNotAttemptToSendTagsBeforeGettingPlayerId() throws Exception {
      ShadowPushRegistratorGPS.skipComplete = true;
      OneSignalInit();
      GetIdsAvailable();

      threadAndTaskWait();
      Assert.assertEquals(1, ShadowOneSignalRestClient.networkCallCount);

      // Should not attempt to make a network call yet as we don't have a player_id
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1\"}"));
      threadAndTaskWait();
      Assert.assertEquals(1, ShadowOneSignalRestClient.networkCallCount);

      ShadowPushRegistratorGPS.fireLastCallback();
      threadAndTaskWait(); threadAndTaskWait();
      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      Assert.assertNotNull(callBackUseId);
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

      Assert.assertNull(lastGetTags);
   }

   @Test
   public void testSendTagNonStringValues() throws Exception {
      OneSignalInit();
      OneSignal.sendTags("{\"int\": 122, \"bool\": true, \"null\": null, \"array\": [123], \"object\": {}}");
      GetTags();
      threadAndTaskWait(); threadAndTaskWait(); threadAndTaskWait();

      Assert.assertEquals(String.class, lastGetTags.get("int").getClass());
      Assert.assertEquals("122", lastGetTags.get("int"));
      Assert.assertEquals(String.class, lastGetTags.get("bool").getClass());
      Assert.assertEquals("true", lastGetTags.get("bool"));

      // null should be the same as a blank string.
      Assert.assertFalse(lastGetTags.has("null"));

      Assert.assertFalse(lastGetTags.has("array"));
      Assert.assertFalse(lastGetTags.has("object"));
   }

   private static boolean failedCurModTest;
   @Test
   public void testSendTagsConcurrentModificationException() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      for(int a = 0; a < 10; a++) {
         List<Thread> threadList = new ArrayList<>(30);
         for (int i = 0; i < 30; i++) {
            Thread lastThread = newSendTagTestThread(Thread.currentThread(), i);
            lastThread.start();
            threadList.add(lastThread);
            Assert.assertFalse(failedCurModTest);
         }

         for(Thread thread : threadList)
            thread.join();
         Assert.assertFalse(failedCurModTest);
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
//                  OneSignalPackagePrivateHelper.OneSignalStateSynchronizer_syncUserState(false);
               }
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
   public void shouldSaveToSyncIfKilledBeforeDelayedCompare() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      Service service = Robolectric.buildService(SyncService.class).create().get();
      
      OneSignal.sendTag("key", "value");

      // Swipe app away from Recent Apps list, should save unsynced data.
      OneSignalPackagePrivateHelper.SyncService_onTaskRemoved(service);
      OneSignalPackagePrivateHelper.resetRunnables();

      // Network call for android params and player create should have been made.
      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);

      // App is re-opened.
      StaticResetHelper.restSetStaticFields();
      OneSignalInit();
      threadAndTaskWait();

      // Un-synced tag should now sync.
      Assert.assertEquals("value", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("key"));
   }

   @Test
   public void shouldSyncPendingChangesFromSyncService() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      
      OneSignal.sendTag("key", "value");
   
      // App is swiped away
      Service service = Robolectric.buildService(SyncService.class).create().get();
      OneSignalPackagePrivateHelper.SyncService_onTaskRemoved(service);
      
      OneSignalPackagePrivateHelper.resetRunnables();
      
      threadAndTaskWait();
      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      
      StaticResetHelper.restSetStaticFields();
   
      // There were unsynced changes so service should have been scheduled for a restart.
      AlarmManager alarmManager = (AlarmManager)RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
      Assert.assertEquals(1, shadowOf(alarmManager).getScheduledAlarms().size());
      Assert.assertEquals(SyncService.class, shadowOf(shadowOf(shadowOf(alarmManager).getNextScheduledAlarm().operation).getSavedIntent()).getIntentClass());
      shadowOf(alarmManager).getScheduledAlarms().clear();
   
      // Service is restarted
      Intent intent = new Intent();
      intent.putExtra("task", 1); // TASK_SYNC
      service = Robolectric.buildService(SyncService.class, intent).startCommand(0, 0).get();

      threadAndTaskWait();
      Assert.assertEquals("value", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("key"));
      Assert.assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
   
      OneSignalInit();
      threadAndTaskWait();
      
      // No new changes, don't schedule another restart.
      OneSignalPackagePrivateHelper.SyncService_onTaskRemoved(service);
      Assert.assertEquals(0, shadowOf(alarmManager).getScheduledAlarms().size());
   }

   @Test
   public void shouldNotCrashIfOnTaskRemovedIsCalledBeforeInitIsDone() {
      Service service = Robolectric.buildService(SyncService.class).create().get();
      OneSignalPackagePrivateHelper.SyncService_onTaskRemoved(service);
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
      Assert.assertEquals(inFocusDisplayOption, OneSignal.OSInFocusDisplayOption.Notification);

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

      Assert.assertFalse(lastGetTags.has("int"));
      lastGetTags = null;

      // Should only send the tag we added back.
      OneSignal.sendTags("{\"str\": \"str1\", \"int\": 122, \"bool\": true}");
      threadAndTaskWait();
      Assert.assertEquals("{\"int\":\"122\"}", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").toString());

      // Make sure a single delete works.
      OneSignal.deleteTag("int");
      GetTags();
      threadAndTaskWait();
      Assert.assertFalse(lastGetTags.has("int"));

      // Delete all other tags, the 'tags' key should not exists in local storage.
      OneSignal.deleteTags(Arrays.asList("bool", "str"));
      threadAndTaskWait();
      final SharedPreferences prefs = blankActivity.getSharedPreferences(OneSignal.class.getSimpleName(), Context.MODE_PRIVATE);
      String syncValues = prefs.getString("ONESIGNAL_USERSTATE_SYNCVALYES_CURRENT_STATE", null);
      Assert.assertFalse(new JSONObject(syncValues).has("tags"));
   }


   @Test
   public void testDeleteTagsAfterSync() throws Exception {
      OneSignalInit();
      OneSignal.sendTags("{\"foo\": \"bar\", \"fuz\": \"baz\"}");
      threadAndTaskWait();
      Assert.assertEquals("bar", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").get("foo"));
      Assert.assertEquals("baz", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").get("fuz"));

      OneSignal.deleteTags("[\"foo\", \"fuz\"]");
      threadAndTaskWait();
      Assert.assertEquals("", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").get("foo"));
      Assert.assertEquals("", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").get("fuz"));

      GetTags();

      Assert.assertNull(lastGetTags);

      final SharedPreferences prefs = blankActivity.getSharedPreferences(OneSignal.class.getSimpleName(), Context.MODE_PRIVATE);
      JSONObject syncValues = new JSONObject(prefs.getString("ONESIGNAL_USERSTATE_SYNCVALYES_CURRENT_STATE", null));
      Assert.assertFalse(syncValues.has("tags"));
   }

   @Test
   public void testOmitDeletesOfNonExistingKeys() throws Exception {
      OneSignalInit();
      OneSignal.deleteTag("this_key_does_not_exist");
      threadAndTaskWait();

      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("tags"));
   }


   // ####### GetTags Tests ########
   @Test
   public void testGetTagsWithNoTagsShouldBeNull() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      
      GetTags();
      threadAndTaskWait();
      
      Assert.assertNull(lastGetTags);
      String lastUrl = ShadowOneSignalRestClient.lastUrl;
      Assert.assertEquals("?app_id=" + ONESIGNAL_APP_ID, lastUrl.substring(lastUrl.lastIndexOf("?")));
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

      Assert.assertEquals("value1", lastGetTags.getString("test1"));
      Assert.assertEquals("value2", lastGetTags.getString("test2"));
   }

   @Test
   public void shouldGetTagsFromServerOnFirstCallAndMergeLocalAndRemote() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      ShadowOneSignalRestClient.nextSuccessfulGETResponse = "{\"tags\": {\"test1\": \"value1\", \"test2\": \"value2\"}}";
      GetTags();
      threadAndTaskWait();

      Assert.assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
      Assert.assertEquals("value1", lastGetTags.getString("test1"));
      Assert.assertEquals("value2", lastGetTags.getString("test2"));

      // Makes sure a 2nd call to GetTags correctly uses existing tags and merges new local changes.
      lastGetTags = null;
      OneSignal.sendTag("test3", "value3");
      GetTags();
      threadAndTaskWait();
      Assert.assertEquals("value1", lastGetTags.getString("test1"));
      Assert.assertEquals("value2", lastGetTags.getString("test2"));
      Assert.assertEquals("value3", lastGetTags.getString("test3"));
      threadAndTaskWait();
      // Also ensure only 1 network call is made to just send the new tags only.
      Assert.assertEquals(4, ShadowOneSignalRestClient.networkCallCount);

      StaticResetHelper.restSetStaticFields();
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
      Assert.assertEquals("value1", lastGetTags.getString("test1"));
      System.out.println("lastGetTags: " + lastGetTags);
      Assert.assertFalse(lastGetTags.has("test2"));
      Assert.assertEquals("ShouldOverride", lastGetTags.getString("test3"));
      Assert.assertEquals("value4", lastGetTags.getString("test4"));
      Assert.assertEquals(8, ShadowOneSignalRestClient.networkCallCount);

      // Sending 'test1' and 'test3' could be omitted but this is a negotiable performance hit.
      Assert.assertEquals("{\"test1\":\"value1\",\"test2\":\"\",\"test3\":\"ShouldOverride\",\"test4\":\"value4\"}",
                           ShadowOneSignalRestClient.lastPost.optJSONObject("tags").toString());
   }

   @Test
   public void getTagsDelayedAfterRegistering() throws Exception {
      ShadowOneSignalRestClient.nextSuccessfulGETResponse = "{\"tags\": {\"test1\": \"value1\"}}";

      OneSignalInit();
      GetTags();
      threadAndTaskWait(); threadAndTaskWait(); threadAndTaskWait();

      Assert.assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
      Assert.assertEquals("value1", lastGetTags.getString("test1"));
      Assert.assertTrue(ShadowOneSignalRestClient.lastUrl.contains(ShadowOneSignalRestClient.testUserId));
   }


   @Test
   public void syncHashedEmailTest() throws Exception {
      OneSignalInit();
      // Casing should be forced to lower.
      OneSignal.syncHashedEmail("Test@tEst.CoM");
      threadAndTaskWait();
      Assert.assertEquals("b642b4217b34b1e8d3bd915fc65c4452" ,ShadowOneSignalRestClient.lastPost.getString("em_m"));
      Assert.assertEquals("a6ad00ac113a19d953efb91820d8788e2263b28a" ,ShadowOneSignalRestClient.lastPost.getString("em_s"));

      // Test email update
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.syncHashedEmail("test@test2.com");
      threadAndTaskWait();
      Assert.assertEquals("3e1163777d25d2b935057c3ae393efee" ,ShadowOneSignalRestClient.lastPost.getString("em_m"));
      Assert.assertEquals("69e9ca5af84bc88bc185136cd6f782ee889be5c8" ,ShadowOneSignalRestClient.lastPost.getString("em_s"));

      // Test trim on email
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.syncHashedEmail(" test@test2.com ");
      threadAndTaskWait();
      Assert.assertNull(ShadowOneSignalRestClient.lastPost);

      // Test invalid email.
      OneSignal.syncHashedEmail("aaaaaa");
      threadAndTaskWait();
      Assert.assertNull(ShadowOneSignalRestClient.lastPost);

      // Test invalid email.
      OneSignal.syncHashedEmail(null);
      threadAndTaskWait();
      Assert.assertNull(ShadowOneSignalRestClient.lastPost);
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
      Assert.assertEquals(60, ShadowOneSignalRestClient.lastPost.getInt("active_time"));
      Assert.assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
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

      Assert.assertEquals(60, ShadowOneSignalRestClient.lastPost.getInt("active_time"));
      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
   }
   */

   // ####### Unit Test Location       ########

   @Test
   @Config(shadows = {ShadowGoogleApiClientBuilder.class, ShadowGoogleApiClientCompatProxy.class, ShadowFusedLocationApiWrapper.class})
   public void shouldUpdateAllLocationFieldsWhenAnyFieldsChange() throws Exception {
      ShadowApplication.getInstance().grantPermissions("android.permission.ACCESS_COARSE_LOCATION");
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(1.0, ShadowOneSignalRestClient.lastPost.getDouble("lat"));
      Assert.assertEquals(2.0, ShadowOneSignalRestClient.lastPost.getDouble("long"));
      Assert.assertEquals(3.0, ShadowOneSignalRestClient.lastPost.getDouble("loc_acc"));
      Assert.assertEquals(0.0, ShadowOneSignalRestClient.lastPost.getDouble("loc_type"));

      ShadowOneSignalRestClient.lastPost = null;
      StaticResetHelper.restSetStaticFields();
      ShadowFusedLocationApiWrapper.lat = 30.0;
      ShadowFusedLocationApiWrapper.accuracy = 5.0f;

      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(30.0, ShadowOneSignalRestClient.lastPost.getDouble("lat"));
      Assert.assertEquals(2.0, ShadowOneSignalRestClient.lastPost.getDouble("long"));
      Assert.assertEquals(5.0, ShadowOneSignalRestClient.lastPost.getDouble("loc_acc"));
      Assert.assertEquals(0.0, ShadowOneSignalRestClient.lastPost.getDouble("loc_type"));
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

      Assert.assertFalse(ShadowOneSignal.messages.contains("GoogleApiClient timedout"));
   }
   
   @Test
   @Config(shadows = {ShadowGoogleApiClientBuilder.class, ShadowGoogleApiClientCompatProxy.class, ShadowFusedLocationApiWrapper.class})
   public void testLocationSchedule() throws Exception {
      ShadowApplication.getInstance().grantPermissions("android.permission.ACCESS_FINE_LOCATION");
      ShadowFusedLocationApiWrapper.lat = 1.0d; ShadowFusedLocationApiWrapper.log = 2.0d;
      ShadowFusedLocationApiWrapper.accuracy = 3.0f;
      ShadowFusedLocationApiWrapper.time = 12345L;
      
      
      // location if we have permission
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(1.0, ShadowOneSignalRestClient.lastPost.optDouble("lat"));
      Assert.assertEquals(2.0, ShadowOneSignalRestClient.lastPost.optDouble("long"));
      Assert.assertEquals(3.0, ShadowOneSignalRestClient.lastPost.optDouble("loc_acc"));
      Assert.assertEquals(1, ShadowOneSignalRestClient.lastPost.optInt("loc_type"));
   
      // Checking make sure an update is scheduled.
      AlarmManager alarmManager = (AlarmManager)RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
      Assert.assertEquals(1, shadowOf(alarmManager).getScheduledAlarms().size());
      Intent intent = shadowOf(shadowOf(alarmManager).getNextScheduledAlarm().operation).getSavedIntent();
      Assert.assertEquals(SyncService.class, shadowOf(intent).getIntentClass());
   
      // Setting up a new point and testing it is sent
      ShadowFusedLocationApiWrapper.lat = 1.1d; ShadowFusedLocationApiWrapper.log = 2.2d;
      ShadowFusedLocationApiWrapper.accuracy = 3.3f;
      ShadowFusedLocationApiWrapper.time = 12346L;
      Robolectric.buildService(SyncService.class, intent).startCommand(0, 0);
      threadAndTaskWait();
      Assert.assertEquals(1.1d, ShadowOneSignalRestClient.lastPost.optDouble("lat"));
      Assert.assertEquals(2.2d, ShadowOneSignalRestClient.lastPost.optDouble("long"));
      Assert.assertEquals(3.3f, ShadowOneSignalRestClient.lastPost.opt("loc_acc"));
      
      Assert.assertEquals(false, ShadowOneSignalRestClient.lastPost.opt("loc_bg"));
      Assert.assertEquals("11111111-2222-3333-4444-555555555555", ShadowOneSignalRestClient.lastPost.opt("ad_id"));
   
      // Testing loc_bg
      blankActivityController.pause();
      threadAndTaskWait();
      ShadowFusedLocationApiWrapper.time = 12347L;
      Robolectric.buildService(SyncService.class, intent).startCommand(0, 0);
      threadAndTaskWait();
      Assert.assertEquals(1.1d, ShadowOneSignalRestClient.lastPost.optDouble("lat"));
      Assert.assertEquals(2.2d, ShadowOneSignalRestClient.lastPost.optDouble("long"));
      Assert.assertEquals(3.3f, ShadowOneSignalRestClient.lastPost.opt("loc_acc"));
      Assert.assertEquals(true, ShadowOneSignalRestClient.lastPost.opt("loc_bg"));
      Assert.assertEquals(1, ShadowOneSignalRestClient.lastPost.optInt("loc_type"));
      Assert.assertEquals("11111111-2222-3333-4444-555555555555", ShadowOneSignalRestClient.lastPost.opt("ad_id"));
   }
   
   @Test
   @Config(shadows = {ShadowGoogleApiClientBuilder.class, ShadowGoogleApiClientCompatProxy.class, ShadowFusedLocationApiWrapper.class})
   public void testLocationFromSyncAlarm() throws Exception {
      ShadowFusedLocationApiWrapper.lat = 1.0d; ShadowFusedLocationApiWrapper.log = 2.0d;
      ShadowFusedLocationApiWrapper.accuracy = 3.0f;
      ShadowFusedLocationApiWrapper.time = 12345L;
      
      OneSignalInit();
      threadAndTaskWait();
      StaticResetHelper.restSetStaticFields();
      AlarmManager alarmManager = (AlarmManager)RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
      shadowOf(alarmManager).getScheduledAlarms().clear();
      ShadowOneSignalRestClient.lastPost = null;
      
      ShadowApplication.getInstance().grantPermissions("android.permission.ACCESS_COARSE_LOCATION");
   
      Intent intent = new Intent();
      intent.putExtra("task", 1); // Sync
      Robolectric.buildService(SyncService.class, intent).startCommand(0, 0);
      threadAndTaskWait();
      Assert.assertEquals(1.0, ShadowOneSignalRestClient.lastPost.optDouble("lat"));
      Assert.assertEquals(2.0, ShadowOneSignalRestClient.lastPost.optDouble("long"));
      Assert.assertEquals(3.0, ShadowOneSignalRestClient.lastPost.optDouble("loc_acc"));
      Assert.assertEquals(0, ShadowOneSignalRestClient.lastPost.optInt("loc_type"));
      Assert.assertEquals(true, ShadowOneSignalRestClient.lastPost.opt("loc_bg"));
   
      // Checking make sure an update is scheduled.
      alarmManager = (AlarmManager)RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
      Assert.assertEquals(1, shadowOf(alarmManager).getScheduledAlarms().size());
      intent = shadowOf(shadowOf(alarmManager).getNextScheduledAlarm().operation).getSavedIntent();
      Assert.assertEquals(SyncService.class, shadowOf(intent).getIntentClass());
      shadowOf(alarmManager).getScheduledAlarms().clear();
   }

   @Test
   public void testAppl() throws Exception {
      AddLauncherIntentFilter();
      RuntimeEnvironment.getRobolectricPackageManager().addPackage("org.robolectric.default");

      OneSignalInit();
      threadAndTaskWait();
      String baseKey = "pkgs";
      Assert.assertEquals(1, ShadowOneSignalRestClient.lastPost.getJSONArray(baseKey + "_a").length());

      final SharedPreferences prefs = blankActivity.getSharedPreferences(OneSignal.class.getSimpleName(), Context.MODE_PRIVATE);
      JSONObject syncValues = new JSONObject(prefs.getString("ONESIGNAL_USERSTATE_SYNCVALYES_CURRENT_STATE", null));
      Assert.assertFalse(syncValues.has(baseKey + "_a"));
      Assert.assertEquals(1, syncValues.getJSONArray(baseKey).length());

      JSONObject toSyncValues = new JSONObject(prefs.getString("ONESIGNAL_USERSTATE_SYNCVALYES_TOSYNC_STATE", null));
      Assert.assertFalse(toSyncValues.has(baseKey + "_a"));
      Assert.assertEquals(1, toSyncValues.getJSONArray(baseKey).length());

      restartAppAndElapseTimeToNextSession();
      ShadowOneSignalRestClient.lastPost = null;
      RuntimeEnvironment.getRobolectricPackageManager().addPackage("org.test.app2");
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(1, ShadowOneSignalRestClient.lastPost.getJSONArray(baseKey + "_a").length());

      restartAppAndElapseTimeToNextSession();
      ShadowOneSignalRestClient.lastPost = null;
      RuntimeEnvironment.getRobolectricPackageManager().removePackage("org.test.app2");
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(1, ShadowOneSignalRestClient.lastPost.getJSONArray(baseKey + "_d").length());

      restartAppAndElapseTimeToNextSession();
      ShadowOneSignalRestClient.lastPost = null;
      OneSignalInit();
      threadAndTaskWait();

      System.out.println("ShadowOneSignalRestClient.lastPost: " + ShadowOneSignalRestClient.lastPost);

      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has(baseKey + "_d"));
      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has(baseKey + "_a"));
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
      Assert.assertNotNull(postNotificationSuccess);
      Assert.assertNull(postNotificationFailure);
      postNotificationSuccess = postNotificationFailure = null;


      ShadowOneSignalRestClient.nextSuccessResponse = "{\"id\":\"\",\"recipients\":0,\"errors\":[\"All included players are not subscribed\"]}";
      OneSignal.postNotification("{}", handler);
      Assert.assertNull(postNotificationSuccess);
      Assert.assertNotNull(postNotificationFailure);
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
      Assert.assertEquals(1, ShadowBadgeCountUpdater.lastCount);
      Assert.assertEquals(1, ShadowRoboNotificationManager.notifications.size());

      OneSignal.clearOneSignalNotifications();
      Assert.assertEquals(0, ShadowBadgeCountUpdater.lastCount);
      Assert.assertEquals(0, ShadowRoboNotificationManager.notifications.size());

      // Make sure they are marked dismissed.
      SQLiteDatabase readableDb = OneSignalDbHelper.getInstance(blankActivity).getReadableDatabase();
      Cursor cursor = readableDb.query(OneSignalPackagePrivateHelper.NotificationTable.TABLE_NAME, new String[] { "created_time" },
          OneSignalPackagePrivateHelper.NotificationTable.COLUMN_NAME_DISMISSED + " = 1", null, null, null, null);
      Assert.assertEquals(2, cursor.getCount());
      cursor.close();
   }


   // ####### Unit test toJSONObject methods
   @Test
   public void testOSNotificationToJSONObject() throws Exception {
      OSNotification osNotification = createTestOSNotification();

      JSONObject testJsonObj = osNotification.toJSONObject();

      Assert.assertEquals("msg_body", testJsonObj.optJSONObject("payload").optString("body"));
      JSONObject firstActionButton = (JSONObject)testJsonObj.optJSONObject("payload").optJSONArray("actionButtons").get(0);
      Assert.assertEquals("text", firstActionButton.optString("text"));

      JSONObject additionalData = testJsonObj.optJSONObject("payload").optJSONObject("additionalData");
      Assert.assertEquals("bar", additionalData.optString("foo"));
   }

   @Test
   public void testOSNotificationOpenResultToJSONObject() throws Exception {
      OSNotificationOpenResult osNotificationOpenResult = new OSNotificationOpenResult();
      osNotificationOpenResult.notification = createTestOSNotification();
      osNotificationOpenResult.action = new OSNotificationAction();
      osNotificationOpenResult.action.type = OSNotificationAction.ActionType.Opened;

      JSONObject testJsonObj = osNotificationOpenResult.toJSONObject();

      JSONObject additionalData = testJsonObj.optJSONObject("notification").optJSONObject("payload").optJSONObject("additionalData");
      Assert.assertEquals("bar", additionalData.optString("foo"));
      
      JSONObject firstGroupedNotification = (JSONObject)testJsonObj.optJSONObject("notification").optJSONArray("groupedNotifications").get(0);
      Assert.assertEquals("collapseId1", firstGroupedNotification.optString("collapseId"));
   }
   
   @Test
   public void testNotificationOpenedProcessorHandlesEmptyIntent() {
      NotificationOpenedProcessor_processFromContext(blankActivity, new Intent());
   }
   
   @Test
   public void shouldOpenChromeTab() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      
      Assert.assertTrue(ShadowCustomTabsClient.bindCustomTabsServiceCalled);
      Assert.assertTrue(ShadowCustomTabsSession.lastURL.toString().contains("https://onesignal.com/android_frame.html?app_id=b2f7f966-d8cc-11e4-bed1-df8f05be55ba&user_id=a2f7f967-e8cc-11e4-bed1-118f05be4511&ad_id=11111111-2222-3333-4444-555555555555&cbs_id="));
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
      
      Assert.assertFalse(lastPermissionStateChanges.getFrom().getEnabled());
      Assert.assertTrue(lastPermissionStateChanges.getTo().getEnabled());
      // Test to make sure object was correct at the time of firing.
      Assert.assertTrue(currentPermission);
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
      
      Assert.assertTrue(lastPermissionStateChanges.getFrom().getEnabled());
      Assert.assertFalse(lastPermissionStateChanges.getTo().getEnabled());
      // Test to make sure object was correct at the time of firing.
      Assert.assertFalse(currentPermission);
      // unsubscribeWhenNotificationsAreDisabled is not set so don't send notification_types.
      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }
   
   @Test
   public void shouldSetNotificationTypesToZeroWhenUnsubscribeWhenNotificationsAreDisabledIsEnabled() throws Exception {
      ShadowNotificationManagerCompat.enabled = false;
      OneSignal.startInit(blankActivity).unsubscribeWhenNotificationsAreDisabled(true).init();
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID);
      threadAndTaskWait();
      
      Assert.assertEquals(0, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
      
      blankActivityController.pause();
      threadAndTaskWait();
      ShadowNotificationManagerCompat.enabled = true;
      blankActivityController.resume();
      threadAndTaskWait();
      Assert.assertEquals(1, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
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
      
      Assert.assertEquals(0, ShadowBadgeCountUpdater.lastCount);
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
      
      Assert.assertFalse(lastSubscriptionStateChanges.getFrom().getSubscribed());
      Assert.assertTrue(lastSubscriptionStateChanges.getTo().getSubscribed());
      // Test to make sure object was correct at the time of firing.
      Assert.assertTrue(currentSubscription);
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
      
      Assert.assertTrue(lastSubscriptionStateChanges.getFrom().getSubscribed());
      Assert.assertFalse(lastSubscriptionStateChanges.getTo().getSubscribed());
      // Test to make sure object was correct at the time of firing.
      Assert.assertFalse(currentSubscription);
      // unsubscribeWhenNotificationsAreDisabled is not set so don't send notification_types.
      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
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
      
      Assert.assertFalse(lastSubscriptionStateChanges.getFrom().getSubscribed());
      Assert.assertTrue(lastSubscriptionStateChanges.getTo().getSubscribed());
      // Test to make sure object was correct at the time of firing.
      Assert.assertTrue(currentSubscription);
      Assert.assertTrue(lastSubscriptionStateChanges.getTo().getUserSubscriptionSetting());
      Assert.assertEquals(ShadowPushRegistratorGPS.regId, lastSubscriptionStateChanges.getTo().getPushToken());
      Assert.assertEquals(ShadowOneSignalRestClient.testUserId, lastSubscriptionStateChanges.getTo().getUserId());
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
      
      Assert.assertFalse(currentSubscription);
      Assert.assertNull(lastSubscriptionStateChanges);
   }
   
   @Test
   public void shouldReturnCorrectGetPermissionSubscriptionState() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      OSPermissionSubscriptionState permissionSubscriptionState = OneSignal.getPermissionSubscriptionState();
      Assert.assertTrue(permissionSubscriptionState.getPermissionStatus().getEnabled());
      Assert.assertTrue(permissionSubscriptionState.getSubscriptionStatus().getSubscribed());
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

      RuntimeEnvironment.getRobolectricPackageManager().addResolveInfoForIntent(launchIntent, resolveInfo);
      RuntimeEnvironment.getRobolectricPackageManager().addManifest(shadowOf(RuntimeEnvironment.application).getAppManifest(), 0);
   }

   private static void AddDisableNotificationOpenedToManifest() {
      ShadowApplication.getInstance().getAppManifest().getApplicationMetaData().put("com.onesignal.NotificationOpened.DEFAULT", "DISABLE");
      RuntimeEnvironment.getRobolectricPackageManager().addManifest(shadowOf(RuntimeEnvironment.application).getAppManifest(), 0);
   }

   private static void RemoveDisableNotificationOpenedToManifest() {
      ShadowApplication.getInstance().getAppManifest().getApplicationMetaData().remove("com.onesignal.NotificationOpened.DEFAULT");
      RuntimeEnvironment.getRobolectricPackageManager().addManifest(shadowOf(RuntimeEnvironment.application).getAppManifest(), 0);
   }

   private static int sessionCountOffset = 1;
   private static void restartAppAndElapseTimeToNextSession() {
      StaticResetHelper.restSetStaticFields();
      ShadowSystemClock.setCurrentTimeMillis(System.currentTimeMillis() + 1000 * 31 * sessionCountOffset++);
   }
}