package com.test.onesignal;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.onesignal.NotificationOpenedActivityHMS;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalPackagePrivateHelper.UserState;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOSViewUtils;
import com.onesignal.ShadowOSWebView;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorHMS;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.UUID;

import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor.PUSH_ADDITIONAL_DATA_KEY;
import static com.onesignal.OneSignalPackagePrivateHelper.OSNotificationFormatHelper.PAYLOAD_OS_ROOT_CUSTOM;
import static com.onesignal.OneSignalPackagePrivateHelper.OSNotificationFormatHelper.PAYLOAD_OS_NOTIFICATION_ID;
import static com.onesignal.OneSignalPackagePrivateHelper.GenerateNotification.BUNDLE_KEY_ACTION_ID;
import static com.onesignal.InAppMessagingHelpers.ONESIGNAL_APP_ID;
import static com.test.onesignal.RestClientAsserts.assertNotificationOpenAtIndex;
import static com.test.onesignal.TestHelpers.fastColdRestartApp;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

@Config(
    packageName = "com.onesignal.example",
    shadows = {
        ShadowOSUtils.class,
        ShadowPushRegistratorHMS.class,
        ShadowOneSignalRestClient.class,
        ShadowCustomTabsClient.class,
        ShadowOSWebView.class,
        ShadowOSViewUtils.class,
        ShadowCustomTabsClient.class,
        ShadowCustomTabsSession.class
    },
    sdk = 26
)
@RunWith(RobolectricTestRunner.class)
public class NotificationOpenedActivityHMSIntegrationTestsRunner {

    private static final String TEST_ACTION_ID = "myTestActionId";

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
    }

    private static @NonNull Intent helper_baseHMSOpenIntent() {
        return new Intent()
                .setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .setAction("android.intent.action.VIEW");
    }

    private static @NonNull Intent helper_basicOSHMSOpenIntent() throws JSONException {
        return helper_baseHMSOpenIntent()
                .putExtra(
                        PAYLOAD_OS_ROOT_CUSTOM,
                        new JSONObject() {{
                            put(PAYLOAD_OS_NOTIFICATION_ID, UUID.randomUUID().toString());
                        }}.toString()
        );
    }

    private static @NonNull Intent helper_basicOSHMSOpenIntentWithActionId(final @NonNull String actionId) throws JSONException {
        return helper_baseHMSOpenIntent()
                .putExtra(
                        PAYLOAD_OS_ROOT_CUSTOM,
                        new JSONObject() {{
                            put(PAYLOAD_OS_NOTIFICATION_ID, UUID.randomUUID().toString());
                            put(BUNDLE_KEY_ACTION_ID, actionId);
                        }}.toString()
                );
    }

    private static void helper_startHMSOpenActivity(@NonNull Intent intent) {
        Robolectric.buildActivity(NotificationOpenedActivityHMS.class, intent).create();
    }

    private static void helper_initSDKAndFireHMSNotificationBarebonesOSOpenIntent() throws Exception {
        Intent intent = helper_basicOSHMSOpenIntent();
        helper_initSDKAndFireHMSNotificationOpenWithIntent(intent);
    }

    private static void helper_initSDKAndFireHMSNotificationActionButtonTapIntent(@NonNull String actionId) throws Exception {
        Intent intent = helper_basicOSHMSOpenIntentWithActionId(actionId);
        helper_initSDKAndFireHMSNotificationOpenWithIntent(intent);
    }

    private static void helper_initSDKAndFireHMSNotificationOpenWithIntent(@NonNull Intent intent) throws Exception {
        OneSignal.init(RuntimeEnvironment.application, "123456789", ONESIGNAL_APP_ID);
        fastColdRestartApp();

        helper_startHMSOpenActivity(intent);
    }

    // Since the Activity has to be public it could be started outside of a OneSignal flow.
    // Ensure it doesn't crash the app.
    @Test
    public void emptyIntent_doesNotThrow() {
        helper_startHMSOpenActivity(helper_baseHMSOpenIntent());
    }

    @Test
    public void barebonesOSPayload_startsMainActivity() throws Exception {
        helper_initSDKAndFireHMSNotificationBarebonesOSOpenIntent();

        Intent startedActivity = shadowOf(RuntimeEnvironment.application).getNextStartedActivity();
        assertEquals(startedActivity.getComponent().getClassName(), BlankActivity.class.getName());
    }

    @Test
    public void barebonesOSPayload_makesNotificationOpenRequest() throws Exception {
        helper_initSDKAndFireHMSNotificationBarebonesOSOpenIntent();
        assertNotificationOpenAtIndex(1, UserState.DEVICE_TYPE_HUAWEI);
    }

    private static String lastActionId;
    @Test
    public void firesOSNotificationOpenCallbackWithActionId() throws Exception {
        helper_initSDKAndFireHMSNotificationActionButtonTapIntent(TEST_ACTION_ID);

        OneSignal.startInit(RuntimeEnvironment.application).setNotificationOpenedHandler(new OneSignal.NotificationOpenedHandler() {
            @Override
            public void notificationOpened(OSNotificationOpenResult result) {
                lastActionId = result.action.actionID;
            }
        }).init();

        assertEquals(TEST_ACTION_ID, lastActionId);
    }

    @Test
    public void osIAMPreview_showsPreview() throws Exception {
        Activity activity = Robolectric.buildActivity(BlankActivity.class).create().get();
        OneSignal.init(activity, "123456789", ONESIGNAL_APP_ID);
        threadAndTaskWait();

        Intent intent = helper_baseHMSOpenIntent()
                .putExtra(
                        PAYLOAD_OS_ROOT_CUSTOM,
                        new JSONObject() {{
                            put(PAYLOAD_OS_NOTIFICATION_ID, UUID.randomUUID().toString());
                            put(PUSH_ADDITIONAL_DATA_KEY, new JSONObject() {{
                                put("os_in_app_message_preview_id", "UUID");
                            }});
                        }}.toString()
                );

        helper_startHMSOpenActivity(intent);

        assertEquals("PGh0bWw+PC9odG1sPg==", ShadowOSWebView.lastData);
    }
}
