package com.test.onesignal;

import androidx.test.core.app.ApplicationProvider;

import com.onesignal.OneSignal;
import com.onesignal.ShadowOSUtils;
import com.onesignal.StaticResetHelper;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import static com.onesignal.OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_ANDROID;
import static com.onesignal.OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_FIREOS;
import static com.onesignal.OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_HUAWEI;
import static com.onesignal.OneSignalPackagePrivateHelper.getDeviceType;
import static org.junit.Assert.assertEquals;

@Config(
    packageName = "com.onesignal.example",
    shadows = {
        ShadowOSUtils.class
    },
    sdk = 26
)
@RunWith(RobolectricTestRunner.class)
public class DeviceTypeTestsRunner {

    @BeforeClass // Runs only once, before any tests
    public static void setUpClass() throws Exception {
        ShadowLog.stream = System.out;
        TestHelpers.beforeTestSuite();
        StaticResetHelper.saveStaticValues();
    }

    @Before
    public void beforeEachTest() throws Exception {
        TestHelpers.beforeTestInitAndCleanup();
        OneSignal.initWithContext(ApplicationProvider.getApplicationContext());
    }

    @AfterClass
    public static void afterEverything() throws Exception {
        TestHelpers.beforeTestInitAndCleanup();
    }

    @After
    public void afterEachTest() throws Exception {
        TestHelpers.afterTestCleanup();
    }

    @Test
    public void noAvailablePushChannels_defaultsToAndroid() {
        assertEquals(DEVICE_TYPE_ANDROID, getDeviceType());
    }

    @Test
    public void onlyADM_isFireOS() {
        ShadowOSUtils.supportsADM = true;
        assertEquals(DEVICE_TYPE_FIREOS, getDeviceType());
    }

    @Test
    public void onlyFCM_isAndroid() {
        ShadowOSUtils.hasFCMLibrary = true;
        assertEquals(DEVICE_TYPE_ANDROID, getDeviceType());
    }

    @Test
    public void FCMAndGMSEnabled_isAndroid() {
        ShadowOSUtils.isGMSInstalledAndEnabled = true;
        ShadowOSUtils.hasFCMLibrary = true;
        assertEquals(DEVICE_TYPE_ANDROID, getDeviceType());
    }

    @Test
    public void supportedHMS_isHuawei() {
        ShadowOSUtils.isHMSCoreInstalledAndEnabled = true;
        ShadowOSUtils.hasAllRecommendedHMSLibraries(true);

        assertEquals(DEVICE_TYPE_HUAWEI, getDeviceType());
    }

    @Test
    public void supportsFCMAndHMS_PreferAndroid() {
        ShadowOSUtils.isGMSInstalledAndEnabled = true;
        ShadowOSUtils.hasFCMLibrary = true;

        ShadowOSUtils.isHMSCoreInstalledAndEnabled = true;
        ShadowOSUtils.hasAllRecommendedHMSLibraries(true);

        // Prefer Google Services over Huawei if both available
        assertEquals(DEVICE_TYPE_ANDROID, getDeviceType());
    }

    @Test
    public void hasFCMButNoGMSOnDeviceAndHasHMS_isHuawei() {
        ShadowOSUtils.isGMSInstalledAndEnabled = false;
        ShadowOSUtils.hasFCMLibrary = true;

        ShadowOSUtils.isHMSCoreInstalledAndEnabled = true;
        ShadowOSUtils.hasAllRecommendedHMSLibraries(true);

        // Use HMS since device does not have the "Google Play services" app or it is disabled
        assertEquals(DEVICE_TYPE_HUAWEI, getDeviceType());
    }

    @Test
    public void noPushSDKsAndOnlyHMSCoreInstalled_isHuawei() {
        ShadowOSUtils.isHMSCoreInstalledAndEnabled = true;
        assertEquals(DEVICE_TYPE_HUAWEI, getDeviceType());
    }

    @Test
    public void noPushSDKsAndOnlyGoogleServicesInstalled_isAndroid() {
        ShadowOSUtils.isGMSInstalledAndEnabled = true;
        assertEquals(DEVICE_TYPE_ANDROID, getDeviceType());
    }

    @Test
    public void supportsFCMAndADM_PreferADM() {
        ShadowOSUtils.isGMSInstalledAndEnabled = true;
        ShadowOSUtils.hasFCMLibrary = true;

        ShadowOSUtils.supportsADM = true;

        // Prefer ADM as if available it will always be native to the device
        assertEquals(DEVICE_TYPE_FIREOS, getDeviceType());
    }
}
