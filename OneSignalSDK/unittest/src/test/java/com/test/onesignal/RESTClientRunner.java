package com.test.onesignal;

import com.onesignal.BuildConfig;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.ShadowOneSignalRestClientForTimeouts;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowSystemClock;

@Config(packageName = "com.onesignal.example",
    constants = BuildConfig.class,
    instrumentedPackages = {"com.onesignal"},
    shadows = { ShadowOneSignalRestClientForTimeouts.class },
    sdk = 21)
@RunWith(RobolectricTestRunner.class)
public class RESTClientRunner {
   
   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;
   }
   
   @Before // Before each test
   public void beforeEachTest() throws Exception {
      ShadowOneSignalRestClientForTimeouts.threadInterrupted = false;
   }
   
   @Test
   public void testRESTClientFallbackTimeout() throws Exception {
      OneSignalPackagePrivateHelper.OneSignalRestClientPublic_getSync("URL", null);
      ShadowSystemClock.setCurrentTimeMillis(120000);
      TestHelpers.threadAndTaskWait();
      Assert.assertTrue(ShadowOneSignalRestClientForTimeouts.threadInterrupted);
   }
   
}
