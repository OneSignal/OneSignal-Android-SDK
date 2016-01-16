/**
 * Modified MIT License
 *
 * Copyright 2015 OneSignal
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
import android.net.ConnectivityManager;
import android.os.Bundle;

import com.onesignal.BuildConfig;
import com.onesignal.NotificationBundleProcessor;
import com.onesignal.OneSignal;
import com.onesignal.ShadowLocationGMS;
import com.onesignal.ShadowOSUtils;
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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ActivityController;

import java.lang.reflect.Field;

@Config(packageName = "com.onesignal.example",
      constants = BuildConfig.class,
      shadows = {ShadowOneSignalRestClient.class, ShadowPushRegistratorGPS.class, ShadowPushRegistratorADM.class, ShadowOSUtils.class, ShadowLocationGMS.class},
      sdk = 21)

@RunWith(CustomRobolectricTestRunner.class)
public class MainOneSignalClassRunner {

   private static final String ONESIGNAL_APP_ID = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";
   private static Field OneSignal_CurrentSubscription;
   private Activity blankActivity;
   private static String callBackUseId, getCallBackRegId;
   private static String notificationOpenedMessage;
   private static JSONObject lastGetTags;
   private ActivityController<BlankActivity> blankActivityController;
   
   public static void GetIdsAvailable() {
      OneSignal.idsAvailable(new OneSignal.IdsAvailableHandler() {
         @Override
         public void idsAvailable(String userId, String registrationId) {
            callBackUseId = userId;
            getCallBackRegId = registrationId;
         }
      });
   }

   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;

      OneSignal_CurrentSubscription = OneSignal.class.getDeclaredField("subscribableStatus");
      OneSignal_CurrentSubscription.setAccessible(true);

      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
      StaticResetHelper.saveStaticValues();
   }

   @Before // Before each test
   public void beforeEachTest() throws Exception {
      callBackUseId = getCallBackRegId = null;
      StaticResetHelper.restSetStaticFields();
      blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
      blankActivity = blankActivityController.get();

      ShadowOneSignalRestClient.failNext = false;
      ShadowOneSignalRestClient.failAll = false;
      ShadowOneSignalRestClient.interruptibleDelayNext = false;
      ShadowOneSignalRestClient.networkCallCount = 0;
      ShadowOneSignalRestClient.testThread = Thread.currentThread();

      ShadowPushRegistratorGPS.fail = false;
      notificationOpenedMessage = null;
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
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
            notificationOpenedMessage = message;
         }
      });

      threadAndTaskWait();

      Bundle bundle = GenerateNotificationRunner.getBaseNotifBundle();
      NotificationBundleProcessor.Process(blankActivity, bundle);

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
   public void testNotificationReceivedWhenAppInFocus() throws Exception {
      OneSignal.init(blankActivity, "123456789", ONESIGNAL_APP_ID, new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
            notificationOpenedMessage = message;
         }
      });
      threadAndTaskWait();
      Assert.assertNull(notificationOpenedMessage);

      NotificationBundleProcessor.Process(blankActivity, GenerateNotificationRunner.getBaseNotifBundle());
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
   public void testUnsubcribeShouldMakeRegIdNullToIdsAvailable() throws Exception {
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

      System.out.println("ShadowOneSignalRestClient.lastPost: " + ShadowOneSignalRestClient.lastPost);
      Assert.assertEquals(normalCreateFieldCount, ShadowOneSignalRestClient.lastPost.length());


      // Developer deletes users again from dashboard while app is running.
      ShadowOneSignalRestClient.lastPost = null;
      ShadowOneSignalRestClient.failNext = true;
      ShadowOneSignalRestClient.failResponse = "{\"errors\":[\"No user with this id found\"]}";
      OneSignal.sendTag("key1", "value1");
      threadAndTaskWait();

      System.out.println("ShadowOneSignalRestClient.lastPost: " + ShadowOneSignalRestClient.lastPost);
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

      OneSignal.getTags(new OneSignal.GetTagsHandler() {
         @Override
         public void tagsAvailable(JSONObject tags) {
            lastGetTags = tags;
            System.out.println("tags: " + tags);
         }
      });
      threadAndTaskWait();
      threadAndTaskWait();

      System.out.println("lastGetTags: " + lastGetTags);
      Assert.assertEquals("value1", lastGetTags.getString("test1"));
      Assert.assertEquals("value2", lastGetTags.getString("test2"));
      Assert.assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
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

   // ####### GetTags Tests ########

   @Test
   public void shouldGetTags() throws Exception {
      OneSignalInit();
      OneSignal.sendTags(new JSONObject("{\"test1\": \"value1\", \"test2\": \"value2\"}"));
      threadAndTaskWait();
      OneSignal.getTags(new OneSignal.GetTagsHandler() {
         @Override
         public void tagsAvailable(JSONObject tags) {
            lastGetTags = tags;
         }
      });

      Assert.assertEquals("value1", lastGetTags.getString("test1"));
      Assert.assertEquals("value2", lastGetTags.getString("test2"));
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

   // ####### Unit test helper methods ########

   private static void threadWait() {
      try {Thread.sleep(1000);} catch (Throwable t) {}
   }

   private void threadAndTaskWait() {
      try {Thread.sleep(300);} catch (Throwable t) {}
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
}