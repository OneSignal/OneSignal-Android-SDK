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
import junit.framework.Assert.assertEquals
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

@Config(packageName = "com.onesignal.example", shadows = [ShadowOSUtils::class], sdk = [26])
@RunWith(RobolectricTestRunner::class)
object DeviceTypeTestsRunner : Spek({
    beforeEachTest {
        TestHelpers.beforeTestInitAndCleanup()
        OneSignal.initWithContext(ApplicationProvider.getApplicationContext())
    }
    val deviceType =  OneSignalPackagePrivateHelper.getDeviceType()
    it("defaults to Android DeviceType") {
        Assert.assertEquals(deviceType, DEVICE_TYPE_ANDROID)
    }

    describe("FireOS Device") {
        beforeEachTest {
            ShadowOSUtils.supportsADM = true
        }

        it("gives device_type FireOS") {
            assertEquals(DEVICE_TYPE_FIREOS, OneSignalPackagePrivateHelper.getDeviceType())
        }
    }

    describe("Android Device with Play Services") {
        beforeEachTest {
            ShadowOSUtils.isGMSInstalledAndEnabled = true
        }

        it("gives device_type Android") {
            assertEquals(DEVICE_TYPE_ANDROID, OneSignalPackagePrivateHelper.getDeviceType())
        }

        describe("gives device_type Android with FCM") {
            beforeEachTest {
                ShadowOSUtils.hasFCMLibrary = true
            }

            it("gives device_type Android") {
                assertEquals(DEVICE_TYPE_ANDROID, OneSignalPackagePrivateHelper.getDeviceType())
            }
        }
    }

    describe("Huawei Device") {

    }

//    @Test
//    fun noAvailablePushChannels_defaultsToAndroid() {
//        Assert.assertEquals(DEVICE_TYPE_ANDROID.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
//    }
//
//    @Test
//    fun onlyADM_isFireOS() {
//        ShadowOSUtils.supportsADM = true
//        Assert.assertEquals(DEVICE_TYPE_FIREOS.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
//    }
//
//    @Test
//    fun onlyFCM_isAndroid() {
//        ShadowOSUtils.hasFCMLibrary = true
//        Assert.assertEquals(DEVICE_TYPE_ANDROID.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
//    }
//
//    @Test
//    fun FCMAndGMSEnabled_isAndroid() {
//        ShadowOSUtils.isGMSInstalledAndEnabled = true
//        ShadowOSUtils.hasFCMLibrary = true
//        Assert.assertEquals(DEVICE_TYPE_ANDROID.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
//    }
//
//    @Test
//    fun supportedHMS_isHuawei() {
//        ShadowOSUtils.isHMSCoreInstalledAndEnabled = true
//        ShadowOSUtils.hasAllRecommendedHMSLibraries(true)
//        Assert.assertEquals(DEVICE_TYPE_HUAWEI.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
//    }
//
//    @Test
//    fun supportsFCMAndHMS_PreferAndroid() {
//        ShadowOSUtils.isGMSInstalledAndEnabled = true
//        ShadowOSUtils.hasFCMLibrary = true
//        ShadowOSUtils.isHMSCoreInstalledAndEnabled = true
//        ShadowOSUtils.hasAllRecommendedHMSLibraries(true)
//
//        // Prefer Google Services over Huawei if both available
//        Assert.assertEquals(DEVICE_TYPE_ANDROID.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
//    }
//
//    @Test
//    fun hasFCMButNoGMSOnDeviceAndHasHMS_isHuawei() {
//        ShadowOSUtils.isGMSInstalledAndEnabled = false
//        ShadowOSUtils.hasFCMLibrary = true
//        ShadowOSUtils.isHMSCoreInstalledAndEnabled = true
//        ShadowOSUtils.hasAllRecommendedHMSLibraries(true)
//
//        // Use HMS since device does not have the "Google Play services" app or it is disabled
//        Assert.assertEquals(DEVICE_TYPE_HUAWEI.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
//    }
//
//    @Test
//    fun noPushSDKsAndOnlyHMSCoreInstalled_isHuawei() {
//        ShadowOSUtils.isHMSCoreInstalledAndEnabled = true
//        Assert.assertEquals(DEVICE_TYPE_HUAWEI.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
//    }
//
//    @Test
//    fun noPushSDKsAndOnlyGoogleServicesInstalled_isAndroid() {
//        ShadowOSUtils.isGMSInstalledAndEnabled = true
//        Assert.assertEquals(DEVICE_TYPE_ANDROID.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
//    }
//
//    @Test
//    fun supportsFCMAndADM_PreferADM() {
//        ShadowOSUtils.isGMSInstalledAndEnabled = true
//        ShadowOSUtils.hasFCMLibrary = true
//        ShadowOSUtils.supportsADM = true
//
//        // Prefer ADM as if available it will always be native to the device
//        Assert.assertEquals(DEVICE_TYPE_FIREOS.toLong(), OneSignalPackagePrivateHelper.getDeviceType().toLong())
//    }
})