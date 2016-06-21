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

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.os.Bundle;

import com.onesignal.BuildConfig;
import com.onesignal.OneSignal;
import com.onesignal.ShadowLocationGMS;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignal;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.ShadowPushRegistratorADM;
import com.onesignal.ShadowPushRegistratorGPS;
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
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ActivityController;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Config(packageName = "com.onesignal.example",
      constants = BuildConfig.class,
      shadows = {ShadowOneSignalRestClient.class, ShadowPushRegistratorGPS.class, ShadowPushRegistratorADM.class, ShadowOSUtils.class},
      sdk = 21)

@RunWith(CustomRobolectricTestRunner.class)
public class MainOneSignalClassRunner {

   private static final String ONESIGNAL_APP_ID = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";
   private static int testSleepTime;
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
      StaticResetHelper.restSetStaticFields();

      ShadowOneSignalRestClient.nextSuccessResponse = null;
      ShadowOneSignalRestClient.failNext = false;
      ShadowOneSignalRestClient.failAll = false;
      ShadowOneSignalRestClient.interruptibleDelayNext = false;
      ShadowOneSignalRestClient.networkCallCount = 0;
      ShadowOneSignalRestClient.testThread = Thread.currentThread();

      ShadowPushRegistratorGPS.fail = false;
      notificationOpenedMessage = null;
      lastGetTags = null;
   }

   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;

      testSleepTime = System.getenv("TRAVIS") != null ? 2000 : 200;

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
      StaticResetHelper.restSetStaticFields();

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
   public void testOpenFromNotificationWhenAppIsDead() throws Exception {
      OneSignal.handleNotificationOpened(blankActivity, new JSONArray("[{ \"alert\": \"Robo test message\", \"custom\": { \"i\": \"UUID\" } }]"), false);

      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
            notificationOpenedMessage = message;
         }
      });

      threadAndTaskWait();

      Assert.assertEquals("Robo test message", notificationOpenedMessage);
   }

   @Test
   public void shouldCorrectlyRemoveOpenedHandlerAndFireMissedOnesWhenAddedBack() throws Exception {
      OneSignal.NotificationOpenedHandler notifHandler = new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
            notificationOpenedMessage = message;
         }
      };
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, notifHandler);
      threadAndTaskWait();

      OneSignal.removeNotificationOpenedHandler();
      OneSignal.handleNotificationOpened(blankActivity, new JSONArray("[{ \"alert\": \"Robo test message\", \"custom\": { \"i\": \"UUID\" } }]"), false);
      Assert.assertNull(notificationOpenedMessage);

      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, notifHandler);
      Assert.assertEquals("Robo test message", notificationOpenedMessage);
   }

   @Test
   public void shouldNotFireNotificationOpenAgainAfterAppRestart() throws Exception {
      AddLauncherIntentFilter();
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
            notificationOpenedMessage = message;
         }
      });

      threadAndTaskWait();

      Bundle bundle = GenerateNotificationRunner.getBaseNotifBundle();
      OneSignalPackagePrivateHelper.NotificationBundleProcessor_ProcessFromGCMIntentService(blankActivity, bundle, null);

      threadAndTaskWait();

      notificationOpenedMessage = null;

      // Restart app - Should omit notification_types
      StaticResetHelper.restSetStaticFields();
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
            notificationOpenedMessage = message;
         }
      });

      threadAndTaskWait();

      Assert.assertEquals(null, notificationOpenedMessage);
   }

   @Test
   public void testOpenFromNotificationWhenAppIsInBackground() throws Exception {
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
            notificationOpenedMessage = message;
         }
      });
      Assert.assertNull(notificationOpenedMessage);

      OneSignal.handleNotificationOpened(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false);
      Assert.assertEquals("Test Msg", notificationOpenedMessage);
      threadWait();
   }

   @Test
   public void testOpeningLauncherActivity() throws Exception {
      AddLauncherIntentFilter();

      // From app launching normally
      Assert.assertNotNull(Shadows.shadowOf(blankActivity).getNextStartedActivity());

      OneSignal.handleNotificationOpened(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false);

      Assert.assertNotNull(Shadows.shadowOf(blankActivity).getNextStartedActivity());
      Assert.assertNull(Shadows.shadowOf(blankActivity).getNextStartedActivity());
   }

   @Test
   public void testOpeningLaunchUrl() throws Exception {
      // Removes app launch
      Shadows.shadowOf(blankActivity).getNextStartedActivity();

      // No OneSignal init here to test case where it is located in an Activity.

      OneSignal.handleNotificationOpened(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\", \"u\": \"http://google.com\" } }]"), false);

      Intent intent = Shadows.shadowOf(blankActivity).getNextStartedActivity();
      Assert.assertEquals("android.intent.action.VIEW", intent.getAction());
      Assert.assertEquals("http://google.com", intent.getData().toString());
      Assert.assertNull(Shadows.shadowOf(blankActivity).getNextStartedActivity());
   }

   @Test
   public void testOpeningLaunchUrlWithDisableDefault() throws Exception {
      ShadowApplication.getInstance().getAppManifest().getApplicationMetaData().put("com.onesignal.NotificationOpened.DEFAULT", "DISABLE");
      RuntimeEnvironment.getRobolectricPackageManager().addManifest(ShadowApplication.getInstance().getAppManifest(), ShadowApplication.getInstance().getResourceLoader());

      // Removes app launch
      Shadows.shadowOf(blankActivity).getNextStartedActivity();

      // No OneSignal init here to test case where it is located in an Activity.

      OneSignal.handleNotificationOpened(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\", \"u\": \"http://google.com\" } }]"), false);
      Assert.assertNull(Shadows.shadowOf(blankActivity).getNextStartedActivity());
   }

   @Test
   public void testDisableOpeningLauncherActivityOnNotifiOpen() throws Exception {
      ShadowApplication.getInstance().getAppManifest().getApplicationMetaData().put("com.onesignal.NotificationOpened.DEFAULT", "DISABLE");
      RuntimeEnvironment.getRobolectricPackageManager().addManifest(ShadowApplication.getInstance().getAppManifest(), ShadowApplication.getInstance().getResourceLoader());
      AddLauncherIntentFilter();

      // From app launching normally
      Assert.assertNotNull(Shadows.shadowOf(blankActivity).getNextStartedActivity());
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
            notificationOpenedMessage = message;
         }
      });
      Assert.assertNull(notificationOpenedMessage);

      OneSignal.handleNotificationOpened(blankActivity, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"), false);

      Assert.assertNull(Shadows.shadowOf(blankActivity).getNextStartedActivity());
      Assert.assertEquals("Test Msg", notificationOpenedMessage);
   }

   @Test
   public void testNotificationReceivedWhenAppInFocus() throws Exception {
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
            notificationOpenedMessage = message;
         }
      });
      threadAndTaskWait();
      Assert.assertNull(notificationOpenedMessage);

      OneSignalPackagePrivateHelper.GcmBroadcastReceiver_processBundle(blankActivity, GenerateNotificationRunner.getBaseNotifBundle());
      threadAndTaskWait();
      Assert.assertEquals("Robo test message", notificationOpenedMessage);
   }

   @Test
   public void testInvalidGoogleProjectNumber() throws Exception {
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
      StaticResetHelper.restSetStaticFields();
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

      StaticResetHelper.restSetStaticFields();

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
      StaticResetHelper.restSetStaticFields();
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Restart app again - Value synced last time so don't send again.
      StaticResetHelper.restSetStaticFields();
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
      StaticResetHelper.restSetStaticFields();
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
      StaticResetHelper.restSetStaticFields();
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
      ShadowConnectivityManager shadowConnectivityManager = Shadows.shadowOf(connectivityManager);
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
      Assert.assertEquals(1, ShadowOneSignalRestClient.networkCallCount);
      Assert.assertEquals(ONESIGNAL_APP_ID, ShadowOneSignalRestClient.lastPost.getString("app_id"));
      Assert.assertEquals("value1", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("test1"));
      Assert.assertEquals("value2", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("test2"));

      // Should omit sending repeated tags
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1\", \"test2\": \"value2\"}"));
      threadAndTaskWait();
      Assert.assertEquals(1, ShadowOneSignalRestClient.networkCallCount);
      Assert.assertNull(ShadowOneSignalRestClient.lastPost);

      // Should only send changed and new tags
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1.5\", \"test2\": \"value2\", \"test3\": \"value3\"}"));
      threadAndTaskWait();
      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      JSONObject sentTags = ShadowOneSignalRestClient.lastPost.getJSONObject("tags");
      Assert.assertEquals("value1.5", sentTags.getString("test1"));
      Assert.assertFalse(sentTags.has(("test2")));
      Assert.assertEquals("value3", sentTags.getString("test3"));
   }

   @Test
   public void shouldSendTagsWithRequestBatching() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(1, ShadowOneSignalRestClient.networkCallCount);
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1\"}"));
      OneSignal.sendTags(new JSONObject("{\"test2\": \"value2\"}"));

      GetTags();
      threadAndTaskWait();
      threadAndTaskWait();

      Assert.assertEquals("value1", lastGetTags.getString("test1"));
      Assert.assertEquals("value2", lastGetTags.getString("test2"));
      Assert.assertEquals(3, ShadowOneSignalRestClient.networkCallCount);
   }

   @Test
   public void testOldIntValues() throws Exception {
      final SharedPreferences prefs = blankActivity.getSharedPreferences(OneSignal.class.getSimpleName(), Context.MODE_PRIVATE);
      SharedPreferences.Editor editor = prefs.edit();
      editor.putString("ONESIGNAL_USERSTATE_SYNCVALYES_CURRENT_STATE", "{\"tags\": {\"int\": 123}}");
      editor.putString("ONESIGNAL_USERSTATE_SYNCVALYES_TOSYNC_STATE", "{\"tags\": {\"int\": 123}}");
      editor.commit();

      OneSignalInit();
      threadAndTaskWait();

      OneSignal.deleteTag("int");
      threadAndTaskWait();

      GetTags();

      final SharedPreferences prefs2 = blankActivity.getSharedPreferences(OneSignal.class.getSimpleName(), Context.MODE_PRIVATE);
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

   static boolean failedCurModTest;
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
      OneSignal.sendTag("key", "value");
      threadWait();

      OneSignalPackagePrivateHelper.SyncService_onTaskRemoved();
      OneSignalPackagePrivateHelper.resetRunnables();
      threadAndTaskWait();
      Assert.assertEquals(0, ShadowOneSignalRestClient.networkCallCount);

      StaticResetHelper.restSetStaticFields();

      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals("value", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("key"));
   }

   @Test
   public void shouldSyncPendingChangesFromSyncService() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      OneSignal.sendTag("key", "value");
      OneSignalPackagePrivateHelper.SyncService_onTaskRemoved();
      OneSignalPackagePrivateHelper.resetRunnables();
      threadAndTaskWait();
      Assert.assertEquals(1, ShadowOneSignalRestClient.networkCallCount);

      StaticResetHelper.restSetStaticFields();

      Service service = new SyncService();
      service.onCreate();
      threadAndTaskWait();
      Assert.assertEquals("value", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").getString("key"));
   }

   @Test
   public void shouldNotCrashIfOnTaskRemovedIsCalledBeforeInitIsDone() {
      OneSignalPackagePrivateHelper.SyncService_onTaskRemoved();
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
         public void onSuccess(JSONObject response) {
         }

         @Override
         public void onFailure(JSONObject response) {
         }
      });
      OneSignalInit();
      threadAndTaskWait();
   }

   // ####### DeleteTags Tests ######
   @Test
   public void testDeleteTags() throws Exception {
      OneSignalInit();
      OneSignal.sendTags("{\"str\": \"str1\", \"int\": 122, \"bool\": true}");
      OneSignal.deleteTag("int");
      GetTags();
      threadAndTaskWait(); threadAndTaskWait(); threadAndTaskWait();

      Assert.assertFalse(lastGetTags.has("int"));
      lastGetTags = null;

      // Should only send the tag we added back.
      OneSignal.sendTags("{\"str\": \"str1\", \"int\": 122, \"bool\": true}");
      threadAndTaskWait();
      Assert.assertEquals("{\"int\":\"122\"}", ShadowOneSignalRestClient.lastPost.getJSONObject("tags").toString());

      // Make sure a single delete works.
      OneSignal.deleteTag("int");
      GetTags();
      threadAndTaskWait(); threadAndTaskWait();
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
      GetTags();

      Assert.assertNull(lastGetTags);
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
      threadAndTaskWait(); threadAndTaskWait();

      Assert.assertEquals("value1", lastGetTags.getString("test1"));
      Assert.assertEquals("value2", lastGetTags.getString("test2"));
   }

   @Test
   public void shouldGetTagsFromServerOnFirstCall() throws Exception {
      OneSignalInit();
      threadAndTaskWait();

      ShadowOneSignalRestClient.nextSuccessResponse = "{\"tags\": {\"test1\": \"value1\", \"test2\": \"value2\"}}";
      GetTags();
      threadAndTaskWait(); threadAndTaskWait();

      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      Assert.assertEquals("value1", lastGetTags.getString("test1"));
      Assert.assertEquals("value2", lastGetTags.getString("test2"));

      GetTags();
      threadAndTaskWait();
      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
   }

   @Test
   public void getTagsDelayedAfterRegistering() throws Exception {
      OneSignalInit();
      GetTags();
      threadAndTaskWait();
      ShadowOneSignalRestClient.nextSuccessResponse = "{\"tags\": {\"test1\": \"value1\"}}";
      threadAndTaskWait(); threadAndTaskWait();

      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
      Assert.assertEquals("value1", lastGetTags.getString("test1"));
      Assert.assertTrue(ShadowOneSignalRestClient.lastUrl.contains(ShadowOneSignalRestClient.testUserId));
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
      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
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
   @Config(shadows = {ShadowLocationGMS.class})
   public void shouldUpdateAllLocationFieldsWhenAnyFieldsChange() throws Exception {
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(1.0, ShadowOneSignalRestClient.lastPost.getDouble("lat"));
      Assert.assertEquals(2.0, ShadowOneSignalRestClient.lastPost.getDouble("long"));
      Assert.assertEquals(3.0, ShadowOneSignalRestClient.lastPost.getDouble("loc_acc"));
      Assert.assertEquals(0.0, ShadowOneSignalRestClient.lastPost.getDouble("loc_type"));

      ShadowOneSignalRestClient.lastPost = null;
      StaticResetHelper.restSetStaticFields();
      ShadowLocationGMS.lat = 30.0;
      ShadowLocationGMS.accuracy = 5.0f;

      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(30.0, ShadowOneSignalRestClient.lastPost.getDouble("lat"));
      Assert.assertEquals(2.0, ShadowOneSignalRestClient.lastPost.getDouble("long"));
      Assert.assertEquals(5.0, ShadowOneSignalRestClient.lastPost.getDouble("loc_acc"));
      Assert.assertEquals(0.0, ShadowOneSignalRestClient.lastPost.getDouble("loc_type"));
   }

   @Test
   @Config(shadows = {ShadowOneSignal.class})
   public void testLocationTimeout() throws Exception {
      //ShadowApplication.getInstance().grantPermissions(new String[]{"android.permission.YOUR_PERMISSION"});

      OneSignalInit();
      threadAndTaskWait();

      Class klass = Class.forName("com.onesignal.LocationGMS");
      klass.getDeclaredMethod("startFallBackThread").invoke(null);
      klass.getDeclaredMethod("fireFailedComplete").invoke(null);
      threadAndTaskWait();

      Assert.assertFalse(ShadowOneSignal.messages.contains("GoogleApiClient timedout"));
   }

   @Test
   public void testAppl() throws Exception {
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

      StaticResetHelper.restSetStaticFields();
      RuntimeEnvironment.getRobolectricPackageManager().addPackage("org.test.app2");
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(1, ShadowOneSignalRestClient.lastPost.getJSONArray(baseKey + "_a").length());

      StaticResetHelper.restSetStaticFields();
      RuntimeEnvironment.getRobolectricPackageManager().removePackage("org.test.app2");
      OneSignalInit();
      threadAndTaskWait();
      Assert.assertEquals(1, ShadowOneSignalRestClient.lastPost.getJSONArray(baseKey + "_d").length());

      StaticResetHelper.restSetStaticFields();
      OneSignalInit();
      threadAndTaskWait();

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

   // ####### Unit test helper methods ########

   private static void threadWait() {
      try {Thread.sleep(1000);} catch (Throwable t) {}
   }

   private void threadAndTaskWait() {
      try {Thread.sleep(testSleepTime);} catch (Throwable t) {}
      OneSignalPackagePrivateHelper.runAllNetworkRunnables();
      OneSignalPackagePrivateHelper.runFocusRunnables();

      Robolectric.getForegroundThreadScheduler().runOneTask();
   }

   private void OneSignalInit() {
      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.NONE);
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID);
   }

   private void OneSignalInitWithBadProjectNum() {
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
   }
}