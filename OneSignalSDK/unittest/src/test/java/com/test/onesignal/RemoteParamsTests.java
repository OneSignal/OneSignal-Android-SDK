/**
 * Modified MIT License
 * <p>
 * Copyright 2018 OneSignal
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 * <p>
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

import com.onesignal.OneSignal;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;

import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_areNotificationsEnabledForSubscribedState;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_getDisableGMSMissingPrompt;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_locationShared;
import static com.test.onesignal.TestHelpers.afterTestCleanup;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@Config(packageName = "com.onesignal.example",
        instrumentedPackages = {"com.onesignal"},
        shadows = {
                ShadowOneSignalRestClient.class,
                ShadowOSUtils.class,
                ShadowCustomTabsClient.class,
                ShadowCustomTabsSession.class,
        },
        sdk = 21
)
@RunWith(RobolectricTestRunner.class)
public class RemoteParamsTests {

    private static final String ONESIGNAL_APP_ID = "b2f7f966-d8cc-11e4-bed1-df8f05be55ba";
    private static ActivityController<BlankActivity> blankActivityController;
    private static Activity blankActivity;

    @BeforeClass // Runs only once, before any tests
    public static void setUpClass() throws Exception {
        TestHelpers.beforeTestSuite();
        StaticResetHelper.saveStaticValues();
    }

    @Before
    public void beforeEachTest() throws Exception {
        blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
        blankActivity = blankActivityController.get();

        TestHelpers.beforeTestInitAndCleanup();
    }

    @After
    public void afterEachTest() throws Exception {
        afterTestCleanup();
    }

    @AfterClass
    public static void afterEverything() throws Exception {
        TestHelpers.beforeTestInitAndCleanup();
    }

    @Test
    public void testUserPrivacyConsentRequired() throws Exception {
        JSONObject remoteParams = new JSONObject();
        remoteParams.put("requires_user_privacy_consent", true);
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(remoteParams);

        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal.requiresUserPrivacyConsent());
    }

    @Test
    public void testUserPrivacyConsentNotRequired() throws Exception {
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse();

        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal.requiresUserPrivacyConsent());
    }

    @Test
    public void testLocationSharedEnable() throws Exception {
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse();

        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal_locationShared());
    }

    @Test
    public void testLocationSharedDisable() throws Exception {
        JSONObject remoteParams = new JSONObject();
        remoteParams.put("location_shared", false);
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(remoteParams);

        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal_locationShared());
    }

    @Test
    @Config(shadows = {ShadowNotificationManagerCompat.class})
    public void testUnsubscribeOnNotificationsDisable_Enable() throws Exception {
        ShadowNotificationManagerCompat.enabled = false;
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse();

        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal_areNotificationsEnabledForSubscribedState());
    }

    @Test
    public void testUnsubscribeOnNotificationsDisable_Disable() throws Exception {
        JSONObject remoteParams = new JSONObject();
        remoteParams.put("unsubscribe_on_notifications_disabled", false);
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(remoteParams);

        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal_areNotificationsEnabledForSubscribedState());
    }

    @Test
    public void testGMSMissingPromptDisable() throws Exception {
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse();

        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal_getDisableGMSMissingPrompt());
    }

    @Test
    public void testGMSMissingPromptEnabled() throws Exception {
        JSONObject remoteParams = new JSONObject();
        remoteParams.put("disable_gms_missing_prompt", false);
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(remoteParams);

        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal_getDisableGMSMissingPrompt());
    }

    private void OneSignalInit() {
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.DEBUG, OneSignal.LOG_LEVEL.NONE);
        ShadowOSUtils.subscribableStatus = 1;
        OneSignal.setAppId(ONESIGNAL_APP_ID);
        OneSignal.setAppContext(blankActivity);
        blankActivityController.resume();
    }
}