package com.test.onesignal;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.onesignal.NotificationOpenedActivityHMS;
import com.onesignal.OneSignal;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOSViewUtils;
import com.onesignal.ShadowOSWebView;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;

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

import static com.onesignal.InAppMessagingHelpers.ONESIGNAL_APP_ID;
import static com.test.onesignal.TestHelpers.fastColdRestartApp;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;


@Config(packageName = "com.onesignal.example",
    instrumentedPackages = { "com.onesignal" },
    shadows = {
        ShadowOSUtils.class,
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

    @BeforeClass // Runs only once, before any tests
    public static void setUpClass() throws Exception {
        ShadowLog.stream = System.out;
        TestHelpers.beforeTestSuite();
        StaticResetHelper.saveStaticValues();
    }

    @Before
    public void beforeEachTest() throws Exception {
        TestHelpers.beforeTestInitAndCleanup();
    }

    private static Intent helper_baseHMSOpenIntent() {
        return new Intent()
                .setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_TASK)
                .setAction("android.intent.action.VIEW");
    }

    private static void helper_startHMSOpenActivity(@NonNull Intent intent) {
        Robolectric.buildActivity(NotificationOpenedActivityHMS.class, intent).create();
    }

    // Since the Activity has to be public it could be started outside of a OneSignal flow.
    // Ensure it doesn't crash the app.
    @Test
    public void emptyIntent_doesNotThrow() {
        helper_startHMSOpenActivity(helper_baseHMSOpenIntent());
    }

    @Test
    public void barebonesOSPayload_startsMainActivity() throws Exception {
        OneSignal.init(RuntimeEnvironment.application, "123456789", ONESIGNAL_APP_ID);
        fastColdRestartApp();

        Intent intent = helper_baseHMSOpenIntent()
            .putExtra(
                "custom",
                new JSONObject() {{
                    put("i", UUID.randomUUID().toString());
                }}.toString()
            );

        helper_startHMSOpenActivity(intent);

        Intent startedActivity = shadowOf(RuntimeEnvironment.application).getNextStartedActivity();
        assertEquals(startedActivity.getComponent().getClassName(), BlankActivity.class.getName());
    }

    @Test
    public void osIAMPreview_showsPreview() throws Exception {
        Activity activity = Robolectric.buildActivity(BlankActivity.class).create().get();
        OneSignal.init(activity, "123456789", ONESIGNAL_APP_ID);
        threadAndTaskWait();

        Intent intent = helper_baseHMSOpenIntent()
                .putExtra(
                        "custom",
                        new JSONObject() {{
                            put("i", UUID.randomUUID().toString());
                            put("a", new JSONObject() {{
                                put("os_in_app_message_preview_id", "UUID");
                            }});
                        }}.toString()
                );

        helper_startHMSOpenActivity(intent);

        assertEquals("PGh0bWw+PC9odG1sPg==", ShadowOSWebView.lastData);
    }
}
