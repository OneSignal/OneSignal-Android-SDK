package com.test.onesignal

import androidx.test.core.app.ApplicationProvider
import com.onesignal.*
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

import com.onesignal.OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_ANDROID
import com.onesignal.OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_FIREOS
import com.onesignal.OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_HUAWEI

@Config(packageName = "com.onesignal.example", shadows = [ShadowOSUtils::class], sdk = [26])
@RunWith(RobolectricTestRunner::class)
class DeviceTypeTestsRunner {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setupClass() {
            ShadowLog.stream = System.out
            TestHelpers.beforeTestSuite()
            StaticResetHelper.saveStaticValues()
        }
    }

    @Before
    @Throws(Exception::class)
    fun beforeEachTest() {
        TestHelpers.beforeTestInitAndCleanup()
        OneSignal.initWithContext(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun noAvailablePushChannels_defaultsToAndroid() {
        Assert.assertEquals(DEVICE_TYPE_ANDROID.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
    }

    @Test
    fun onlyADM_isFireOS() {
        ShadowOSUtils.supportsADM = true
        Assert.assertEquals(DEVICE_TYPE_FIREOS.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
    }

    @Test
    fun onlyFCM_isAndroid() {
        ShadowOSUtils.hasFCMLibrary = true
        Assert.assertEquals(DEVICE_TYPE_ANDROID.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
    }

    @Test
    fun FCMAndGMSEnabled_isAndroid() {
        ShadowOSUtils.isGMSInstalledAndEnabled = true
        ShadowOSUtils.hasFCMLibrary = true
        Assert.assertEquals(DEVICE_TYPE_ANDROID.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
    }

    @Test
    fun supportedHMS_isHuawei() {
        ShadowOSUtils.isHMSCoreInstalledAndEnabled = true
        ShadowOSUtils.hasAllRecommendedHMSLibraries(true)
        Assert.assertEquals(DEVICE_TYPE_HUAWEI.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
    }

    @Test
    fun supportsFCMAndHMS_PreferAndroid() {
        ShadowOSUtils.isGMSInstalledAndEnabled = true
        ShadowOSUtils.hasFCMLibrary = true
        ShadowOSUtils.isHMSCoreInstalledAndEnabled = true
        ShadowOSUtils.hasAllRecommendedHMSLibraries(true)

        // Prefer Google Services over Huawei if both available
        Assert.assertEquals(DEVICE_TYPE_ANDROID.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
    }

    @Test
    fun hasFCMButNoGMSOnDeviceAndHasHMS_isHuawei() {
        ShadowOSUtils.isGMSInstalledAndEnabled = false
        ShadowOSUtils.hasFCMLibrary = true
        ShadowOSUtils.isHMSCoreInstalledAndEnabled = true
        ShadowOSUtils.hasAllRecommendedHMSLibraries(true)

        // Use HMS since device does not have the "Google Play services" app or it is disabled
        Assert.assertEquals(DEVICE_TYPE_HUAWEI.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
    }

    @Test
    fun noPushSDKsAndOnlyHMSCoreInstalled_isHuawei() {
        ShadowOSUtils.isHMSCoreInstalledAndEnabled = true
        Assert.assertEquals(DEVICE_TYPE_HUAWEI.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
    }

    @Test
    fun noPushSDKsAndOnlyGoogleServicesInstalled_isAndroid() {
        ShadowOSUtils.isGMSInstalledAndEnabled = true
        Assert.assertEquals(DEVICE_TYPE_ANDROID.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
    }

    @Test
    fun supportsFCMAndADM_PreferADM() {
        ShadowOSUtils.isGMSInstalledAndEnabled = true
        ShadowOSUtils.hasFCMLibrary = true
        ShadowOSUtils.supportsADM = true

        // Prefer ADM as if available it will always be native to the device
        Assert.assertEquals(DEVICE_TYPE_FIREOS.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
    }
}