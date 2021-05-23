package com.test.onesignal;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.onesignal.OneSignal;
import com.onesignal.OneSignalPackagePrivateHelper.TestOneSignalPrefs;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLog;

import static com.onesignal.ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static org.junit.Assert.assertEquals;

@Config(packageName = "com.onesignal.example",
        shadows = {
                ShadowOneSignalRestClient.class,
                ShadowOSUtils.class
        },
        sdk = 21
)
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.LEGACY)
public class OneSignalPrefsRunner {

   private static final String ONESIGNAL_APP_ID = "b4f7f966-d8cc-11e4-bed1-df8f05be55ba";
   private static final String KEY = "key";
   private static final String VALUE = "value";
   private static Activity blankActivity;

   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;
      TestHelpers.beforeTestSuite();
      StaticResetHelper.saveStaticValues();
   }

   @Before // Before each test
   public void beforeEachTest() throws Exception {
      TestHelpers.beforeTestInitAndCleanup();

      ActivityController<BlankActivity> blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
      blankActivity = blankActivityController.get();

      OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);

      // We only care about shared preferences, avoid other logic's
      ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);
   }

   @AfterClass
   public static void afterEverything() throws Exception {
      StaticResetHelper.restSetStaticFields();
   }

   @Test
   public void testNullContextDoesNotCrash() {
      TestOneSignalPrefs.saveString(TestOneSignalPrefs.PREFS_ONESIGNAL, KEY, VALUE);
      TestHelpers.flushBufferedSharedPrefs();
   }

   @Test
   public void tesWriteWithNullAppId_andSavesAfterSetting() throws Exception {
      OneSignal.initWithContext(blankActivity);
      TestOneSignalPrefs.saveString(TestOneSignalPrefs.PREFS_ONESIGNAL, KEY, VALUE);
      TestHelpers.flushBufferedSharedPrefs();

      OneSignal.setAppId(ONESIGNAL_APP_ID);
      threadAndTaskWait();
      TestHelpers.flushBufferedSharedPrefs();

      final SharedPreferences prefs = blankActivity.getSharedPreferences(TestOneSignalPrefs.PREFS_ONESIGNAL, Context.MODE_PRIVATE);
      String value = prefs.getString(KEY, "");
      assertEquals(VALUE, value);
   }

   @Test
   public void tesWriteWithNullContext_andSavesAfterSetting() throws Exception {
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      TestOneSignalPrefs.saveString(TestOneSignalPrefs.PREFS_ONESIGNAL, KEY, VALUE);
      TestHelpers.flushBufferedSharedPrefs();

      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();
      TestHelpers.flushBufferedSharedPrefs();

      final SharedPreferences prefs = blankActivity.getSharedPreferences(TestOneSignalPrefs.PREFS_ONESIGNAL, Context.MODE_PRIVATE);
      String value = prefs.getString(KEY, "");
      assertEquals(VALUE, value);
   }

   @Test
   public void tesWriteWithNullContextAndAppId_andSavesAfterSetting() throws Exception {
      OneSignal.setAppId(ONESIGNAL_APP_ID);
      TestOneSignalPrefs.saveString(TestOneSignalPrefs.PREFS_ONESIGNAL, KEY, VALUE);
      TestHelpers.flushBufferedSharedPrefs();

      OneSignal.initWithContext(blankActivity);
      threadAndTaskWait();
      TestHelpers.flushBufferedSharedPrefs();

      final SharedPreferences prefs = blankActivity.getSharedPreferences(TestOneSignalPrefs.PREFS_ONESIGNAL, Context.MODE_PRIVATE);
      String value = prefs.getString(KEY, "");
      assertEquals(VALUE, value);
   }
}
