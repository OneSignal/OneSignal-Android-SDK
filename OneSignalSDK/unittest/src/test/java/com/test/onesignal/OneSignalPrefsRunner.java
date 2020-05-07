package com.test.onesignal;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.onesignal.OneSignal;
import com.onesignal.OneSignalPackagePrivateHelper.TestOneSignalPrefs;
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
import org.robolectric.shadows.ShadowLog;

import static org.junit.Assert.assertEquals;

@Config(packageName = "com.onesignal.example",
        instrumentedPackages = { "com.onesignal" },
        sdk = 21
)

@RunWith(RobolectricTestRunner.class)
public class OneSignalPrefsRunner {
   private static Activity blankActivity;

   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;
      TestHelpers.beforeTestSuite();
      StaticResetHelper.saveStaticValues();
   }

   @Before // Before each test
   public void beforeEachTest() {
      TestOneSignalPrefs.initializePool();
      ActivityController<BlankActivity> blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
      blankActivity = blankActivityController.get();
   }

   @AfterClass
   public static void afterEverything() throws Exception {
      StaticResetHelper.restSetStaticFields();
   }

   @Test
   public void testNullContextDoesNotCrash() {
      TestOneSignalPrefs.saveString(TestOneSignalPrefs.PREFS_ONESIGNAL,"key", "value");
      TestHelpers.flushBufferedSharedPrefs();
   }

   @Test
   public void tesWriteWithNullContextAndSavesAfterSetting() {
      TestOneSignalPrefs.saveString(TestOneSignalPrefs.PREFS_ONESIGNAL,"key", "value");
      TestHelpers.flushBufferedSharedPrefs();

      OneSignal.setAppContext(blankActivity);
      TestHelpers.flushBufferedSharedPrefs();

      final SharedPreferences prefs = blankActivity.getSharedPreferences(TestOneSignalPrefs.PREFS_ONESIGNAL, Context.MODE_PRIVATE);
      String value = prefs.getString("key", "");
      assertEquals("value", value);
   }
}
