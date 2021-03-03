package com.test.onesignal;

import android.annotation.SuppressLint;
import android.app.Activity;

import androidx.test.core.app.ApplicationProvider;

import com.onesignal.MockOSLog;
import com.onesignal.MockOSSharedPreferences;
import com.onesignal.MockOSTimeImpl;
import com.onesignal.MockOneSignalDBHelper;
import com.onesignal.MockSessionManager;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowGMSLocationController;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;
import com.onesignal.influence.data.OSTrackerFactory;

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
import java.util.List;

import static com.onesignal.OneSignal.ExternalIdErrorType.REQUIRES_EXTERNAL_ID_AUTH;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_getSessionListener;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setSessionManager;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setTime;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setTrackerFactory;
import static com.onesignal.ShadowOneSignalRestClient.EMAIL_USER_ID;
import static com.onesignal.ShadowOneSignalRestClient.PUSH_USER_ID;
import static com.onesignal.ShadowOneSignalRestClient.SMS_USER_ID;
import static com.onesignal.ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse;
import static com.test.onesignal.RestClientAsserts.assertOnFocusAtIndex;
import static com.test.onesignal.RestClientAsserts.assertRestCalls;
import static com.test.onesignal.TestHelpers.afterTestCleanup;
import static com.test.onesignal.TestHelpers.assertAndRunSyncService;
import static com.test.onesignal.TestHelpers.fastColdRestartApp;
import static com.test.onesignal.TestHelpers.pauseActivity;
import static com.test.onesignal.TestHelpers.restartAppAndElapseTimeToNextSession;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;

@Config(packageName = "com.onesignal.example",
        shadows = {
                ShadowOneSignalRestClient.class,
                ShadowOSUtils.class,
                ShadowCustomTabsClient.class,
                ShadowCustomTabsSession.class
        },
        sdk = 21
)
@RunWith(RobolectricTestRunner.class)
public class SynchronizerIntegrationTests {
    private static final String ONESIGNAL_APP_ID = "b4f7f966-d8cc-11e4-bed1-df8f05be55ba";
    private static final String ONESIGNAL_EMAIL_ADDRESS = "test@onesignal.com";
    private static final String ONESIGNAL_SMS_NUMBER = "123456789";

    @SuppressLint("StaticFieldLeak")
    private static Activity blankActivity;
    private static ActivityController<BlankActivity> blankActivityController;

    private MockOSTimeImpl time;
    private OSTrackerFactory trackerFactory;
    private MockSessionManager sessionManager;
    private MockOneSignalDBHelper dbHelper;

    private static JSONObject lastExternalUserIdResponse;
    private static OneSignal.ExternalIdError lastExternalUserIdError;

    private static OneSignal.OSExternalUserIdUpdateCompletionHandler getExternalUserIdUpdateCompletionHandler() {
        return new OneSignal.OSExternalUserIdUpdateCompletionHandler() {
            @Override
            public void onSuccess(JSONObject results) {
                lastExternalUserIdResponse = results;
            }

            @Override
            public void onFailure(OneSignal.ExternalIdError error) {
                lastExternalUserIdError = error;
            }
        };
    }

    private static boolean didEmailUpdateSucceed;
    private static OneSignal.EmailUpdateError lastEmailUpdateFailure;

    private static OneSignal.EmailUpdateHandler getEmailUpdateHandler() {
        return new OneSignal.EmailUpdateHandler() {
            @Override
            public void onSuccess() {
                didEmailUpdateSucceed = true;
            }

            @Override
            public void onFailure(OneSignal.EmailUpdateError error) {
                lastEmailUpdateFailure = error;
            }
        };
    }

    private static JSONObject didSMSlUpdateSucceed;
    private static OneSignal.OSSMSUpdateError lastSMSUpdateFailure;

    private static OneSignal.OSSMSUpdateHandler getSMSUpdateHandler() {
        return new OneSignal.OSSMSUpdateHandler() {
            @Override
            public void onSuccess(JSONObject result) {
                didSMSlUpdateSucceed = result;
            }

            @Override
            public void onFailure(OneSignal.OSSMSUpdateError error) {
                lastSMSUpdateFailure = error;
            }
        };
    }

