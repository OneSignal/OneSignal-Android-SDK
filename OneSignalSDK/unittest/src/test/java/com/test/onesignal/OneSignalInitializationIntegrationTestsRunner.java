package com.test.onesignal;

import androidx.test.core.app.ApplicationProvider;

import com.onesignal.OneSignal;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorFCM;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import static com.onesignal.ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;

@Config(
        packageName = "com.onesignal.example",
        shadows = {
            ShadowOSUtils.class,
            ShadowOneSignalRestClient.class,
            ShadowPushRegistratorFCM.class,
            ShadowCustomTabsClient.class,
            ShadowCustomTabsSession.class,
        },
        sdk = 26
)

@RunWith(RobolectricTestRunner.class)
public class OneSignalInitializationIntegrationTestsRunner {
    private ActivityController<BlankActivity> blankActivityController;

    @BeforeClass // Runs only once, before any tests
    public static void setUpClass() throws Exception {
        ShadowLog.stream = System.out;
        TestHelpers.beforeTestSuite();
        StaticResetHelper.saveStaticValues();
    }

    @Before
    public void beforeEachTest() throws Exception {
        TestHelpers.beforeTestInitAndCleanup();
        setRemoteParamsGetHtmlResponse();
        blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
    }

    private static final String APP_ID = "11111111-2222-3333-4444-55555555555";
    private static void helper_OneSignal_initWithAppContext() {
        OneSignal.initWithContext(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void setRequiresUserPrivacyConsent_withTrue_CalledFirst_DoesNOTCreatePlayer() throws Exception {
        OneSignal.setRequiresUserPrivacyConsent(true);

        OneSignal.setAppId(APP_ID);
        helper_OneSignal_initWithAppContext();
        threadAndTaskWait();

        RestClientAsserts.assertRemoteParamsWasTheOnlyNetworkCall();
    }

    @Test
    public void setRequiresUserPrivacyConsent_withFalseAndRemoteTrue_DoesNOTCreatePlayer() throws Exception {
        ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);
        OneSignal.setRequiresUserPrivacyConsent(false);

        OneSignal.setAppId(APP_ID);
        helper_OneSignal_initWithAppContext();
        threadAndTaskWait();

        RestClientAsserts.assertRemoteParamsWasTheOnlyNetworkCall();
    }

}
