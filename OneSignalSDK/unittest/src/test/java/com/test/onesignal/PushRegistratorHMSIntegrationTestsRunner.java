package com.test.onesignal;

import android.annotation.SuppressLint;
import android.app.Activity;

import com.huawei.hms.common.ApiException;
import com.huawei.hms.support.api.client.Status;
import com.onesignal.InAppMessagingHelpers;
import com.onesignal.OneSignal;
import static com.onesignal.OneSignalPackagePrivateHelper.UserState.PUSH_STATUS_HMS_TOKEN_TIMEOUT;
import static com.onesignal.OneSignalPackagePrivateHelper.UserState.PUSH_STATUS_HMS_API_EXCEPTION_OTHER;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowHmsInstanceId;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorHMS;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;

import org.json.JSONException;
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
import static com.test.onesignal.RestClientAsserts.assertHuaweiPlayerCreateAtIndex;
import static com.test.onesignal.RestClientAsserts.assertPlayerCreateNotSubscribedAtIndex;
import static com.test.onesignal.RestClientAsserts.assertPlayerCreateSubscribedAtIndex;
import static com.test.onesignal.RestClientAsserts.assertPlayerCreateWithNotificationTypesAtIndex;
import static com.test.onesignal.RestClientAsserts.assertRestCalls;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;

@Config(
    packageName = "com.onesignal.example",
    shadows = {
        ShadowOSUtils.class,
        ShadowOneSignalRestClient.class,
        ShadowCustomTabsClient.class,
        ShadowCustomTabsSession.class,
        ShadowHmsInstanceId.class,
        ShadowPushRegistratorHMS.class
    },
    sdk = 26
)
@RunWith(RobolectricTestRunner.class)
public class PushRegistratorHMSIntegrationTestsRunner {

    @SuppressLint("StaticFieldLeak")
    private static Activity blankActivity;
    private static ActivityController<BlankActivity> blankActivityController;

    @BeforeClass // Runs only once, before any tests
    public static void setUpClass() throws Exception {
        ShadowLog.stream = System.out;
        TestHelpers.beforeTestSuite();
        StaticResetHelper.saveStaticValues();
    }

    @Before
    public void beforeEachTest() throws Exception {
        TestHelpers.beforeTestInitAndCleanup();

        ShadowOSUtils.supportsHMS(true);

        blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
        blankActivity = blankActivityController.get();

        // Set remote_params GET response
        setRemoteParamsGetHtmlResponse();
    }

    private static void assertHuaweiSubscribe() throws JSONException {
        assertHuaweiPlayerCreateAtIndex(1);
        assertPlayerCreateSubscribedAtIndex(1);
        assertRestCalls(2);
    }

    private static void assertHuaweiUnsubscribeWithError(int notification_types) throws JSONException {
        assertHuaweiPlayerCreateAtIndex(1);
        assertPlayerCreateNotSubscribedAtIndex(1);
        assertPlayerCreateWithNotificationTypesAtIndex(notification_types, 1);
        assertRestCalls(2);
    }

    private void OneSignalInit() throws Exception {
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        OneSignal.setAppId(InAppMessagingHelpers.ONESIGNAL_APP_ID);
        OneSignal.setAppContext(blankActivity.getApplicationContext());
        blankActivityController.resume();
        threadAndTaskWait();
    }

    @Test
    public void successfulHMS_shouldRegisterSubscribed() throws Exception {
        OneSignalInit();
        assertHuaweiSubscribe();
    }

    @Test
    public void HMSTimeout_shouldRegisterUnsubscribed() throws Exception {
        ShadowHmsInstanceId.token = null;
        OneSignalInit();

        assertHuaweiUnsubscribeWithError(PUSH_STATUS_HMS_TOKEN_TIMEOUT);
    }

    @Test
    public void HMSUnknownException_shouldRegisterUnsubscribed() throws Exception {
        ShadowHmsInstanceId.throwException = new ApiException(new Status(0));
        OneSignalInit();

        assertHuaweiUnsubscribeWithError(PUSH_STATUS_HMS_API_EXCEPTION_OTHER);
    }

    @Test
    public void EMUIPre10Device_shouldRegister() throws Exception {
        // Direct calls to HmsInstanceId.getToken always return null on EMUI9 and older
        ShadowHmsInstanceId.token = null;
        // However HmsMessageServiceOneSignal.onNewToken should fire in the background giving us the token
        ShadowPushRegistratorHMS.backgroundSuccessful = true;

        OneSignalInit();

        assertHuaweiSubscribe();
    }
}