    private static void cleanUp() throws Exception {
        lastExternalUserIdResponse = null;
        lastExternalUserIdError = null;
        lastEmailUpdateFailure = null;
        didEmailUpdateSucceed = false;

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
    public void shouldSetEmail() throws Exception {
        OneSignalInit();
        String email = "josh@onesignal.com";

        OneSignal.setEmail(email);
        threadAndTaskWait();

        assertEquals(4, ShadowOneSignalRestClient.networkCallCount);

        JSONObject pushPost = ShadowOneSignalRestClient.requests.get(1).payload;
        assertEquals(email, pushPost.getString("email"));
        assertEquals(1, pushPost.getInt("device_type"));

        JSONObject emailPost = ShadowOneSignalRestClient.requests.get(2).payload;
        assertEquals(email, emailPost.getString("identifier"));
        assertEquals(11, emailPost.getInt("device_type"));
        assertEquals(ShadowOneSignalRestClient.pushUserId, emailPost.getString("device_player_id"));

        JSONObject pushPut = ShadowOneSignalRestClient.requests.get(3).payload;
        assertEquals(ShadowOneSignalRestClient.emailUserId, pushPut.getString("parent_player_id"));
        assertFalse(pushPut.has("identifier"));
    }

    @Test
    public void shouldSetSMS() throws Exception {
        OneSignalInit();

        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        // NO parent player id call
        assertEquals(3, ShadowOneSignalRestClient.networkCallCount);

        JSONObject pushPost = ShadowOneSignalRestClient.requests.get(1).payload;
        assertEquals(ONESIGNAL_SMS_NUMBER, pushPost.getString("sms_number"));
        assertEquals(1, pushPost.getInt("device_type"));

        JSONObject smsPost = ShadowOneSignalRestClient.requests.get(2).payload;
        assertEquals(ONESIGNAL_SMS_NUMBER, smsPost.getString("identifier"));
        assertEquals(OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_SMS, smsPost.getInt("device_type"));
        assertEquals(ShadowOneSignalRestClient.pushUserId, smsPost.getString("device_player_id"));
    }

    @Test
    public void shouldSetSMSAndEmail() throws Exception {
        OneSignalInit();

        OneSignal.setEmail(ONESIGNAL_EMAIL_ADDRESS);
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        assertEquals(5, ShadowOneSignalRestClient.networkCallCount);

        JSONObject pushPost = ShadowOneSignalRestClient.requests.get(1).payload;
        assertEquals(ONESIGNAL_EMAIL_ADDRESS, pushPost.getString("email"));
        assertEquals(ONESIGNAL_SMS_NUMBER, pushPost.getString("sms_number"));
        assertEquals(1, pushPost.getInt("device_type"));

        JSONObject emailPost = ShadowOneSignalRestClient.requests.get(2).payload;
        assertEquals(ONESIGNAL_EMAIL_ADDRESS, emailPost.getString("identifier"));
        assertEquals(11, emailPost.getInt("device_type"));
        assertEquals(ShadowOneSignalRestClient.pushUserId, emailPost.getString("device_player_id"));

        JSONObject smsPost = ShadowOneSignalRestClient.requests.get(3).payload;
        assertEquals(ONESIGNAL_SMS_NUMBER, smsPost.getString("identifier"));
        assertEquals(OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_SMS, smsPost.getInt("device_type"));
        assertEquals(ShadowOneSignalRestClient.pushUserId, smsPost.getString("device_player_id"));

        JSONObject pushPut = ShadowOneSignalRestClient.requests.get(4).payload;
        assertEquals(ShadowOneSignalRestClient.emailUserId, pushPut.getString("parent_player_id"));
        // add email parent id call
        // TODO: check if this behaviour is not dropped
        assertFalse(pushPut.has("identifier"));
    }

    @Test
    public void shouldSendTagsToEmailBeforeCreate() throws Exception {
        OneSignalInit();
        OneSignal.setEmail("josh@onesignal.com");
        JSONObject tagsJson = new JSONObject("{\"test1\": \"value1\", \"test2\": \"value2\"}");
        OneSignal.sendTags(tagsJson);
        threadAndTaskWait();

        assertEquals(4, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request emailPost = ShadowOneSignalRestClient.requests.get(2);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPost.method);
        assertEquals("josh@onesignal.com", emailPost.payload.get("identifier"));
        assertEquals(tagsJson.toString(), emailPost.payload.getJSONObject("tags").toString());
    }

    @Test
    public void shouldSendTagsToSMSBeforeCreate() throws Exception {
        OneSignalInit();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        JSONObject tagsJson = new JSONObject("{\"test1\": \"value1\", \"test2\": \"value2\"}");
        OneSignal.sendTags(tagsJson);
        threadAndTaskWait();

        assertEquals(3, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request smsPost = ShadowOneSignalRestClient.requests.get(2);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, smsPost.method);
        assertEquals(ONESIGNAL_SMS_NUMBER, smsPost.payload.get("identifier"));
        assertEquals(tagsJson.toString(), smsPost.payload.getJSONObject("tags").toString());
    }

    @Test
    public void shouldWaitBeforeCreateEmailIfPushCreateFails() throws Exception {
        ShadowOneSignalRestClient.failPosts = true;

        OneSignalInit();
        OneSignal.setEmail("josh@onesignal.com");
        threadAndTaskWait();

        // Assert we are sending / retry for the push player first.
        assertEquals(5, ShadowOneSignalRestClient.networkCallCount);
        for (int i = 1; i < ShadowOneSignalRestClient.networkCallCount; i++) {
            ShadowOneSignalRestClient.Request emailPost = ShadowOneSignalRestClient.requests.get(i);
            assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPost.method);
            assertEquals(1, emailPost.payload.getInt("device_type"));
        }

        // Turn off fail mocking, call sendTags to trigger another retry
        ShadowOneSignalRestClient.failPosts = false;
        OneSignal.sendTag("test", "test");
        threadAndTaskWait();

        // Should now POST to create device_type 11 (email)
        ShadowOneSignalRestClient.Request emailPost = ShadowOneSignalRestClient.requests.get(6);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPost.method);
        assertEquals("josh@onesignal.com", emailPost.payload.get("identifier"));
    }

    @Test
    public void shouldWaitBeforeCreateSMSIfPushCreateFails() throws Exception {
        ShadowOneSignalRestClient.failPosts = true;

        OneSignalInit();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        // Assert we are sending / retry for the push player first.
        assertEquals(5, ShadowOneSignalRestClient.networkCallCount);
        for (int i = 1; i < ShadowOneSignalRestClient.networkCallCount; i++) {
            ShadowOneSignalRestClient.Request smsPost = ShadowOneSignalRestClient.requests.get(i);
            assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, smsPost.method);
            assertEquals(ONESIGNAL_SMS_NUMBER, smsPost.payload.getString("sms_number"));
            assertEquals(OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_ANDROID, smsPost.payload.getInt("device_type"));
        }

        // Turn off fail mocking, call sendTags to trigger another retry
        ShadowOneSignalRestClient.failPosts = false;
        OneSignal.sendTag("test", "test");
        threadAndTaskWait();

        // Should now POST to create device_type 14 (sms)
        ShadowOneSignalRestClient.Request smsPost = ShadowOneSignalRestClient.requests.get(6);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, smsPost.method);
        assertEquals(ONESIGNAL_SMS_NUMBER, smsPost.payload.get("identifier"));
        assertEquals(OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_SMS, smsPost.payload.get("device_type"));
    }

    @Test
    public void shouldWaitBeforeCreateEmailAndSMSIfPushCreateFails() throws Exception {
        ShadowOneSignalRestClient.failPosts = true;

        OneSignalInit();
        OneSignal.setEmail(ONESIGNAL_EMAIL_ADDRESS);
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        // Assert we are sending / retry for the push player first.
        assertEquals(5, ShadowOneSignalRestClient.networkCallCount);
        for (int i = 1; i < ShadowOneSignalRestClient.networkCallCount; i++) {
            ShadowOneSignalRestClient.Request smsPost = ShadowOneSignalRestClient.requests.get(i);
            assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, smsPost.method);
            assertEquals(ONESIGNAL_EMAIL_ADDRESS, smsPost.payload.getString("email"));
            assertEquals(ONESIGNAL_SMS_NUMBER, smsPost.payload.getString("sms_number"));
            assertEquals(OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_ANDROID, smsPost.payload.getInt("device_type"));
        }

        // Turn off fail mocking, call sendTags to trigger another retry
        ShadowOneSignalRestClient.failPosts = false;
        OneSignal.sendTag("test", "test");
        threadAndTaskWait();

        // Should now POST to create device_type 11 (email)
        ShadowOneSignalRestClient.Request emailPost = ShadowOneSignalRestClient.requests.get(6);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPost.method);
        assertEquals(ONESIGNAL_EMAIL_ADDRESS, emailPost.payload.get("identifier"));
        assertEquals(OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_EMAIL, emailPost.payload.get("device_type"));

        // Should now POST to create device_type 14 (email)
        ShadowOneSignalRestClient.Request smsPost = ShadowOneSignalRestClient.requests.get(7);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, smsPost.method);
        assertEquals(ONESIGNAL_SMS_NUMBER, smsPost.payload.get("identifier"));
        assertEquals(OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_SMS, smsPost.payload.get("device_type"));
    }

    @Test
    public void shouldSendTagsToEmailAfterCreate() throws Exception {
        OneSignalInit();
        OneSignal.setEmail("josh@onesignal.com");
        threadAndTaskWait();

        JSONObject tagsJson = new JSONObject("{\"test1\": \"value1\", \"test2\": \"value2\"}");
        OneSignal.sendTags(tagsJson);
        threadAndTaskWait();

        assertEquals(6, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request emailPut = ShadowOneSignalRestClient.requests.get(5);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, emailPut.method);
        assertEquals("players/b007f967-98cc-11e4-bed1-118f05be4522", emailPut.url);
        assertEquals(tagsJson.toString(), emailPut.payload.getJSONObject("tags").toString());
    }

    @Test
    public void shouldSendTagsToSMSAfterCreate() throws Exception {
        OneSignalInit();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        JSONObject tagsJson = new JSONObject("{\"test1\": \"value1\", \"test2\": \"value2\"}");
        OneSignal.sendTags(tagsJson);
        threadAndTaskWait();

        assertEquals(5, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request smsPut = ShadowOneSignalRestClient.requests.get(4);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, smsPut.method);
        assertEquals("players/" + ShadowOneSignalRestClient.smsUserId, smsPut.url);
        assertEquals(tagsJson.toString(), smsPut.payload.getJSONObject("tags").toString());
    }

    @Test
    public void shouldSendTagsToEmailAndSMSAfterCreate() throws Exception {
        OneSignalInit();
        OneSignal.setEmail(ONESIGNAL_EMAIL_ADDRESS);
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        JSONObject tagsJson = new JSONObject("{\"test1\": \"value1\", \"test2\": \"value2\"}");
        OneSignal.sendTags(tagsJson);
        threadAndTaskWait();

        assertEquals(8, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request emailPut = ShadowOneSignalRestClient.requests.get(6);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, emailPut.method);
        assertEquals("players/" + ShadowOneSignalRestClient.emailUserId, emailPut.url);
        assertEquals(tagsJson.toString(), emailPut.payload.getJSONObject("tags").toString());

        ShadowOneSignalRestClient.Request smsPut = ShadowOneSignalRestClient.requests.get(7);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, smsPut.method);
        assertEquals("players/" + ShadowOneSignalRestClient.smsUserId, smsPut.url);
        assertEquals(tagsJson.toString(), smsPut.payload.getJSONObject("tags").toString());
    }

    @Test
    public void shouldSetEmailWithAuthHash() throws Exception {
        OneSignalInit();
        String email = "josh@onesignal.com";
        String mockEmailHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setEmail(email, mockEmailHash);
        threadAndTaskWait();

        JSONObject emailPost = ShadowOneSignalRestClient.requests.get(2).payload;
        assertEquals(email, emailPost.getString("identifier"));
        assertEquals(11, emailPost.getInt("device_type"));
        assertEquals(mockEmailHash, emailPost.getString("email_auth_hash"));
    }

    @Test
    public void shouldSetSMSWithAuthHash() throws Exception {
        OneSignalInit();
        String mockSMSHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER, mockSMSHash);
        threadAndTaskWait();

        JSONObject smsPost = ShadowOneSignalRestClient.requests.get(2).payload;
        assertEquals(ONESIGNAL_SMS_NUMBER, smsPost.getString("identifier"));
        assertEquals(OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_SMS, smsPost.getInt("device_type"));
        assertEquals(mockSMSHash, smsPost.getString("sms_auth_hash"));
    }

    @Test
    public void shouldSetEmailAndSMSWithAuthHash() throws Exception {
        OneSignalInit();
        String mockEmailHash = new String(new char[64]).replace('\0', '1');
        String mockSMSHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setEmail(ONESIGNAL_EMAIL_ADDRESS, mockEmailHash);
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER, mockSMSHash);
        threadAndTaskWait();

        JSONObject emailPost = ShadowOneSignalRestClient.requests.get(2).payload;
        assertEquals(ONESIGNAL_EMAIL_ADDRESS, emailPost.getString("identifier"));
        assertEquals(OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_EMAIL, emailPost.getInt("device_type"));
        assertEquals(mockEmailHash, emailPost.getString("email_auth_hash"));

        JSONObject smsPost = ShadowOneSignalRestClient.requests.get(3).payload;
        assertEquals(ONESIGNAL_SMS_NUMBER, smsPost.getString("identifier"));
        assertEquals(OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_SMS, smsPost.getInt("device_type"));
        assertEquals(mockSMSHash, smsPost.getString("sms_auth_hash"));
    }

    private class TestEmailUpdateHandler implements OneSignal.EmailUpdateHandler {
        boolean emailFiredSuccess = false;
        OneSignal.EmailUpdateError emailFiredFailure = null;

        @Override
        public void onSuccess() {
            emailFiredSuccess = true;
        }

        @Override
        public void onFailure(OneSignal.EmailUpdateError error) {
            emailFiredFailure = error;
        }
    }

    private class TestSMSUpdateHandler implements OneSignal.OSSMSUpdateHandler {
        JSONObject smsResult = null;
        OneSignal.OSSMSUpdateError smsFiredFailure = null;

        @Override
        public void onSuccess(JSONObject result) {
            smsResult = result;
        }

        @Override
        public void onFailure(OneSignal.OSSMSUpdateError error) {
            smsFiredFailure = error;
        }
    }

    @Test
    public void shouldFireOnSuccessOfEmailUpdate() throws Exception {
        OneSignalInit();
        TestEmailUpdateHandler testEmailUpdateHandler = new TestEmailUpdateHandler();
        OneSignal.setEmail("josh@onesignal.com", testEmailUpdateHandler);
        assertFalse(testEmailUpdateHandler.emailFiredSuccess);
        threadAndTaskWait();

        assertTrue(testEmailUpdateHandler.emailFiredSuccess);
        assertNull(testEmailUpdateHandler.emailFiredFailure);
    }

    @Test
    public void shouldFireOnSuccessOfSMSUpdate() throws Exception {
        OneSignalInit();
        TestSMSUpdateHandler testSMSUpdateHandler = new TestSMSUpdateHandler();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER, testSMSUpdateHandler);
        assertNull(testSMSUpdateHandler.smsResult);
        threadAndTaskWait();

        assertNotNull(testSMSUpdateHandler.smsResult);
        assertEquals(1, testSMSUpdateHandler.smsResult.length());
        assertEquals(ONESIGNAL_SMS_NUMBER, testSMSUpdateHandler.smsResult.get("sms_number"));
        assertNull(testSMSUpdateHandler.smsFiredFailure);
    }

    @Test
    public void shouldFireOnSuccessOfEmailEvenWhenNoChanges() throws Exception {
        OneSignalInit();
        String email = "josh@onesignal.com";
        OneSignal.setEmail(email);
        threadAndTaskWait();

        TestEmailUpdateHandler testEmailUpdateHandler = new TestEmailUpdateHandler();
        OneSignal.setEmail(email, testEmailUpdateHandler);
        threadAndTaskWait();

        assertTrue(testEmailUpdateHandler.emailFiredSuccess);
        assertNull(testEmailUpdateHandler.emailFiredFailure);
    }

    @Test
    public void shouldFireOnSuccessOfSMSEvenWhenNoChanges() throws Exception {
        OneSignalInit();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        TestSMSUpdateHandler testSMSUpdateHandler = new TestSMSUpdateHandler();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER, testSMSUpdateHandler);
        threadAndTaskWait();

        assertNotNull(testSMSUpdateHandler.smsResult);
        assertEquals(1, testSMSUpdateHandler.smsResult.length());
        assertEquals(ONESIGNAL_SMS_NUMBER, testSMSUpdateHandler.smsResult.get("sms_number"));
        assertNull(testSMSUpdateHandler.smsFiredFailure);
    }

    @Test
    public void shouldFireOnFailureOfEmailUpdateOnNetworkFailure() throws Exception {
        OneSignalInit();
        TestEmailUpdateHandler testEmailUpdateHandler = new TestEmailUpdateHandler();
        OneSignal.setEmail("josh@onesignal.com", testEmailUpdateHandler);
        ShadowOneSignalRestClient.failAll = true;
        threadAndTaskWait();

        assertFalse(testEmailUpdateHandler.emailFiredSuccess);
        assertEquals(OneSignal.EmailErrorType.NETWORK, testEmailUpdateHandler.emailFiredFailure.getType());
    }

    @Test
    public void shouldFireOnFailureOfSMSlUpdateOnNetworkFailure() throws Exception {
        OneSignalInit();
        TestSMSUpdateHandler testSMSUpdateHandler = new TestSMSUpdateHandler();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER, testSMSUpdateHandler);
        ShadowOneSignalRestClient.failAll = true;
        threadAndTaskWait();

        assertNull(testSMSUpdateHandler.smsResult);
        assertNotNull(testSMSUpdateHandler.smsFiredFailure);
        assertEquals(OneSignal.SMSErrorType.NETWORK, testSMSUpdateHandler.smsFiredFailure.getType());
    }

    @Test
    public void shouldFireOnFailureOfEmailAndSMSlUpdateOnNetworkFailure() throws Exception {
        OneSignalInit();
        TestEmailUpdateHandler testEmailUpdateHandler = new TestEmailUpdateHandler();
        OneSignal.setEmail(ONESIGNAL_EMAIL_ADDRESS, testEmailUpdateHandler);
        TestSMSUpdateHandler testSMSUpdateHandler = new TestSMSUpdateHandler();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER, testSMSUpdateHandler);
        ShadowOneSignalRestClient.failAll = true;
        threadAndTaskWait();

        assertFalse(testEmailUpdateHandler.emailFiredSuccess);
        assertEquals(OneSignal.EmailErrorType.NETWORK, testEmailUpdateHandler.emailFiredFailure.getType());

        assertNull(testSMSUpdateHandler.smsResult);
        assertNotNull(testSMSUpdateHandler.smsFiredFailure);
        assertEquals(OneSignal.SMSErrorType.NETWORK, testSMSUpdateHandler.smsFiredFailure.getType());
    }

    @Test
    public void shouldFireOnSuccessOnlyAfterNetworkCallAfterEmailLogout() throws Exception {
        OneSignalInit();
        emailSetThenLogout();
        TestEmailUpdateHandler testEmailUpdateHandler = new TestEmailUpdateHandler();
        OneSignal.setEmail("josh@onesignal.com", testEmailUpdateHandler);
        assertFalse(testEmailUpdateHandler.emailFiredSuccess);
        threadAndTaskWait();

        assertTrue(testEmailUpdateHandler.emailFiredSuccess);
        assertNull(testEmailUpdateHandler.emailFiredFailure);
    }

    @Test
    public void shouldFireOnSuccessOnlyAfterNetworkCallAfterSMSLogout() throws Exception {
        OneSignalInit();
        smsSetThenLogout();
        TestSMSUpdateHandler testSMSUpdateHandler = new TestSMSUpdateHandler();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER, testSMSUpdateHandler);
        assertNull(testSMSUpdateHandler.smsResult);
        threadAndTaskWait();

        assertNotNull(testSMSUpdateHandler.smsResult);
        assertEquals(ONESIGNAL_SMS_NUMBER, testSMSUpdateHandler.smsResult.getString("sms_number"));
        assertNull(testSMSUpdateHandler.smsFiredFailure);
    }

    // Should create a new email instead of updating existing player record when no auth hash
    @Test
    public void shouldDoPostOnEmailChange() throws Exception {
        OneSignalInit();

        OneSignal.setEmail("josh@onesignal.com");
        threadAndTaskWait();

        String newMockEmailPlayerId = "c007f967-98cc-11e4-bed1-118f05be4533";
        ShadowOneSignalRestClient.emailUserId = newMockEmailPlayerId;
        String newEmail = "different@email.com";
        OneSignal.setEmail(newEmail);
        threadAndTaskWait();

        ShadowOneSignalRestClient.Request emailPost = ShadowOneSignalRestClient.requests.get(5);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPost.method);
        assertEquals(newEmail, emailPost.payload.get("identifier"));

        ShadowOneSignalRestClient.Request playerPut = ShadowOneSignalRestClient.requests.get(6);
        assertEquals(newMockEmailPlayerId, playerPut.payload.get("parent_player_id"));
    }

    // Should create a new sms instead of updating existing player record when no auth hash
    @Test
    public void shouldDoPostOnSMSChange() throws Exception {
        OneSignalInit();

        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        String newMockSMSPlayerId = "z007f967-98cc-11e4-bed1-118f05be4533";
        ShadowOneSignalRestClient.smsUserId = newMockSMSPlayerId;
        String newSMS = "different_sms_number";
        OneSignal.setSMSNumber(newSMS);
        threadAndTaskWait();

        ShadowOneSignalRestClient.Request smsPost = ShadowOneSignalRestClient.requests.get(4);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, smsPost.method);
        assertEquals(newSMS, smsPost.payload.get("identifier"));
    }

    // Should update player with new email instead of creating a new one when auth hash is provided
    @Test
    public void shouldUpdateEmailWhenAuthHashIsUsed() throws Exception {
        OneSignalInit();
        String email = "josh@onesignal.com";
        String mockEmailHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setEmail(email, mockEmailHash);
        threadAndTaskWait();
        OneSignal.setEmail("different@email.com", mockEmailHash);
        threadAndTaskWait();

        ShadowOneSignalRestClient.Request pushPut = ShadowOneSignalRestClient.requests.get(4);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, pushPut.method);
        assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511", pushPut.url);
        assertEquals("different@email.com", pushPut.payload.get("email"));

        ShadowOneSignalRestClient.Request emailPut = ShadowOneSignalRestClient.requests.get(5);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, emailPut.method);
        assertEquals("players/b007f967-98cc-11e4-bed1-118f05be4522", emailPut.url);
        assertEquals("different@email.com", emailPut.payload.get("identifier"));
    }

    // Should update player with new sms instead of creating a new one when auth hash is provided
    @Test
    public void shouldUpdateSMSlWhenAuthHashIsUsed() throws Exception {
        OneSignalInit();
        String mockEmailHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER, mockEmailHash);
        threadAndTaskWait();
        String newSMS = "different_sms_number";
        OneSignal.setSMSNumber(newSMS, mockEmailHash);
        threadAndTaskWait();

        ShadowOneSignalRestClient.Request pushPut = ShadowOneSignalRestClient.requests.get(3);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, pushPut.method);
        assertEquals("players/" + PUSH_USER_ID, pushPut.url);
        assertEquals(newSMS, pushPut.payload.get("sms_number"));

        ShadowOneSignalRestClient.Request smsPut = ShadowOneSignalRestClient.requests.get(4);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, smsPut.method);
        assertEquals("players/" + SMS_USER_ID, smsPut.url);
        assertEquals(newSMS, smsPut.payload.get("identifier"));
    }

    @Test
    public void shouldSendEmailAuthHashWithLogout() throws Exception {
        OneSignalInit();
        threadAndTaskWait();
        String mockEmailHash = new String(new char[64]).replace('\0', '0');
        OneSignal.setEmail("josh@onesignal.com", mockEmailHash);
        threadAndTaskWait();

        OneSignal.logoutEmail();
        threadAndTaskWait();

        ShadowOneSignalRestClient.Request emailPut = ShadowOneSignalRestClient.requests.get(5);
        assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511/email_logout", emailPut.url);
        assertEquals(mockEmailHash, emailPut.payload.get("email_auth_hash"));
    }

    @Test
    public void shouldSendSMSAuthHashWithLogout() throws Exception {
        OneSignalInit();
        threadAndTaskWait();
        String mockSMSHash = new String(new char[64]).replace('\0', '0');
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER, mockSMSHash);
        threadAndTaskWait();

        OneSignal.logoutSMSNumber();
        threadAndTaskWait();

        ShadowOneSignalRestClient.Request smsPut = ShadowOneSignalRestClient.requests.get(4);
        assertEquals("players/" + PUSH_USER_ID + "/sms_logout", smsPut.url);
        assertEquals(mockSMSHash, smsPut.payload.get("sms_auth_hash"));
    }

    private void emailSetThenLogout() throws Exception {
        OneSignal.setEmail("josh@onesignal.com");
        threadAndTaskWait();

        OneSignal.logoutEmail();
        threadAndTaskWait();
    }

    private void smsSetThenLogout() throws Exception {
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        OneSignal.logoutSMSNumber();
        threadAndTaskWait();
    }

    @Test
    public void shouldLogoutOfEmail() throws Exception {
        OneSignalInit();

        emailSetThenLogout();

        ShadowOneSignalRestClient.Request logoutEmailPost = ShadowOneSignalRestClient.requests.get(4);
        assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511/email_logout", logoutEmailPost.url);
        assertEquals("b007f967-98cc-11e4-bed1-118f05be4522", logoutEmailPost.payload.get("parent_player_id"));
        assertEquals("b4f7f966-d8cc-11e4-bed1-df8f05be55ba", logoutEmailPost.payload.get("app_id"));
    }

    @Test
    public void shouldLogoutOfSMS() throws Exception {
        OneSignalInit();

        smsSetThenLogout();

        ShadowOneSignalRestClient.Request logoutSMSPost = ShadowOneSignalRestClient.requests.get(3);
        assertEquals("players/" + PUSH_USER_ID + "/sms_logout", logoutSMSPost.url);
        assertFalse(logoutSMSPost.payload.has("parent_player_id"));
        assertEquals(ONESIGNAL_APP_ID, logoutSMSPost.payload.get("app_id"));
    }

    @Test
    public void shouldLogoutOfSMSWithExternalId() throws Exception {
        OneSignalInit();

        String externalUserId = "test_external_user_id";
        OneSignal.setExternalUserId(externalUserId);
        threadAndTaskWait();

        smsSetThenLogout();

        ShadowOneSignalRestClient.Request logoutSMSPost = ShadowOneSignalRestClient.requests.get(4);
        assertEquals("players/" + PUSH_USER_ID + "/sms_logout", logoutSMSPost.url);
        assertFalse(logoutSMSPost.payload.has("parent_player_id"));
        assertEquals(externalUserId, logoutSMSPost.payload.get("external_user_id"));
        assertEquals(ONESIGNAL_APP_ID, logoutSMSPost.payload.get("app_id"));
    }

    @Test
    public void logoutEmailShouldNotSendEmailPlayersRequest() throws Exception {
        // 1. Init OneSignal and set email
        OneSignalInit();

        emailSetThenLogout();

        time.advanceSystemAndElapsedTimeBy(60);
        pauseActivity(blankActivityController);
        assertAndRunSyncService();

        assertEquals(6, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request postPush = ShadowOneSignalRestClient.requests.get(4);
        assertNotEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511", postPush.url);

        ShadowOneSignalRestClient.Request postEmail = ShadowOneSignalRestClient.requests.get(5);
        assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511/on_focus", postEmail.url);
    }

    @Test
    public void logoutSMSShouldNotSendSMSPlayersRequest() throws Exception {
        // 1. Init OneSignal and set email
        OneSignalInit();

        smsSetThenLogout();

        time.advanceSystemAndElapsedTimeBy(60);
        pauseActivity(blankActivityController);
        assertAndRunSyncService();

        assertEquals(5, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request postPush = ShadowOneSignalRestClient.requests.get(3);
        assertNotEquals("players/" + PUSH_USER_ID, postPush.url);

        ShadowOneSignalRestClient.Request postEmail = ShadowOneSignalRestClient.requests.get(4);
        assertEquals("players/" + PUSH_USER_ID + "/on_focus", postEmail.url);
    }

    @Test
    public void shouldFireOnSuccessOfLogoutEmail() throws Exception {
        OneSignalInit();
        TestEmailUpdateHandler testEmailUpdateHandler = new TestEmailUpdateHandler();

        OneSignalInit();
        OneSignal.setEmail("josh@onesignal.com");
        threadAndTaskWait();
        OneSignal.logoutEmail(testEmailUpdateHandler);
        threadAndTaskWait();

        assertTrue(testEmailUpdateHandler.emailFiredSuccess);
        assertNull(testEmailUpdateHandler.emailFiredFailure);
    }

    @Test
    public void shouldFireOnSuccessOfLogoutSMS() throws Exception {
        OneSignalInit();
        TestSMSUpdateHandler testSMSUpdateHandler = new TestSMSUpdateHandler();

        OneSignalInit();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();
        OneSignal.logoutSMSNumber(testSMSUpdateHandler);
        threadAndTaskWait();

        assertNotNull(testSMSUpdateHandler.smsResult);
        assertEquals(ONESIGNAL_SMS_NUMBER, testSMSUpdateHandler.smsResult.getString("sms_number"));
        assertNull(testSMSUpdateHandler.smsFiredFailure);
    }

    @Test
    public void shouldFireOnFailureOfLogoutEmailOnNetworkFailure() throws Exception {
        OneSignalInit();
        TestEmailUpdateHandler testEmailUpdateHandler = new TestEmailUpdateHandler();

        OneSignalInit();
        OneSignal.setEmail("josh@onesignal.com");
        threadAndTaskWait();

        ShadowOneSignalRestClient.failAll = true;
        OneSignal.logoutEmail(testEmailUpdateHandler);
        threadAndTaskWait();

        assertFalse(testEmailUpdateHandler.emailFiredSuccess);
        assertEquals(OneSignal.EmailErrorType.NETWORK, testEmailUpdateHandler.emailFiredFailure.getType());
    }

    @Test
    public void shouldFireOnFailureOfLogoutSMSOnNetworkFailure() throws Exception {
        OneSignalInit();
        TestSMSUpdateHandler testSMSUpdateHandler = new TestSMSUpdateHandler();

        OneSignalInit();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        ShadowOneSignalRestClient.failAll = true;
        OneSignal.logoutSMSNumber(testSMSUpdateHandler);
        threadAndTaskWait();

        assertNull(testSMSUpdateHandler.smsResult);
        assertEquals(OneSignal.SMSErrorType.NETWORK, testSMSUpdateHandler.smsFiredFailure.getType());
    }

    @Test
    public void shouldCreateNewEmailAfterLogout() throws Exception {
        OneSignalInit();

        emailSetThenLogout();

        String newMockEmailPlayerId = "c007f967-98cc-11e4-bed1-118f05be4533";
        ShadowOneSignalRestClient.emailUserId = newMockEmailPlayerId;
        OneSignal.setEmail("different@email.com");
        threadAndTaskWait();

        // Update Push record's email field.
        ShadowOneSignalRestClient.Request putPushEmail = ShadowOneSignalRestClient.requests.get(5);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, putPushEmail.method);
        assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511", putPushEmail.url);
        assertEquals("different@email.com", putPushEmail.payload.get("email"));

        // Create new Email record
        ShadowOneSignalRestClient.Request emailPost = ShadowOneSignalRestClient.requests.get(6);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPost.method);
        assertEquals("different@email.com", emailPost.payload.get("identifier"));

        // Update Push record's parent_player_id
        ShadowOneSignalRestClient.Request playerPut2 = ShadowOneSignalRestClient.requests.get(7);
        assertEquals(newMockEmailPlayerId, playerPut2.payload.get("parent_player_id"));
    }

    @Test
    public void shouldCreateNewSMSAfterLogout() throws Exception {
        OneSignalInit();

        smsSetThenLogout();

        String newMockSMSPlayerId = "c007f967-98cc-11e4-bed1-118f05be4533";
        String newSMSNumber = "new_sms_number";

        ShadowOneSignalRestClient.smsUserId = newMockSMSPlayerId;
        OneSignal.setSMSNumber(newSMSNumber);
        threadAndTaskWait();

        // There should not be a parent_player_id update, last SMS POST should be the last one
        assertEquals(6, ShadowOneSignalRestClient.requests.size());

        // Update Push record's sms field.
        ShadowOneSignalRestClient.Request putPushSMS = ShadowOneSignalRestClient.requests.get(4);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, putPushSMS.method);
        assertEquals("players/" + PUSH_USER_ID, putPushSMS.url);
        assertEquals(newSMSNumber, putPushSMS.payload.get("sms_number"));

        // Create new SMS record
        ShadowOneSignalRestClient.Request smsPost = ShadowOneSignalRestClient.requests.get(5);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, smsPost.method);
        assertEquals(newSMSNumber, smsPost.payload.get("identifier"));
    }

    // ####### external_id Tests ########

    @Test
    public void shouldSendExternalUserIdAfterRegistration() throws Exception {
        OneSignalInit();
        threadAndTaskWait();

        String testExternalId = "test_ext_id";

        OneSignal.setExternalUserId(testExternalId);

        threadAndTaskWait();

        assertEquals(3, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request externalIdRequest = ShadowOneSignalRestClient.requests.get(2);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, externalIdRequest.method);
        assertEquals(testExternalId, externalIdRequest.payload.getString("external_user_id"));
    }

    @Test
    public void shouldSendExternalUserIdBeforeRegistration() throws Exception {
        String testExternalId = "test_ext_id";

        OneSignal.setExternalUserId(testExternalId);

        OneSignalInit();
        threadAndTaskWait();

        assertEquals(2, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request registrationRequest = ShadowOneSignalRestClient.requests.get(1);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, registrationRequest.method);
        assertEquals(testExternalId, registrationRequest.payload.getString("external_user_id"));
    }

    @Test
    public void shouldSetExternalIdWithAuthHash() throws Exception {
        ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse(new JSONObject().put("require_user_id_auth", true));

        OneSignalInit();
        threadAndTaskWait();

        String testExternalId = "test_ext_id";

        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        assertNotNull(lastExternalUserIdError);
        assertEquals(REQUIRES_EXTERNAL_ID_AUTH, lastExternalUserIdError.getType());
    }

    @Test
    public void shouldSetExternalIdWithAuthHashAfterRegistration() throws Exception {
        OneSignalInit();
        threadAndTaskWait();

        String testExternalId = "test_ext_id";
        String mockExternalIdHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setExternalUserId(testExternalId, mockExternalIdHash, null);
        threadAndTaskWait();

        assertEquals(3, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request externalIdRequest = ShadowOneSignalRestClient.requests.get(2);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, externalIdRequest.method);
        assertEquals(testExternalId, externalIdRequest.payload.getString("external_user_id"));
        assertEquals(mockExternalIdHash, externalIdRequest.payload.getString("external_user_id_auth_hash"));
    }

    @Test
    public void shouldSetExternalIdWithAuthHashBeforeRegistration() throws Exception {
        String testExternalId = "test_ext_id";
        String mockExternalIdHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setExternalUserId(testExternalId, mockExternalIdHash, null);

        OneSignalInit();
        threadAndTaskWait();

        assertEquals(2, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request registrationRequest = ShadowOneSignalRestClient.requests.get(1);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, registrationRequest.method);
        assertEquals(testExternalId, registrationRequest.payload.getString("external_user_id"));
        assertEquals(mockExternalIdHash, registrationRequest.payload.getString("external_user_id_auth_hash"));
    }

    @Test
    public void shouldAlwaysSetExternalIdWithAuthHashAfterRegistration() throws Exception {
        OneSignalInit();
        threadAndTaskWait();

        String testExternalId = "test_ext_id";
        String mockExternalIdHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setExternalUserId(testExternalId, mockExternalIdHash);
        threadAndTaskWait();

        ShadowOneSignalRestClient.Request registrationRequest = ShadowOneSignalRestClient.requests.get(2);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, registrationRequest.method);
        assertEquals(testExternalId, registrationRequest.payload.getString("external_user_id"));
        assertEquals(mockExternalIdHash, registrationRequest.payload.getString("external_user_id_auth_hash"));

        fastColdRestartApp();

        time.advanceSystemTimeBy(60);
        OneSignalInit();
        threadAndTaskWait();

        ShadowOneSignalRestClient.Request registrationRequestAfterColdStart = ShadowOneSignalRestClient.requests.get(4);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, registrationRequestAfterColdStart.method);
        assertEquals(mockExternalIdHash, registrationRequestAfterColdStart.payload.getString("external_user_id_auth_hash"));
    }

    @Test
    public void shouldAlwaysSetExternalIdAndEmailWithAuthHashAfterRegistration() throws Exception {
        OneSignalInit();
        threadAndTaskWait();

        String testExternalId = "test_ext_id";
        String mockExternalIdHash = new String(new char[64]).replace('\0', '0');

        String email = "josh@onesignal.com";
        String mockEmailHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setExternalUserId(testExternalId, mockExternalIdHash);
        OneSignal.setEmail(email, mockEmailHash);
        threadAndTaskWait();

        ShadowOneSignalRestClient.Request registrationRequest = ShadowOneSignalRestClient.requests.get(2);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, registrationRequest.method);
        assertEquals(testExternalId, registrationRequest.payload.getString("external_user_id"));
        assertEquals(mockExternalIdHash, registrationRequest.payload.getString("external_user_id_auth_hash"));

        ShadowOneSignalRestClient.Request emailPost = ShadowOneSignalRestClient.requests.get(3);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPost.method);
        assertEquals(email, emailPost.payload.getString("identifier"));
        assertEquals(11, emailPost.payload.getInt("device_type"));
        assertEquals(mockEmailHash, emailPost.payload.getString("email_auth_hash"));

        fastColdRestartApp();

        time.advanceSystemTimeBy(60);
        OneSignalInit();
        threadAndTaskWait();

        ShadowOneSignalRestClient.Request registrationRequestAfterColdStart = ShadowOneSignalRestClient.requests.get(6);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, registrationRequestAfterColdStart.method);
        assertEquals(mockExternalIdHash, registrationRequestAfterColdStart.payload.getString("external_user_id_auth_hash"));

        ShadowOneSignalRestClient.Request emailPostAfterColdStart = ShadowOneSignalRestClient.requests.get(7);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPostAfterColdStart.method);
        assertEquals(11, emailPostAfterColdStart.payload.getInt("device_type"));
        assertEquals(mockEmailHash, emailPostAfterColdStart.payload.getString("email_auth_hash"));
    }

    @Test
    public void shouldAlwaysSetExternalIdAndSMSWithAuthHashAfterRegistration() throws Exception {
        OneSignalInit();
        threadAndTaskWait();

        String testExternalId = "test_ext_id";
        String mockExternalIdHash = new String(new char[64]).replace('\0', '0');

        String mockSMSlHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setExternalUserId(testExternalId, mockExternalIdHash);
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER, mockSMSlHash);
        threadAndTaskWait();

        ShadowOneSignalRestClient.Request registrationRequest = ShadowOneSignalRestClient.requests.get(2);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, registrationRequest.method);
        assertEquals(testExternalId, registrationRequest.payload.getString("external_user_id"));
        assertEquals(mockExternalIdHash, registrationRequest.payload.getString("external_user_id_auth_hash"));

        ShadowOneSignalRestClient.Request smsPost = ShadowOneSignalRestClient.requests.get(3);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, smsPost.method);
        assertEquals(ONESIGNAL_SMS_NUMBER, smsPost.payload.getString("identifier"));
        assertEquals(OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_SMS, smsPost.payload.getInt("device_type"));
        assertEquals(mockSMSlHash, smsPost.payload.getString("sms_auth_hash"));

        fastColdRestartApp();

        time.advanceSystemTimeBy(60);
        OneSignalInit();
        threadAndTaskWait();

        ShadowOneSignalRestClient.Request registrationRequestAfterColdStart = ShadowOneSignalRestClient.requests.get(5);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, registrationRequestAfterColdStart.method);
        assertEquals(mockExternalIdHash, registrationRequestAfterColdStart.payload.getString("external_user_id_auth_hash"));

        ShadowOneSignalRestClient.Request smsPostAfterColdStart = ShadowOneSignalRestClient.requests.get(6);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, smsPostAfterColdStart.method);
        assertEquals(OneSignalPackagePrivateHelper.UserState.DEVICE_TYPE_SMS, smsPostAfterColdStart.payload.getInt("device_type"));
        assertEquals(mockSMSlHash, smsPostAfterColdStart.payload.getString("sms_auth_hash"));
    }

    @Test
    public void shouldRemoveExternalUserId() throws Exception {
        OneSignal.setExternalUserId("test_ext_id");

        OneSignalInit();
        threadAndTaskWait();

        OneSignal.removeExternalUserId();
        threadAndTaskWait();

        assertEquals(3, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request removeIdRequest = ShadowOneSignalRestClient.requests.get(2);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, removeIdRequest.method);
        assertEquals(removeIdRequest.payload.getString("external_user_id"), "");
    }

    @Test
    public void shouldRemoveExternalUserIdFromPushWithAuthHash() throws Exception {
        String testExternalId = "test_ext_id";
        String mockExternalIdHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setExternalUserId(testExternalId, mockExternalIdHash, null);
        OneSignalInit();
        threadAndTaskWait();

        OneSignal.removeExternalUserId(getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        JSONObject expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : true" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());

        assertEquals(3, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request removeIdRequest = ShadowOneSignalRestClient.requests.get(2);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, removeIdRequest.method);
        assertEquals(removeIdRequest.payload.getString("external_user_id"), "");
        assertEquals(mockExternalIdHash, removeIdRequest.payload.getString("external_user_id_auth_hash"));
    }

    @Test
    public void shouldRemoveExternalUserIdFromEmailWithAuthHash() throws Exception {
        String testEmail = "test@test.com";
        String mockEmailHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setEmail(testEmail, mockEmailHash, getEmailUpdateHandler());
        OneSignalInit();
        threadAndTaskWait();

        OneSignal.removeExternalUserId(getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        JSONObject expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : true" +
                        "   }" + ", " +
                        "   \"email\" : {" +
                        "      \"success\" : true" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());
        assertTrue(didEmailUpdateSucceed);
        assertNull(lastEmailUpdateFailure);

        assertEquals(6, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request removeIdRequest = ShadowOneSignalRestClient.requests.get(4);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, removeIdRequest.method);
        assertEquals(removeIdRequest.payload.getString("external_user_id"), "");
        assertFalse(removeIdRequest.payload.has("external_user_id_auth_hash"));

        ShadowOneSignalRestClient.Request removeIdEmailRequest = ShadowOneSignalRestClient.requests.get(5);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, removeIdEmailRequest.method);
        assertEquals(removeIdEmailRequest.payload.getString("external_user_id"), "");
        assertEquals(mockEmailHash, removeIdEmailRequest.payload.getString("email_auth_hash"));
    }

    @Test
    public void shouldRemoveExternalUserIdFromSMSWithAuthHash() throws Exception {
        String mockSMSHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER, mockSMSHash, getSMSUpdateHandler());
        OneSignalInit();
        threadAndTaskWait();

        OneSignal.removeExternalUserId(getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        JSONObject expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : true" +
                        "   }" + ", " +
                        "   \"sms\" : {" +
                        "      \"success\" : true" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());
        assertNotNull(didSMSlUpdateSucceed);
        assertNull(lastEmailUpdateFailure);

        assertEquals(5, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request removeIdRequest = ShadowOneSignalRestClient.requests.get(3);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, removeIdRequest.method);
        assertEquals(removeIdRequest.payload.getString("external_user_id"), "");
        assertFalse(removeIdRequest.payload.has("external_user_id_auth_hash"));

        ShadowOneSignalRestClient.Request removeIdSMSRequest = ShadowOneSignalRestClient.requests.get(4);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, removeIdSMSRequest.method);
        assertEquals(removeIdSMSRequest.payload.getString("external_user_id"), "");
        assertEquals(mockSMSHash, removeIdSMSRequest.payload.getString("sms_auth_hash"));
    }

    @Test
    public void shouldRemoveExternalUserIdFromPushAndEmailWithAuthHash() throws Exception {
        String testExternalId = "test_ext_id";
        String mockExternalIdHash = new String(new char[64]).replace('\0', '0');
        String testEmail = "test@test.com";
        String mockEmailHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setExternalUserId(testExternalId, mockExternalIdHash, null);
        OneSignal.setEmail(testEmail, mockEmailHash, null);
        OneSignalInit();
        threadAndTaskWait();

        OneSignal.removeExternalUserId();
        threadAndTaskWait();

        assertEquals(6, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request removeIdRequest = ShadowOneSignalRestClient.requests.get(4);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, removeIdRequest.method);
        assertEquals(removeIdRequest.payload.getString("external_user_id"), "");
        assertEquals(mockExternalIdHash, removeIdRequest.payload.getString("external_user_id_auth_hash"));

        ShadowOneSignalRestClient.Request removeIdEmailRequest = ShadowOneSignalRestClient.requests.get(5);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, removeIdEmailRequest.method);
        assertEquals(removeIdEmailRequest.payload.getString("external_user_id"), "");
        assertEquals(mockEmailHash, removeIdEmailRequest.payload.getString("email_auth_hash"));
    }

    @Test
    public void shouldRemoveExternalUserIdFromPushAndSMSWithAuthHash() throws Exception {
        String testExternalId = "test_ext_id";
        String mockExternalIdHash = new String(new char[64]).replace('\0', '0');
        String mockSMSHash = new String(new char[64]).replace('\0', '0');

        OneSignal.setExternalUserId(testExternalId, mockExternalIdHash, null);
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER, mockSMSHash, null);
        OneSignalInit();
        threadAndTaskWait();

        OneSignal.removeExternalUserId();
        threadAndTaskWait();

        assertEquals(5, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request removeIdRequest = ShadowOneSignalRestClient.requests.get(3);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, removeIdRequest.method);
        assertEquals(removeIdRequest.payload.getString("external_user_id"), "");
        assertEquals(mockExternalIdHash, removeIdRequest.payload.getString("external_user_id_auth_hash"));

        ShadowOneSignalRestClient.Request removeIdSMSRequest = ShadowOneSignalRestClient.requests.get(4);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, removeIdSMSRequest.method);
        assertEquals(removeIdSMSRequest.payload.getString("external_user_id"), "");
        assertEquals(mockSMSHash, removeIdSMSRequest.payload.getString("sms_auth_hash"));
    }

    @Test
    public void shouldRemoveExternalUserIdFromPushAndEmailAndSMSWithAuthHash() throws Exception {
        String testExternalId = "test_ext_id";
        String mockExternalIdHash = new String(new char[64]).replace('\0', '0');
        String mockEmailHash = new String(new char[64]).replace('\0', '0');
        String mockSMSHash = new String(new char[64]).replace('\0', '1');

        OneSignal.setExternalUserId(testExternalId, mockExternalIdHash, null);
        OneSignal.setEmail(ONESIGNAL_EMAIL_ADDRESS, mockEmailHash, null);
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER, mockSMSHash, null);
        OneSignalInit();
        threadAndTaskWait();

        OneSignal.removeExternalUserId();
        threadAndTaskWait();

        assertEquals(8, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request removeIdRequest = ShadowOneSignalRestClient.requests.get(5);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, removeIdRequest.method);
        assertEquals(removeIdRequest.payload.getString("external_user_id"), "");
        assertEquals(mockExternalIdHash, removeIdRequest.payload.getString("external_user_id_auth_hash"));

        ShadowOneSignalRestClient.Request removeIdEmailRequest = ShadowOneSignalRestClient.requests.get(6);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, removeIdEmailRequest.method);
        assertEquals(removeIdEmailRequest.payload.getString("external_user_id"), "");
        assertEquals(mockEmailHash, removeIdEmailRequest.payload.getString("email_auth_hash"));

        ShadowOneSignalRestClient.Request removeIdSMSRequest = ShadowOneSignalRestClient.requests.get(7);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.PUT, removeIdSMSRequest.method);
        assertEquals(removeIdSMSRequest.payload.getString("external_user_id"), "");
        assertEquals(mockSMSHash, removeIdSMSRequest.payload.getString("sms_auth_hash"));
    }

    @Test
    public void doesNotSendSameExternalId() throws Exception {
        String testExternalId = "test_ext_id";
        OneSignal.setExternalUserId(testExternalId);

        OneSignalInit();
        threadAndTaskWait();

        assertEquals(2, ShadowOneSignalRestClient.networkCallCount);

        OneSignal.setExternalUserId(testExternalId);
        threadAndTaskWait();

        // Setting the same ID again should not generate a duplicate API request
        // The SDK should detect it is the same and not generate a request
        assertEquals(2, ShadowOneSignalRestClient.networkCallCount);
    }

    @Test
    public void sendsExternalIdOnEmailPlayers() throws Exception {
        String testExternalId = "test_ext_id";

        OneSignalInit();
        threadAndTaskWait();

        OneSignal.setEmail("brad@onesignal.com");
        threadAndTaskWait();

        int currentRequestCount = ShadowOneSignalRestClient.networkCallCount;

        OneSignal.setExternalUserId(testExternalId);
        threadAndTaskWait();

        // the SDK should have made two additional API calls
        // One to set extID on the push player record,
        // and another for the email player record
        assertEquals(ShadowOneSignalRestClient.networkCallCount, currentRequestCount + 2);

        int externalIdRequests = 0;

        for (ShadowOneSignalRestClient.Request request : ShadowOneSignalRestClient.requests) {
            if (request.payload != null && request.payload.has("external_user_id")) {
                externalIdRequests += 1;
                assertEquals(request.payload.getString("external_user_id"), testExternalId);
            }
        }

        assertEquals(externalIdRequests, 2);
    }

    @Test
    public void sendsExternalIdOnSMSPlayers() throws Exception {
        String testExternalId = "test_ext_id";

        OneSignalInit();
        threadAndTaskWait();

        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        int currentRequestCount = ShadowOneSignalRestClient.networkCallCount;

        OneSignal.setExternalUserId(testExternalId);
        threadAndTaskWait();

        // the SDK should have made two additional API calls
        // One to set extID on the push player record,
        // and another for the sms player record
        assertEquals(ShadowOneSignalRestClient.networkCallCount, currentRequestCount + 2);

        int externalIdRequests = 0;

        for (ShadowOneSignalRestClient.Request request : ShadowOneSignalRestClient.requests) {
            if (request.payload != null && request.payload.has("external_user_id")) {
                externalIdRequests += 1;
                assertEquals(request.payload.getString("external_user_id"), testExternalId);
            }
        }

        assertEquals(externalIdRequests, 2);
    }

    @Test
    public void sendsExternalIdOnEmailAndSMSPlayers() throws Exception {
        String testExternalId = "test_ext_id";

        OneSignalInit();
        threadAndTaskWait();

        OneSignal.setEmail(ONESIGNAL_EMAIL_ADDRESS);
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        int currentRequestCount = ShadowOneSignalRestClient.networkCallCount;

        OneSignal.setExternalUserId(testExternalId);
        threadAndTaskWait();

        // the SDK should have made two additional API calls
        // One to set extID on the push player record,
        // another for the sms player record
        // and another for email sms player record
        assertEquals(ShadowOneSignalRestClient.networkCallCount, currentRequestCount + 3);

        int externalIdRequests = 0;

        for (ShadowOneSignalRestClient.Request request : ShadowOneSignalRestClient.requests) {
            if (request.payload != null && request.payload.has("external_user_id")) {
                externalIdRequests += 1;
                assertEquals(request.payload.getString("external_user_id"), testExternalId);
            }
        }

        assertEquals(externalIdRequests, 3);
    }

    @Test
    public void sendExternalUserId_withCompletionHandler() throws Exception {
        String testExternalId = "test_ext_id";

        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // 2. Attempt to set external user id with callback
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 3. Make sure lastExternalUserIdResponse is equal to the expected response
        JSONObject expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : true" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());
    }

    @Test
    public void sendDifferentExternalUserId_withCompletionHandler() throws Exception {
        String testExternalId = "test_ext_id_1";

        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // 2. Attempt to set external user id with callback
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 3. Make sure lastExternalUserIdResponse is equal to the expected response
        JSONObject expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : true" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());

        // 4. Change test external user id to send
        testExternalId = "test_ext_id_2";

        // 5. Attempt to set same exact external user id with callback
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 6. Make sure lastExternalUserIdResponse is equal to the expected response
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());
    }

    @Test
    public void sendSameExternalUserId_withCompletionHandler() throws Exception {
        String testExternalId = "test_ext_id";

        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // 2. Attempt to set external user id with callback
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 3. Make sure lastExternalUserIdResponse is equal to the expected response
        JSONObject expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : true" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());

        // 4. Attempt to set same exact external user id with callback
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 5. Make sure lastExternalUserIdResponse is equal to the expected response
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());
    }

    @Test
    public void sendExternalUserId_withFailure_withCompletionHandler() throws Exception {
        String testExternalId = "test_ext_id";

        // 1. Init OneSignal
        OneSignalInit();
        threadAndTaskWait();

        // 2. Attempt to set external user id with callback and force failure on the network requests
        ShadowOneSignalRestClient.failAll = true;
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 3. Make sure lastExternalUserIdResponse is equal to the expected response
        JSONObject expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : false" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());

        // 4. Flip ShadowOneSignalRestClient.failAll flag back to false
        ShadowOneSignalRestClient.failAll = false;

        // 5. Attempt a second set external user id with callback
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 6. Make sure lastExternalUserIdResponse is equal to the expected response
        expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : true" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());
    }

    @Test
    public void sendExternalUserId_forPushAndEmail_withFailure_withCompletionHandler() throws Exception {
        String testEmail = "test@onesignal.com";
        String testExternalId = "test_ext_id";

        // 1. Init OneSignal
        OneSignalInit();
        OneSignal.setEmail(testEmail);
        threadAndTaskWait();

        // 2. Attempt to set external user id with callback and force failure on the network requests
        ShadowOneSignalRestClient.failAll = true;
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 3. Make sure lastExternalUserIdResponse has push and email with success : false
        JSONObject expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : false" +
                        "   }," +
                        "   \"email\" : {" +
                        "      \"success\" : false" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());

        // 4. Flip ShadowOneSignalRestClient.failAll flag back to false
        ShadowOneSignalRestClient.failAll = false;

        // 5. Attempt a second set external user id with callback
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 6. Make sure lastExternalUserIdResponse has push and email with success : true
        expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : true" +
                        "   }," +
                        "   \"email\" : {" +
                        "      \"success\" : true" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());
    }

    @Test
    public void sendExternalUserId_forPushAndSMS_withFailure_withCompletionHandler() throws Exception {
        String testExternalId = "test_ext_id";

        // 1. Init OneSignal
        OneSignalInit();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        // 2. Attempt to set external user id with callback and force failure on the network requests
        ShadowOneSignalRestClient.failAll = true;
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 3. Make sure lastExternalUserIdResponse has push and sms with success : false
        JSONObject expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : false" +
                        "   }," +
                        "   \"sms\" : {" +
                        "      \"success\" : false" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());

        // 4. Flip ShadowOneSignalRestClient.failAll flag back to false
        ShadowOneSignalRestClient.failAll = false;

        // 5. Attempt a second set external user id with callback
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 6. Make sure lastExternalUserIdResponse has push and sms with success : true
        expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : true" +
                        "   }," +
                        "   \"sms\" : {" +
                        "      \"success\" : true" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());
    }

    @Test
    public void sendExternalUserId_forPushAndEmailAndSMS_withFailure_withCompletionHandler() throws Exception {
        String testExternalId = "test_ext_id";

        // 1. Init OneSignal
        OneSignalInit();
        OneSignal.setEmail(ONESIGNAL_EMAIL_ADDRESS);
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        // 2. Attempt to set external user id with callback and force failure on the network requests
        ShadowOneSignalRestClient.failAll = true;
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 3. Make sure lastExternalUserIdResponse has push and sms with success : false
        JSONObject expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : false" +
                        "   }," +
                        "   \"email\" : {" +
                        "      \"success\" : false" +
                        "   }," +
                        "   \"sms\" : {" +
                        "      \"success\" : false" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());

        // 4. Flip ShadowOneSignalRestClient.failAll flag back to false
        ShadowOneSignalRestClient.failAll = false;

        // 5. Attempt a second set external user id with callback
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 6. Make sure lastExternalUserIdResponse has push and sms with success : true
        expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : true" +
                        "   }," +
                        "   \"email\" : {" +
                        "      \"success\" : true" +
                        "   }," +
                        "   \"sms\" : {" +
                        "      \"success\" : true" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());
    }

    @Test
    public void sendExternalUserId_forPush_afterLoggingOutEmail_withCompletion() throws Exception {
        String testEmail = "test@onesignal.com";
        String testExternalId = "test_ext_id";

        // 1. Init OneSignal and set email
        OneSignalInit();
        OneSignal.setEmail(testEmail);
        threadAndTaskWait();

        // 2. Logout Email
        OneSignal.logoutEmail();
        threadAndTaskWait();

        // 3. Attempt a set external user id with callback
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 4. Make sure lastExternalUserIdResponse has push with success : true
        JSONObject expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : true" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());
    }

    @Test
    public void sendExternalUserId_forPush_afterLoggingOutSMS_withCompletion() throws Exception {
        String testExternalId = "test_ext_id";

        // 1. Init OneSignal and set email
        OneSignalInit();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        // 2. Logout SMS
        OneSignal.logoutSMSNumber();
        threadAndTaskWait();

        // 4. Attempt a set external user id with callback
        OneSignal.setExternalUserId(testExternalId, getExternalUserIdUpdateCompletionHandler());
        threadAndTaskWait();

        // 5. Make sure lastExternalUserIdResponse has push with success : true
        JSONObject expectedExternalUserIdResponse = new JSONObject(
                "{" +
                        "   \"push\" : {" +
                        "      \"success\" : true" +
                        "   }" +
                        "}"
        );
        assertEquals(expectedExternalUserIdResponse.toString(), lastExternalUserIdResponse.toString());
    }

    // ####### on_session Tests ########

    @Test
    public void shouldSendOnSessionToEmail() throws Exception {
        OneSignalInit();
        OneSignal.setEmail("josh@onesignal.com");
        threadAndTaskWait();

        restartAppAndElapseTimeToNextSession(time);
        OneSignalInit();
        threadAndTaskWait();

        ShadowOneSignalRestClient.Request emailPost = ShadowOneSignalRestClient.requests.get(6);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, emailPost.method);
        assertEquals("players/b007f967-98cc-11e4-bed1-118f05be4522/on_session", emailPost.url);
    }

    @Test
    public void shouldSendOnSessionToSMS() throws Exception {
        OneSignalInit();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        restartAppAndElapseTimeToNextSession(time);
        OneSignalInit();
        threadAndTaskWait();

        ShadowOneSignalRestClient.Request smsPost = ShadowOneSignalRestClient.requests.get(5);
        assertEquals(ShadowOneSignalRestClient.REST_METHOD.POST, smsPost.method);
        assertEquals("players/" + ShadowOneSignalRestClient.SMS_USER_ID + "/on_session", smsPost.url);
    }

    // ####### on_focus Tests ########

    @Test
    public void sendsOnFocus() throws Exception {
        time.advanceSystemAndElapsedTimeBy(0);
        OneSignalInit();
        threadAndTaskWait();

        time.advanceSystemAndElapsedTimeBy(60);
        pauseActivity(blankActivityController);
        assertAndRunSyncService();

        assertOnFocusAtIndex(2, 60);
        assertRestCalls(3);
    }

    @Test
    public void sendsOnFocusToEmail() throws Exception {
        time.advanceSystemAndElapsedTimeBy(0);
        OneSignalInit();
        OneSignal.setEmail("josh@onesignal.com");
        threadAndTaskWait();

        blankActivityController.resume();
        threadAndTaskWait();
        time.advanceSystemAndElapsedTimeBy(60);
        pauseActivity(blankActivityController);
        assertAndRunSyncService();

        assertEquals(6, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request post = ShadowOneSignalRestClient.requests.get(3);
        assertFalse(post.url.contains("on_focus"));

        ShadowOneSignalRestClient.Request postPush = ShadowOneSignalRestClient.requests.get(4);
        assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511/on_focus", postPush.url);
        assertEquals(60, postPush.payload.getInt("active_time"));

        ShadowOneSignalRestClient.Request postEmail = ShadowOneSignalRestClient.requests.get(5);
        assertEquals("players/b007f967-98cc-11e4-bed1-118f05be4522/on_focus", postEmail.url);
        assertEquals(60, postEmail.payload.getInt("active_time"));
    }

    @Test
    public void sendsOnFocusToSMS() throws Exception {
        time.advanceSystemAndElapsedTimeBy(0);
        OneSignalInit();
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        blankActivityController.resume();
        threadAndTaskWait();
        time.advanceSystemAndElapsedTimeBy(60);
        pauseActivity(blankActivityController);
        assertAndRunSyncService();

        assertEquals(5, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request post = ShadowOneSignalRestClient.requests.get(2);
        assertFalse(post.url.contains("on_focus"));

        ShadowOneSignalRestClient.Request postPush = ShadowOneSignalRestClient.requests.get(3);
        assertEquals("players/a2f7f967-e8cc-11e4-bed1-118f05be4511/on_focus", postPush.url);
        assertEquals(60, postPush.payload.getInt("active_time"));

        ShadowOneSignalRestClient.Request postSMSl = ShadowOneSignalRestClient.requests.get(4);
        assertEquals("players/" + SMS_USER_ID + "/on_focus", postSMSl.url);
        assertEquals(60, postSMSl.payload.getInt("active_time"));
    }

    @Test
    public void sendsOnFocusToEmailAndSMS() throws Exception {
        time.advanceSystemAndElapsedTimeBy(0);
        OneSignalInit();
        OneSignal.setEmail(ONESIGNAL_EMAIL_ADDRESS);
        OneSignal.setSMSNumber(ONESIGNAL_SMS_NUMBER);
        threadAndTaskWait();

        blankActivityController.resume();
        threadAndTaskWait();
        time.advanceSystemAndElapsedTimeBy(60);
        pauseActivity(blankActivityController);
        assertAndRunSyncService();

        assertEquals(8, ShadowOneSignalRestClient.networkCallCount);

        ShadowOneSignalRestClient.Request post = ShadowOneSignalRestClient.requests.get(4);
        assertFalse(post.url.contains("on_focus"));

        ShadowOneSignalRestClient.Request postPush = ShadowOneSignalRestClient.requests.get(5);
        assertEquals("players/" + PUSH_USER_ID + "/on_focus", postPush.url);
        assertEquals(60, postPush.payload.getInt("active_time"));

        ShadowOneSignalRestClient.Request postEmail = ShadowOneSignalRestClient.requests.get(6);
        assertEquals("players/" + EMAIL_USER_ID + "/on_focus", postEmail.url);
        assertEquals(60, postEmail.payload.getInt("active_time"));

        ShadowOneSignalRestClient.Request postSMSl = ShadowOneSignalRestClient.requests.get(7);
        assertEquals("players/" + SMS_USER_ID + "/on_focus", postSMSl.url);
        assertEquals(60, postSMSl.payload.getInt("active_time"));
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
}
