package com.test.onesignal

import com.onesignal.BuildConfig
import com.onesignal.OneSignalPackagePrivateHelper
import com.onesignal.ShadowOneSignalRestClientForTimeouts

import junit.framework.Assert

import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSystemClock

@Config(packageName = "com.onesignal.example",
        constants = BuildConfig::class,
        instrumentedPackages = arrayOf("com.onesignal"),
        shadows = arrayOf(ShadowOneSignalRestClientForTimeouts::class),
        sdk = intArrayOf(21))
@RunWith(RobolectricTestRunner::class)
class RESTClientRunner {

    @Before // Before each test
    fun beforeEachTest() {
        ShadowOneSignalRestClientForTimeouts.threadInterrupted = false
    }

    companion object {
        @BeforeClass // Runs only once, before any tests
        @JvmStatic fun setUpClass() {
            ShadowLog.stream = System.out
        }
    }

    @Test
    @Throws(Exception::class)
    fun testRESTClientFallbackTimeout() {
        OneSignalPackagePrivateHelper.OneSignalRestClientPublic_getSync("URL", null)
        ShadowSystemClock.setCurrentTimeMillis(120000)
        TestHelpers.threadAndTaskWait()
        Assert.assertTrue(ShadowOneSignalRestClientForTimeouts.threadInterrupted)
    }
}
