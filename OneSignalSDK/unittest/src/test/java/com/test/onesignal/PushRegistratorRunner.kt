/**
 * Modified MIT License

 * Copyright 2016 OneSignal

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.

 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.test.onesignal

import android.app.Activity

import com.onesignal.BuildConfig
import com.onesignal.PushRegistrator
import com.onesignal.PushRegistratorGPS
import com.onesignal.ShadowGoogleCloudMessaging
import com.onesignal.ShadowGooglePlayServicesUtil
import com.onesignal.example.BlankActivity

import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@Config(packageName = "com.onesignal.example", constants = BuildConfig::class, instrumentedPackages = arrayOf("com.onesignal"), shadows = arrayOf(ShadowGooglePlayServicesUtil::class, ShadowGoogleCloudMessaging::class), sdk = intArrayOf(21))
@RunWith(RobolectricTestRunner::class)
class PushRegistratorRunner {

    private var blankActivity: Activity? = null

    @Before // Before each test
    @Throws(Exception::class)
    fun beforeEachTest() {
        blankActivity = Robolectric.buildActivity(BlankActivity::class.java).create().get()
        callbackFired = false
        ShadowGoogleCloudMessaging.exists = true
    }

    @Test
    @Throws(Exception::class)
    fun testGooglePlayServicesAPKMissingOnDevice() {
        val pushReg = PushRegistratorGPS()
        val testThread = Thread.currentThread()

        pushReg.registerForPush(blankActivity, "") { id, status ->
            println("HERE: " + id)
            callbackFired = true
            testThread.interrupt()
        }
        try {
            Thread.sleep(5000)
        } catch (t: Throwable) {
        }

        Assert.assertTrue(callbackFired)
    }

    @Test
    @Throws(Exception::class)
    fun testGCMPartOfGooglePlayServicesMissing() {
        val pushReg = PushRegistratorGPS()
        ShadowGoogleCloudMessaging.exists = false

        val testThread = Thread.currentThread()

        pushReg.registerForPush(blankActivity, "") { id, status ->
            println("HERE: " + id)
            callbackFired = true
            testThread.interrupt()
        }
        try {
            Thread.sleep(5000)
        } catch (t: Throwable) {
        }

        Assert.assertTrue(callbackFired)
    }

    companion object {
        private var callbackFired: Boolean = false

        @BeforeClass // Runs only once, before any tests
        @JvmStatic fun setUpClass() {
            ShadowLog.stream = System.out
        }
    }
}