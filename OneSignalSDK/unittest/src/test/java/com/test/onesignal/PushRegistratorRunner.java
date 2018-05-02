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

import android.app.Activity;

import com.onesignal.BuildConfig;
import com.onesignal.PushRegistrator;
import com.onesignal.OneSignalPackagePrivateHelper.PushRegistratorGCM;
import com.onesignal.ShadowFirebaseCloudMessaging;
import com.onesignal.ShadowGooglePlayServicesUtil;
import com.onesignal.example.BlankActivity;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import static junit.framework.Assert.assertTrue;

@Config(packageName = "com.onesignal.example",
      constants = BuildConfig.class,
      instrumentedPackages = {"com.onesignal"},
      shadows = { ShadowGooglePlayServicesUtil.class, ShadowFirebaseCloudMessaging.class },
      sdk = 21)
@RunWith(RobolectricTestRunner.class)
public class PushRegistratorRunner {

   private Activity blankActivity;
   private static boolean callbackFired;

   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() {
      ShadowLog.stream = System.out;
      TestHelpers.beforeTestSuite();
   }

   @Before // Before each test
   public void beforeEachTest() {
      blankActivity = Robolectric.buildActivity(BlankActivity.class).create().get();
      callbackFired = false;
      ShadowFirebaseCloudMessaging.exists = true;
   }

   @Test
   public void testGooglePlayServicesAPKMissingOnDevice() throws Exception {
      PushRegistratorGCM pushReg = new PushRegistratorGCM();
      final Thread testThread = Thread.currentThread();

      pushReg.registerForPush(blankActivity, "", new PushRegistrator.RegisteredHandler() {
         @Override
         public void complete(String id, int status) {
            callbackFired = true;
            testThread.interrupt();
         }
      });
      try {Thread.sleep(5000);} catch (Throwable t) {}

      assertTrue(callbackFired);
   }

   @Test
   public void testGCMPartOfGooglePlayServicesMissing() throws Exception {
      PushRegistratorGCM pushReg = new PushRegistratorGCM();
      ShadowFirebaseCloudMessaging.exists = false;

      final Thread testThread = Thread.currentThread();

      pushReg.registerForPush(blankActivity, "", new PushRegistrator.RegisteredHandler() {
         @Override
         public void complete(String id, int status) {
            callbackFired = true;
            testThread.interrupt();
         }
      });
      try {Thread.sleep(5000);} catch (Throwable t) {}

      assertTrue(callbackFired);
   }
}