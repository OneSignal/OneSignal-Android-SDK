/**
 * Modified MIT License
 * <p>
 * Copyright 2020 OneSignal
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

import android.annotation.SuppressLint;
import android.app.Activity;

import androidx.test.core.app.ApplicationProvider;

import com.onesignal.MockOSLog;
import com.onesignal.MockOSSharedPreferences;
import com.onesignal.MockOSTimeImpl;
import com.onesignal.MockOneSignalDBHelper;
import com.onesignal.MockSessionManager;
import com.onesignal.MockUserState;
import com.onesignal.OSNotification;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.OneSignalShadowPackageManager;
import com.onesignal.ShadowAdvertisingIdProviderGPS;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowGMSLocationController;
import com.onesignal.ShadowHmsInstanceId;
import com.onesignal.ShadowJobService;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorADM;
import com.onesignal.ShadowPushRegistratorFCM;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;
import com.onesignal.influence.data.OSTrackerFactory;

import org.json.JSONException;
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
import org.robolectric.shadows.ShadowLog;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_getSessionListener;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setSessionManager;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setTime;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setTrackerFactory;
import static com.onesignal.ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse;
import static com.test.onesignal.TestHelpers.afterTestCleanup;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertEquals;

@Config(packageName = "com.onesignal.example",
        shadows = {
                ShadowOneSignalRestClient.class,
                ShadowPushRegistratorADM.class,
                ShadowPushRegistratorFCM.class,
                ShadowOSUtils.class,
                ShadowAdvertisingIdProviderGPS.class,
                ShadowCustomTabsClient.class,
                ShadowCustomTabsSession.class,
                ShadowNotificationManagerCompat.class,
                ShadowJobService.class,
                ShadowHmsInstanceId.class,
                OneSignalShadowPackageManager.class
        },
        sdk = 21
)
@RunWith(RobolectricTestRunner.class)
public class UserStateUnitTest {

    private static final String ONESIGNAL_APP_ID = "b4f7f966-d8cc-11e4-bed1-df8f05be55ba";
    private static final String ONESIGNAL_NOTIFICATION_ID = "97d8e764-81c2-49b0-a644-713d052ae7d5";

    @SuppressLint("StaticFieldLeak")
    private static Activity blankActivity;
    private static ActivityController<BlankActivity> blankActivityController;
    private MockOSTimeImpl time;
    private OSTrackerFactory trackerFactory;
    private MockSessionManager sessionManager;
    private MockOneSignalDBHelper dbHelper;

    private static JSONObject lastGetTags;
    private static void getGetTagsHandler() {
        OneSignal.getTags(tags -> lastGetTags = tags);
    }

    private static JSONObject cur, changedTo;
    private static void populateJSONObjectsSuccess() throws JSONException {
        cur = new JSONObject("{\"notification_types\":1,\"app_id\":\"b2f7f966-d8cc-11e4-bed1-df8f05be55ba\",\"ad_id\":\"8264d1b5-ce74-467d-9514-e08e5247e90a\",\"device_os\":\"9\",\"timezone\":-18000,\"language\":\"en\",\"sdk\":\"031501\",\"sdk_type\":\"native\",\"android_package\":\"com.onesignal.sdktest\",\"device_model\":\"AOSP on IA Emulator\",\"game_version\":1,\"net_type\":0,\"carrier\":\"Android\",\"rooted\":false,\"identifier\":\"cqZsmfZFT6Wwz0tDzT1fty:APA91bH-aob7somEAc92skiE4tO0jtDkjzx_Bs9pS232CEEk60iAnpQjC4yqMkrqVhV5w3mE0EOTWzJOFzcXOps2zzgTfiF9M3f5rdVR-3LeunjxZ8Ld40gi56ozvxWPqHSC-_xpBBBS\",\"device_type\":1,\"tags\":{\"counter\":\"1\",\"test_value\":\"test_key\"}}");
        changedTo = new JSONObject("{\"notification_types\":0,\"app_id\":\"7cdb5dd1-eb5d-4c58-9cbf-c0448b18816b\",\"ad_id\":\"8264d1b5-ce74-467d-9514-e08e5247e90a\",\"device_os\":\"9\",\"timezone\":-18000,\"language\":\"en\",\"sdk\":\"031501\",\"sdk_type\":\"native\",\"android_package\":\"com.onesignal.sdktest\",\"device_model\":\"AOSP on IA Emulator\",\"game_version\":1,\"net_type\":0,\"carrier\":\"Android\",\"rooted\":false,\"identifier\":\"cqZsmfZFT6Wwz0tDzT1fty:APA91bH-aob7somEAc92skiE4tO0jtDkjzx_Bs9pS232CEEk60iAnpQjC4yqMkrqVhV5w3mE0EOTWzJOFzcXOps2zzgTfiF9M3f5rdVR-3LeunjxZ8Ld40gi56ozvxWPqHSC-_xpBBBS\",\"device_type\":1,\"tags\":{\"counter\":\"1\",\"test_key\":\"test_value\"}}");
    }

    private static void cleanUp() throws Exception {
        lastGetTags = null;

        ShadowGMSLocationController.reset();

        TestHelpers.beforeTestInitAndCleanup();

        // Set remote_params GET response
        setRemoteParamsGetHtmlResponse();
    }

    @BeforeClass // Runs only once, before any tests
    public static void setUpClass() throws Exception {
        ShadowLog.stream = System.out;

        TestHelpers.beforeTestSuite();

        Field OneSignal_CurrentSubscription = OneSignal.class.getDeclaredField("subscribableStatus");
        OneSignal_CurrentSubscription.setAccessible(true);

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        StaticResetHelper.saveStaticValues();
    }

    @Before
    public void beforeEachTest() throws Exception {
        blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
        blankActivity = blankActivityController.get();
        time = new MockOSTimeImpl();
        trackerFactory = new OSTrackerFactory(new MockOSSharedPreferences(), new MockOSLog(), time);
        sessionManager = new MockSessionManager(OneSignal_getSessionListener(), trackerFactory, new MockOSLog());
        dbHelper = new MockOneSignalDBHelper(ApplicationProvider.getApplicationContext());

        TestHelpers.setupTestWorkManager(blankActivity);

        cleanUp();

        OneSignal_setTime(time);
    }

    @After
    public void afterEachTest() throws Exception {
        afterTestCleanup();
    }

    @AfterClass
    public static void afterEverything() throws Exception {
        cleanUp();
    }

    @Test
    public void testGenerateJsonDiffMutate() throws Exception {
        JSONObject res = OneSignalPackagePrivateHelper.JSONUtils.jsonDiff(cur, changedTo, null, null);

        populateJSONObjectsSuccess();
        assertEquals(changedTo.toString(), res.toString());
    }

    @Test
    public void testGenerateJsonDiffDoesNotMutate() throws Exception {
        OneSignalInit();
        threadAndTaskWait();

        assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
        OneSignal.sendTags(cur);

        getGetTagsHandler();
        threadAndTaskWait();

        OSNotification osNotification = createTestOSNotification();

        populateJSONObjectsSuccess();

        JSONObject result = OneSignalPackagePrivateHelper.JSONUtils.jsonDiff(osNotification.toJSONObject(), changedTo, null, null);
        assertEquals(result.toString(), changedTo.toString());
    }

    @Test
    public void testGenerateJsonDiffDoesNotMutateIncludingFields() throws Exception {
        populateJSONObjectsSuccess();

        OneSignalInit();
        getGetTagsHandler();
        threadAndTaskWait();

        getGetTagsHandler();
        threadAndTaskWait();

        String email = "josh@onesignal.com";
        OneSignal.setEmail(email);
        threadAndTaskWait();

        MockUserState userState = new MockUserState("ONESIGNAL_USERSTATE_SYNCVALYES_CURRENT_STATE", true);
        userState.dependValues.put("email_auth_hash", "");
        JSONObject syncValues = userState.syncValues;
        userState.generateJsonDiff(syncValues, new JSONObject().put("email", email), syncValues, null);

        JSONObject resultUserValues = OneSignalPackagePrivateHelper.JSONUtils.jsonDiff(userState.dependValues, userState.syncValues, null, null);
        assertEquals(userState.syncValues.toString(), resultUserValues.toString());

        JSONObject resultMockedData = OneSignalPackagePrivateHelper.JSONUtils.jsonDiff(userState.syncValues, changedTo, null, null);
        assertEquals(changedTo.toString(), resultMockedData.toString());
    }

    private void OneSignalInit() {
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        ShadowOSUtils.subscribableStatus = 1;
        OneSignal_setTime(time);
        OneSignal_setTrackerFactory(trackerFactory);
        OneSignal_setSessionManager(sessionManager);
        OneSignal.setAppId(ONESIGNAL_APP_ID);
        OneSignal.initWithContext(blankActivity);
        blankActivityController.resume();
    }


    private static OSNotification createTestOSNotification() throws Exception {
        OSNotification.ActionButton actionButton = new OSNotification.ActionButton("id", "text", null);
        List<OSNotification.ActionButton> actionButtons = new ArrayList<>();
        actionButtons.add(actionButton);

        List<OSNotification> groupedNotifications = new ArrayList<>();

        OSNotification groupedNotification = new OneSignalPackagePrivateHelper.OSTestNotification.OSTestNotificationBuilder()
                .setCollapseId("collapseId1")
                .build();

        groupedNotifications.add(groupedNotification);

        return new OneSignalPackagePrivateHelper.OSTestNotification.OSTestNotificationBuilder()
                .setBody("msg_body")
                .setAdditionalData(new JSONObject("{\"foo\": \"bar\"}"))
                .setActionButtons(actionButtons)
                .setGroupedNotifications(groupedNotifications)
                .build();
    }

}
