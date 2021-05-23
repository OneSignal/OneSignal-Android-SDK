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
import org.robolectric.annotation.LooperMode;

import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_areNotificationsEnabledForSubscribedState;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_getDisableGMSMissingPrompt;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_locationShared;
import static com.test.onesignal.TestHelpers.afterTestCleanup;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@Config(packageName = "com.onesignal.example",
        shadows = {
                ShadowOneSignalRestClient.class,
                ShadowOSUtils.class,
                ShadowCustomTabsClient.class,
                ShadowCustomTabsSession.class,
        },
        sdk = 21
)
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.LEGACY)
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
    public void testUserPrivacyConsentRequired_ByUser() throws Exception {
        ShadowOneSignalRestClient.setAndRemoveKeyFromRemoteParams("requires_user_privacy_consent");
        OneSignal.setRequiresUserPrivacyConsent(true);
        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal.requiresUserPrivacyConsent());
    }

    @Test
    public void testUserPrivacyConsentNotRequired_ByUser() throws Exception {
        ShadowOneSignalRestClient.setAndRemoveKeyFromRemoteParams("requires_user_privacy_consent");
        OneSignal.setRequiresUserPrivacyConsent(false);
        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal.requiresUserPrivacyConsent());
    }

    @Test
    public void testUserPrivacyConsentRequired_ByRemoteParams() throws Exception {
        ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);

        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal.requiresUserPrivacyConsent());
    }

    @Test
    public void testUserPrivacyConsentNotRequired_ByRemoteParams() throws Exception {
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse();

        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal.requiresUserPrivacyConsent());
    }

    @Test
    public void testUserPrivacyConsentRequired_UserConfigurationOverrideByRemoteParams() throws Exception {
        ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);

        OneSignal.setRequiresUserPrivacyConsent(false);
        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal.requiresUserPrivacyConsent());
    }

    @Test
    public void testUserPrivacyConsentNotRequired_UserConfigurationOverrideByRemoteParams() throws Exception {
        ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(false);

        OneSignal.setRequiresUserPrivacyConsent(true);
        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal.requiresUserPrivacyConsent());
    }

    @Test
    public void testUserPrivacyConsentRequired_UserConfigurationNotOverrideRemoteParams() throws Exception {
        ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(true);

        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal.requiresUserPrivacyConsent());

        OneSignal.setRequiresUserPrivacyConsent(false);
        assertTrue(OneSignal.requiresUserPrivacyConsent());
    }

    @Test
    public void testUserPrivacyConsentNotRequired_UserConfigurationNotOverrideRemoteParams() throws Exception {
        ShadowOneSignalRestClient.setRemoteParamsRequirePrivacyConsent(false);

        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal.requiresUserPrivacyConsent());

        OneSignal.setRequiresUserPrivacyConsent(true);
        assertFalse(OneSignal.requiresUserPrivacyConsent());
    }

    @Test
    public void testLocationSharedEnable_ByUser() throws Exception {
        OneSignal.setLocationShared(true);
        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal_locationShared());
    }

    @Test
    public void testLocationSharedDisable_ByUser() throws Exception {
        ShadowOneSignalRestClient.setAndRemoveKeyFromRemoteParams("location_shared");
        OneSignal.setLocationShared(false);
        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal_locationShared());
    }

    @Test
    public void testLocationSharedEnable_ByRemoteParams() throws Exception {
        ShadowOneSignalRestClient.setAndRemoveKeyFromRemoteParams("location_shared");
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse();

        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal_locationShared());
    }

    @Test
    public void testLocationSharedDisable_ByRemoteParams() throws Exception {
        JSONObject remoteParams = new JSONObject();
        remoteParams.put("location_shared", false);
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(remoteParams);

        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal_locationShared());
    }

    @Test
    public void testLocationSharedEnable_UserConfigurationOverrideByRemoteParams() throws Exception {
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse();

        OneSignal.setLocationShared(false);
        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal_locationShared());
    }

    @Test
    public void testLocationSharedDisable_UserConfigurationOverrideByRemoteParams() throws Exception {
        JSONObject remoteParams = new JSONObject();
        remoteParams.put("location_shared", false);
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(remoteParams);

        OneSignal.setLocationShared(true);
        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal_locationShared());
    }

    @Test
    public void testLocationSharedEnable_UserConfigurationNotOverrideRemoteParams() throws Exception {
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse();

        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal_locationShared());

        OneSignal.setLocationShared(false);
        assertTrue(OneSignal_locationShared());
    }

    @Test
    public void testLocationSharedDisable_UserConfigurationNotOverrideRemoteParams() throws Exception {
        JSONObject remoteParams = new JSONObject();
        remoteParams.put("location_shared", false);
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(remoteParams);

        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal_locationShared());

        OneSignal.setLocationShared(true);
        assertFalse(OneSignal_locationShared());
    }

    @Test
    @Config(shadows = {ShadowNotificationManagerCompat.class})
    public void testUnsubscribeOnNotificationsDisable_EnableByUser() throws Exception {
        ShadowNotificationManagerCompat.enabled = false;
        OneSignal.unsubscribeWhenNotificationsAreDisabled(true);
        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal_areNotificationsEnabledForSubscribedState());
    }

    @Test
    @Config(shadows = {ShadowNotificationManagerCompat.class})
    public void testUnsubscribeOnNotificationsDisable_DisableByUser() throws Exception {
        ShadowNotificationManagerCompat.enabled = false;
        ShadowOneSignalRestClient.setAndRemoveKeyFromRemoteParams("unsubscribe_on_notifications_disabled");
        OneSignal.unsubscribeWhenNotificationsAreDisabled(false);
        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal_areNotificationsEnabledForSubscribedState());
    }

    @Test
    @Config(shadows = {ShadowNotificationManagerCompat.class})
    public void testUnsubscribeOnNotificationsDisable_EnableByRemoteParams() throws Exception {
        ShadowNotificationManagerCompat.enabled = false;
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse();

        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal_areNotificationsEnabledForSubscribedState());
    }

    @Test
    public void testUnsubscribeOnNotificationsDisable_DisableByRemoteParams() throws Exception {
        JSONObject remoteParams = new JSONObject();
        remoteParams.put("unsubscribe_on_notifications_disabled", false);
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(remoteParams);

        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal_areNotificationsEnabledForSubscribedState());
    }

    @Test
    @Config(shadows = {ShadowNotificationManagerCompat.class})
    public void testUnsubscribeOnNotificationsDisable_Enable_UserConfigurationOverrideByRemoteParams() throws Exception {
        ShadowNotificationManagerCompat.enabled = false;
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse();

        OneSignal.unsubscribeWhenNotificationsAreDisabled(false);
        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal_areNotificationsEnabledForSubscribedState());
    }

    @Test
    public void testUnsubscribeOnNotificationsDisable_Disable_UserConfigurationOverrideByRemoteParams() throws Exception {
        JSONObject remoteParams = new JSONObject();
        remoteParams.put("unsubscribe_on_notifications_disabled", false);
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(remoteParams);

        OneSignal.unsubscribeWhenNotificationsAreDisabled(true);
        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal_areNotificationsEnabledForSubscribedState());
    }

    @Test
    @Config(shadows = {ShadowNotificationManagerCompat.class})
    public void testUnsubscribeOnNotificationsDisable_Enable_UserConfigurationNotOverrideRemoteParams() throws Exception {
        ShadowNotificationManagerCompat.enabled = false;
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse();

        OneSignalInit();
        threadAndTaskWait();
        assertFalse(OneSignal_areNotificationsEnabledForSubscribedState());

        OneSignal.unsubscribeWhenNotificationsAreDisabled(false);
        assertFalse(OneSignal_areNotificationsEnabledForSubscribedState());
    }

    @Test
    public void testUnsubscribeOnNotificationsDisable_Disable_UserConfigurationNotOverrideRemoteParams() throws Exception {
        JSONObject remoteParams = new JSONObject();
        remoteParams.put("unsubscribe_on_notifications_disabled", false);
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(remoteParams);

        OneSignalInit();
        threadAndTaskWait();
        assertTrue(OneSignal_areNotificationsEnabledForSubscribedState());

        OneSignal.unsubscribeWhenNotificationsAreDisabled(true);
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
        OneSignal.initWithContext(blankActivity);
        blankActivityController.resume();
    }
}