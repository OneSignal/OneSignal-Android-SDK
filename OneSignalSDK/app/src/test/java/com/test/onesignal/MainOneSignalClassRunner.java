package com.test.onesignal;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import com.onesignal.BuildConfig;
import com.onesignal.NotificationBundleProcessor;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalDbContract;
import com.onesignal.OneSignalDbHelper;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorGPS;
import com.onesignal.example.BlankActivity;
import com.onesignal.OneSignalDbContract.NotificationTable;

import junit.framework.Assert;

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
   }

   @Test
   public void testInvalidGoogleProjectNumber() throws Exception {
      OneSignal.init(blankActiviy, "NOT A VALID Google project number", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");

      try {Thread.sleep(5000);} catch (Throwable t) {}
      Assert.assertEquals(-6, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

      // Test that idsAvailable still fires
      Robolectric.getForegroundThreadScheduler().runOneTask();
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