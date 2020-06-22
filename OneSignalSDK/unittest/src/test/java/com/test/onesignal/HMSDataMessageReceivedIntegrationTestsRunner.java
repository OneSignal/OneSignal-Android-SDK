package com.test.onesignal;

import android.annotation.SuppressLint;
import android.app.Activity;

import com.onesignal.OneSignalPackagePrivateHelper.NotificationPayloadProcessorHMS;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowRoboNotificationManager;
import com.onesignal.StaticResetHelper;
import com.onesignal.example.BlankActivity;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.UUID;

import static com.onesignal.OneSignalPackagePrivateHelper.OSNotificationFormatHelper.PAYLOAD_OS_NOTIFICATION_ID;
import static com.onesignal.OneSignalPackagePrivateHelper.OSNotificationFormatHelper.PAYLOAD_OS_ROOT_CUSTOM;
import static junit.framework.Assert.assertEquals;

@Config(
    // NOTE: We can remove "instrumentedPackages" if we make ShadowRoboNotificationManager's constructor public
    instrumentedPackages = { "com.onesignal" },
    packageName = "com.onesignal.example",
    shadows = {
        ShadowRoboNotificationManager.class,
        ShadowNotificationManagerCompat.class
    },
    sdk = 26
)
@RunWith(RobolectricTestRunner.class)
public class HMSDataMessageReceivedIntegrationTestsRunner {
    @SuppressLint("StaticFieldLeak")
    private static Activity blankActivity;
    private static ActivityController<BlankActivity> blankActivityController;

    private static final String ALERT_TEST_MESSAGE_BODY = "Test Message body";

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
    }

    private static @NonNull String helperBasicOSPayload() throws JSONException {
        return new JSONObject() {{
            put(PAYLOAD_OS_ROOT_CUSTOM, new JSONObject() {{
                put(PAYLOAD_OS_NOTIFICATION_ID, UUID.randomUUID().toString());
            }});
            put("alert", ALERT_TEST_MESSAGE_BODY);
        }}.toString();
    }

    @Test
    public void nullData_shouldNotThrow() {
        NotificationPayloadProcessorHMS.processDataMessageReceived(blankActivity, null);
    }

    @Test
    public void blankData_shouldNotThrow() {
        NotificationPayloadProcessorHMS.processDataMessageReceived(blankActivity, "");
    }

    @Test
    public void basicPayload_shouldDisplayNotification() throws JSONException {
        blankActivityController.pause();
        NotificationPayloadProcessorHMS.processDataMessageReceived(blankActivity, helperBasicOSPayload());
        assertEquals(ALERT_TEST_MESSAGE_BODY, ShadowRoboNotificationManager.getLastShadowNotif().getBigText());
    }

    // NOTE: More tests can be added but they would be duplicated with GenerateNotificationRunner
    //       In 4.0.0 or later these should be written in a reusable way between HMS, FCM, and ADM
}
