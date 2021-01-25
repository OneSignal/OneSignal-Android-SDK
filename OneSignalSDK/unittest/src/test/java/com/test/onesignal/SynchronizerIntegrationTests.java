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

import static com.onesignal.OneSignal.ExternalIdErrorType.REQUIRES_EXTERNAL_ID_AUTH;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_getSessionListener;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setSessionManager;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setTime;
import static com.onesignal.OneSignalPackagePrivateHelper.OneSignal_setTrackerFactory;
import static com.onesignal.ShadowOneSignalRestClient.setRemoteParamsGetHtmlResponse;
import static com.test.onesignal.TestHelpers.afterTestCleanup;
import static com.test.onesignal.TestHelpers.fastColdRestartApp;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

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
    public void shouldAlwaysSetExternalIdWithAuthHashAAfterRegistration() throws Exception {
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
