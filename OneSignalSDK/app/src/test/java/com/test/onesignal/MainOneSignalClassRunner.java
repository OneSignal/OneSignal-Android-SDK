/**
 * Modified MIT License
 *
 * Copyright 2015 OneSignal
 *
 * Portions Copyright 2013 Google Inc.
 * This file includes portions from the Google GcmClient demo project
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
import android.content.Intent;

import com.onesignal.BuildConfig;
import com.onesignal.NotificationBundleProcessor;
import com.onesignal.OneSignal;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorGPS;
import com.onesignal.example.BlankActivity;

import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.lang.reflect.Field;


@Config(packageName = "com.onesignal.example",
      constants = BuildConfig.class,
      shadows = {ShadowOneSignalRestClient.class, ShadowPushRegistratorGPS.class},
      sdk = 21)
@RunWith(CustomRobolectricTestRunner.class)
public class MainOneSignalClassRunner {

   private static Field OneSignal_CurrentSubscription;
   private Activity blankActiviy;
   private static String callBackUseId, getCallBackRegId;
   private static String notificationOpenedMessage;

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

      OneSignal_CurrentSubscription = OneSignal.class.getDeclaredField("currentSubscription");
      OneSignal_CurrentSubscription.setAccessible(true);

      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
      StaticResetHelper.saveStaticValues();
   }

   @Before // Before each test
   public void beforeEachTest() throws Exception {
      callBackUseId = getCallBackRegId = null;
      StaticResetHelper.restSetStaticFields();
      blankActiviy = Robolectric.buildActivity(BlankActivity.class).create().get();

      ShadowOneSignalRestClient.failNext = false;
      ShadowOneSignalRestClient.testThread = Thread.currentThread();
      GetIdsAvailable();
      notificationOpenedMessage = null;
   }

   @Test
   public void testOpenFromNotificationWhenAppIsDead() throws Exception {
      Intent intent = new Intent();
      intent.putExtra("onesignal_data", "[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]");

      blankActiviy.setIntent(intent);
      OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba", new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
            notificationOpenedMessage = message;
         }
      });

      Assert.assertEquals("Test Msg", notificationOpenedMessage);
   }

   @Test
   public void testOpenFromNotificationWhenAppIsInBackground() throws Exception {
      OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba", new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
            notificationOpenedMessage = message;
         }
      });
      Assert.assertNull(notificationOpenedMessage);

      OneSignal.handleNotificationOpened(blankActiviy, new JSONArray("[{ \"alert\": \"Test Msg\", \"custom\": { \"i\": \"UUID\" } }]"));
      Assert.assertEquals("Test Msg", notificationOpenedMessage);
   }

   @Test
   public void testNotificationReceivedWhenAppInFocus() throws Exception {
      // Tests seem to be over lapping when running them all. Wait a bit before running this test.
      try {Thread.sleep(1000);} catch (Throwable t) {}

      OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba", new OneSignal.NotificationOpenedHandler() {
         @Override
         public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
            notificationOpenedMessage = message;
         }
      });
      try {Thread.sleep(5000);} catch (Throwable t) {}
      Assert.assertNull(notificationOpenedMessage);

      NotificationBundleProcessor.Process(blankActiviy, GenerateNotificationRunner.getBaseNotifBundle());
      try {Thread.sleep(100);} catch (Throwable t) {}
      Robolectric.getForegroundThreadScheduler().runOneTask();
      Robolectric.getForegroundThreadScheduler().runOneTask();
      Assert.assertEquals("Robo test message", notificationOpenedMessage);
   }

   @Test
   public void testInvalidGoogleProjectNumber() throws Exception {
      // Tests seem to be over lapping when running them all. Wait a bit before running this test.
      try {Thread.sleep(1000);} catch (Throwable t) {}

      OneSignal.init(blankActiviy, "NOT A VALID Google project number", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");

      try {Thread.sleep(5000);} catch (Throwable t) {}
      Robolectric.getForegroundThreadScheduler().runOneTask();
      Assert.assertEquals(-6, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Test that idsAvailable still fires
      Assert.assertEquals(ShadowOneSignalRestClient.testUserId, callBackUseId);
   }

   @Test
   public void testUnsubcribeShouldMakeRegIdNullToIdsAvailable() throws Exception {
      OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      try {Thread.sleep(5000);} catch (Throwable t) {}
      Assert.assertEquals(ShadowPushRegistratorGPS.regId, ShadowOneSignalRestClient.lastPost.getString("identifier"));

      Robolectric.getForegroundThreadScheduler().runOneTask();
      Assert.assertEquals(ShadowPushRegistratorGPS.regId, getCallBackRegId);

      OneSignal.setSubscription(false);
      GetIdsAvailable();
      Assert.assertNull(getCallBackRegId);
   }

   @Test
   public void testSetSubscriptionShouldNotOverrideSubscribeError() throws Exception {
      OneSignal.init(blankActiviy, "NOT A VALID Google project number", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      try {Thread.sleep(5000);} catch (Throwable t) {}

      // Should not try to update server
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.setSubscription(true);
      Assert.assertNull(ShadowOneSignalRestClient.lastPost);

      // Restart app - Should omit notification_types
      StaticResetHelper.restSetStaticFields();
      OneSignal.init(blankActiviy, "NOT A VALID Google project number", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      try {Thread.sleep(5000);} catch (Throwable t) {}
      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void shouldNotResetSubscriptionOnSession() throws Exception {
      OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.setSubscription(false);

      try {Thread.sleep(5000);} catch (Throwable t) {}
      Assert.assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
      StaticResetHelper.restSetStaticFields();

      OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      try {Thread.sleep(5000);} catch (Throwable t) {}
      //System.out.println(ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void shouldSetSubscriptionCorrectlyEvenAfterFirstOneSignalRestInitFail() throws Exception {
      // Failed to register with OneSignal but SetSubscription was called with false
      ShadowOneSignalRestClient.failNext = true;
      OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      OneSignal.setSubscription(false);
      try {Thread.sleep(5000);} catch (Throwable t) {}
      ShadowOneSignalRestClient.failNext = false;


      // Restart app - Should send unsubscribe with create player call.
      StaticResetHelper.restSetStaticFields();
      OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      try {Thread.sleep(5000);} catch (Throwable t) {}
      Assert.assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Restart app again - Value synced last time so don't send again.
      StaticResetHelper.restSetStaticFields();
      OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      try {Thread.sleep(5000);} catch (Throwable t) {}
      Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
   }

   @Test
   public void shouldUpdateNotificationTypesCorrectlyEvenWhenSetSubscriptionIsCalledInAnErrorState() throws Exception {
      // Failed to register with bad google project number then set subscription called at any point.
      OneSignal.init(blankActiviy, "Bad_Google_Project_Number", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      try {Thread.sleep(5000);} catch (Throwable t) {}
      OneSignal.setSubscription(true);

      // Restart app - Should send subscribe with on_session call.
      StaticResetHelper.restSetStaticFields();
      OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      try {Thread.sleep(5000);} catch (Throwable t) {}
      Assert.assertEquals(1, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
   }


   @Test
   public void shouldAllowMultipleSetSubscription() throws Exception {
      OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
      try {Thread.sleep(5000);} catch (Throwable t) {}

      OneSignal.setSubscription(false);
      try {Thread.sleep(5000);} catch (Throwable t) {}

      Assert.assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Should not resend same value
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.setSubscription(false);
      Assert.assertNull(ShadowOneSignalRestClient.lastPost);



      OneSignal.setSubscription(true);
      Assert.assertEquals(1, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Should not resend same value
      ShadowOneSignalRestClient.lastPost = null;
      OneSignal.setSubscription(true);
      Assert.assertNull(ShadowOneSignalRestClient.lastPost);
   }
}