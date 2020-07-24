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

import android.support.annotation.NonNull;

import com.onesignal.MockOSLog;
import com.onesignal.MockOSSharedPreferences;
import com.onesignal.MockSessionManager;
import com.onesignal.OSSessionManager;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.ShadowOSUtils;
import com.onesignal.StaticResetHelper;
import com.onesignal.influence.OSChannelTracker;
import com.onesignal.influence.OSTrackerFactory;
import com.onesignal.influence.model.OSInfluence;
import com.onesignal.influence.model.OSInfluenceChannel;
import com.onesignal.influence.model.OSInfluenceType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.List;

import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@Config(packageName = "com.onesignal.example",
        instrumentedPackages = {"com.onesignal"},
        shadows = {
                ShadowOSUtils.class,
        },
        sdk = 21)
@RunWith(RobolectricTestRunner.class)
public class SessionManagerUnitTests {

    private static final String GENERIC_ID = "generic_id";
    private static final String NOTIFICATION_ID = "notification_id";
    private static final String IAM_ID = "iam_id";
    private static final int INFLUENCE_ID_LIMIT = 10;

    private MockSessionManager sessionManager;
    private OSTrackerFactory trackerFactory;
    private MockOSSharedPreferences preferences;
    private List<OSInfluence> lastInfluencesBySessionEnding;

    private OSSessionManager.SessionListener sessionListener = new OSSessionManager.SessionListener() {
        @Override
        public void onSessionEnding(@NonNull List<OSInfluence> lastInfluences) {
            lastInfluencesBySessionEnding = lastInfluences;
        }
    };

    @BeforeClass // Runs only once, before any tests
    public static void setUpClass() throws Exception {
        ShadowLog.stream = System.out;

        TestHelpers.beforeTestSuite();

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        StaticResetHelper.saveStaticValues();
    }

    @Before // Before each test
    public void beforeEachTest() throws Exception {
        MockOSLog logger = new MockOSLog();
        preferences = new MockOSSharedPreferences();
        trackerFactory = new OSTrackerFactory(preferences, logger);
        sessionManager = new MockSessionManager(sessionListener, trackerFactory, logger);
        TestHelpers.beforeTestInitAndCleanup();
    }

    @After
    public void tearDown() throws Exception {
        lastInfluencesBySessionEnding = null;

        StaticResetHelper.restSetStaticFields();
        threadAndTaskWait();
    }

    @Test
    public void testUnattributedInitInfluence() {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        List<OSInfluence> influences = sessionManager.getInfluences();
        for (OSInfluence influence : influences) {
            assertTrue(influence.getInfluenceType().isUnattributed());
            assertNull(influence.getIds());
        }
    }

    @Test
    public void testIndirectInfluence() throws Exception {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();
        sessionManager.onInAppMessageReceived(GENERIC_ID);
        sessionManager.onNotificationReceived(GENERIC_ID);
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);

        for (OSInfluence influence : sessionManager.getInfluences()) {
            assertTrue(influence.getInfluenceType().isIndirect());
            assertEquals(1, influence.getIds().length());
            assertEquals(GENERIC_ID, influence.getIds().get(0));
        }
    }

    @Test
    public void testIndirectNotificationInitInfluence() throws JSONException {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        OSChannelTracker notificationTracker = trackerFactory.getNotificationChannelTracker();
        assertEquals(0, notificationTracker.getLastReceivedIds().length());
        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        sessionManager.attemptSessionUpgrade(OneSignal.AppEntryAction.APP_OPEN);

        notificationTracker = trackerFactory.getNotificationChannelTracker();
        OSInfluence influence = notificationTracker.getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.INDIRECT, notificationTracker.getInfluenceType());
        assertEquals(NOTIFICATION_ID, notificationTracker.getLastReceivedIds().get(0));
        assertEquals(OSInfluenceChannel.NOTIFICATION, influence.getInfluenceChannel());
        assertEquals(OSInfluenceType.INDIRECT, influence.getInfluenceType());
        assertEquals(1, influence.getIds().length());
        assertEquals(NOTIFICATION_ID, influence.getIds().get(0));
    }

    @Test
    public void testDirectNotificationInitInfluence() throws JSONException {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        OSChannelTracker notificationTracker = trackerFactory.getNotificationChannelTracker();
        assertEquals(0, notificationTracker.getLastReceivedIds().length());
        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);

        notificationTracker = trackerFactory.getNotificationChannelTracker();
        OSInfluence influence = notificationTracker.getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.DIRECT, notificationTracker.getInfluenceType());
        assertEquals(NOTIFICATION_ID, notificationTracker.getLastReceivedIds().get(0));
        assertEquals(OSInfluenceChannel.NOTIFICATION, influence.getInfluenceChannel());
        assertEquals(OSInfluenceType.DIRECT, influence.getInfluenceType());
        assertEquals(1, influence.getIds().length());
        assertEquals(NOTIFICATION_ID, influence.getIds().get(0));
    }

    @Test
    public void testIndirectIAMInitInfluence() throws JSONException {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        OSChannelTracker iamTracker = trackerFactory.getIAMChannelTracker();
        assertEquals(0, iamTracker.getLastReceivedIds().length());

        sessionManager.onInAppMessageReceived(IAM_ID);

        iamTracker = trackerFactory.getIAMChannelTracker();
        OSInfluence influence = iamTracker.getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.INDIRECT, iamTracker.getInfluenceType());
        assertEquals(IAM_ID, iamTracker.getLastReceivedIds().get(0));
        assertEquals(OSInfluenceChannel.IAM, influence.getInfluenceChannel());
        assertEquals(OSInfluenceType.INDIRECT, influence.getInfluenceType());
        assertEquals(1, influence.getIds().length());
        assertEquals(IAM_ID, influence.getIds().get(0));
    }

    @Test
    public void testDirectIAMInitInfluence() throws JSONException {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        OSChannelTracker iamTracker = trackerFactory.getIAMChannelTracker();
        assertEquals(0, iamTracker.getLastReceivedIds().length());

        sessionManager.onInAppMessageReceived(IAM_ID);
        sessionManager.onDirectInfluenceFromIAMClick(IAM_ID);

        iamTracker = trackerFactory.getIAMChannelTracker();
        OSInfluence influence = iamTracker.getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.DIRECT, iamTracker.getInfluenceType());
        assertEquals(IAM_ID, iamTracker.getLastReceivedIds().get(0));
        assertEquals(OSInfluenceChannel.IAM, influence.getInfluenceChannel());
        assertEquals(OSInfluenceType.DIRECT, influence.getInfluenceType());
        assertEquals(1, influence.getIds().length());
        assertEquals(IAM_ID, influence.getIds().get(0));
    }

    @Test
    public void testDirectIAMResetInfluence() throws JSONException {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        OSChannelTracker iamTracker = trackerFactory.getIAMChannelTracker();
        assertEquals(0, iamTracker.getLastReceivedIds().length());

        sessionManager.onInAppMessageReceived(IAM_ID);
        sessionManager.onDirectInfluenceFromIAMClick(IAM_ID);
        sessionManager.onDirectInfluenceFromIAMClickFinished();

        iamTracker = trackerFactory.getIAMChannelTracker();
        OSInfluence influence = iamTracker.getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.INDIRECT, iamTracker.getInfluenceType());
        assertEquals(IAM_ID, iamTracker.getLastReceivedIds().get(0));
        assertEquals(OSInfluenceChannel.IAM, influence.getInfluenceChannel());
        assertEquals(OSInfluenceType.INDIRECT, influence.getInfluenceType());
        assertEquals(1, influence.getIds().length());
        assertEquals(IAM_ID, influence.getIds().get(0));
    }

    @Test
    public void testUnattributedAddSessionData() {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        JSONObject object = new JSONObject();
        sessionManager.addSessionIds(object, sessionManager.getInfluences());

        assertEquals(0, object.length());
    }

    @Test
    public void testIndirectAddSessionData() throws JSONException {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        sessionManager.attemptSessionUpgrade(OneSignal.AppEntryAction.APP_OPEN);
        sessionManager.onInAppMessageReceived(IAM_ID);
        sessionManager.attemptSessionUpgrade(OneSignal.AppEntryAction.APP_OPEN);

        JSONObject object = new JSONObject();
        sessionManager.addSessionIds(object, sessionManager.getInfluences());

        // Only Notification data should be added
        // IAM data is not added on on_focus call
        assertEquals(2, object.length());
        assertEquals(false, object.get("direct"));
        assertEquals(new JSONArray("[\"notification_id\"]"), object.get("notification_ids"));
    }

    @Test
    public void testDirectAddSessionData() throws JSONException {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);
        sessionManager.onInAppMessageReceived(IAM_ID);
        sessionManager.onDirectInfluenceFromIAMClick(IAM_ID);

        JSONObject object = new JSONObject();
        sessionManager.addSessionIds(object, sessionManager.getInfluences());

        // Only Notification data should be added
        // IAM data is not added on on_focus call
        assertEquals(2, object.length());
        assertEquals(true, object.get("direct"));
        assertEquals(new JSONArray("[\"notification_id\"]"), object.get("notification_ids"));
    }

    @Test
    public void testDisabledAddSessionData() {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams(false, false, false));
        sessionManager.initSessionFromCache();

        JSONObject object = new JSONObject();
        sessionManager.addSessionIds(object, sessionManager.getInfluences());

        assertEquals(0, object.length());
    }

    @Test
    public void testDirectWithNullNotification() {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        sessionManager.onNotificationReceived(null);
        sessionManager.onDirectInfluenceFromNotificationOpen(null);

        OSInfluence influence = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.UNATTRIBUTED, influence.getInfluenceType());
        assertNull(influence.getIds());
    }

    @Test
    public void testDirectWithEmptyNotification() {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        sessionManager.onNotificationReceived("");
        sessionManager.onDirectInfluenceFromNotificationOpen("");

        OSInfluence influence = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.UNATTRIBUTED, influence.getInfluenceType());
        assertNull(influence.getIds());
    }

    @Test
    public void testSessionUpgradeFromAppClosed() throws Exception {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        List<OSInfluence> influences = sessionManager.getInfluences();

        for (OSInfluence influence : influences) {
            assertEquals(OSInfluenceType.UNATTRIBUTED, influence.getInfluenceType());
            assertNull(influence.getIds());
        }

        sessionManager.onNotificationReceived(GENERIC_ID);
        sessionManager.onInAppMessageReceived(GENERIC_ID);

        influences = sessionManager.getInfluences();

        for (OSInfluence influence : influences) {
            switch (influence.getInfluenceChannel()) {
                case NOTIFICATION:
                    assertEquals(OSInfluenceType.UNATTRIBUTED, influence.getInfluenceType());
                    assertNull(influence.getIds());
                    break;
                case IAM:
                    assertEquals(OSInfluenceType.INDIRECT, influence.getInfluenceType());
                    assertEquals(1, influence.getIds().length());
                    break;
            }
        }

        sessionManager.attemptSessionUpgrade(OneSignal.AppEntryAction.APP_CLOSE);
        threadAndTaskWait();

        influences = sessionManager.getInfluences();

        for (OSInfluence influence : influences) {
            switch (influence.getInfluenceChannel()) {
                case NOTIFICATION:
                    assertEquals(OSInfluenceType.UNATTRIBUTED, influence.getInfluenceType());
                    assertNull(influence.getIds());
                    break;
                case IAM:
                    assertEquals(OSInfluenceType.INDIRECT, influence.getInfluenceType());
                    assertEquals(1, influence.getIds().length());
                    break;
            }
        }

        // We test that channel ending is working
        assertNull(lastInfluencesBySessionEnding);
    }

    @Test
    public void testSessionUpgradeFromUnattributedToIndirect() throws Exception {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        List<OSInfluence> influences = sessionManager.getInfluences();

        for (OSInfluence influence : influences) {
            assertEquals(OSInfluenceType.UNATTRIBUTED, influence.getInfluenceType());
            assertNull(influence.getIds());
        }

        sessionManager.onNotificationReceived(GENERIC_ID);
        sessionManager.onInAppMessageReceived(GENERIC_ID);

        influences = sessionManager.getInfluences();

        for (OSInfluence influence : influences) {
            switch (influence.getInfluenceChannel()) {
                case NOTIFICATION:
                    assertEquals(OSInfluenceType.UNATTRIBUTED, influence.getInfluenceType());
                    assertNull(influence.getIds());
                    break;
                case IAM:
                    assertEquals(OSInfluenceType.INDIRECT, influence.getInfluenceType());
                    assertEquals(1, influence.getIds().length());
                    break;
            }
        }

        sessionManager.attemptSessionUpgrade(OneSignal.AppEntryAction.APP_OPEN);
        threadAndTaskWait();

        influences = sessionManager.getInfluences();

        for (OSInfluence influence : influences) {
            assertEquals(OSInfluenceType.INDIRECT, influence.getInfluenceType());
            assertEquals(1, influence.getIds().length());
            assertEquals(GENERIC_ID, influence.getIds().get(0));
        }

        // We test that channel ending is working for both IAM and Notification
        assertEquals(1, lastInfluencesBySessionEnding.size());
        OSInfluence endingNotificationInfluence = lastInfluencesBySessionEnding.get(0);

        assertEquals(OSInfluenceChannel.NOTIFICATION, endingNotificationInfluence.getInfluenceChannel());
        assertEquals(OSInfluenceType.UNATTRIBUTED, endingNotificationInfluence.getInfluenceType());
        assertNull(endingNotificationInfluence.getIds());
    }

    @Test
    public void testSessionUpgradeFromUnattributedToDirectNotification() throws Exception {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        OSInfluence iamInfluences = trackerFactory.getIAMChannelTracker().getCurrentSessionInfluence();
        OSInfluence notificationInfluences = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.UNATTRIBUTED, iamInfluences.getInfluenceType());
        assertEquals(OSInfluenceType.UNATTRIBUTED, notificationInfluences.getInfluenceType());

        sessionManager.onNotificationReceived(GENERIC_ID);
        sessionManager.onInAppMessageReceived(GENERIC_ID);
        sessionManager.onDirectInfluenceFromNotificationOpen(GENERIC_ID);
        threadAndTaskWait();

        iamInfluences = trackerFactory.getIAMChannelTracker().getCurrentSessionInfluence();
        notificationInfluences = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.INDIRECT, iamInfluences.getInfluenceType());
        assertEquals(1, iamInfluences.getIds().length());
        assertEquals(GENERIC_ID, iamInfluences.getIds().get(0));

        assertEquals(OSInfluenceType.DIRECT, notificationInfluences.getInfluenceType());
        assertEquals(1, notificationInfluences.getIds().length());
        assertEquals(GENERIC_ID, notificationInfluences.getIds().get(0));

        // We test that channel ending is working for Notification
        assertEquals(1, lastInfluencesBySessionEnding.size());
        OSInfluence endingNotificationInfluence = lastInfluencesBySessionEnding.get(0);

        assertEquals(OSInfluenceChannel.NOTIFICATION, endingNotificationInfluence.getInfluenceChannel());
        assertEquals(OSInfluenceType.UNATTRIBUTED, endingNotificationInfluence.getInfluenceType());
        assertNull(endingNotificationInfluence.getIds());
    }

    @Test
    public void testSessionUpgradeFromIndirectToDirect() throws Exception {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        sessionManager.onNotificationReceived(GENERIC_ID);
        sessionManager.onInAppMessageReceived(GENERIC_ID);
        sessionManager.attemptSessionUpgrade(OneSignal.AppEntryAction.APP_OPEN);

        OSInfluence iamInfluences = trackerFactory.getIAMChannelTracker().getCurrentSessionInfluence();
        OSInfluence notificationInfluences = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.INDIRECT, iamInfluences.getInfluenceType());
        assertEquals(OSInfluenceType.INDIRECT, notificationInfluences.getInfluenceType());
        assertEquals(GENERIC_ID, notificationInfluences.getIds().get(0));

        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);
        threadAndTaskWait();

        iamInfluences = trackerFactory.getIAMChannelTracker().getCurrentSessionInfluence();
        notificationInfluences = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.INDIRECT, iamInfluences.getInfluenceType());
        assertEquals(1, iamInfluences.getIds().length());
        assertEquals(GENERIC_ID, iamInfluences.getIds().get(0));

        assertEquals(OSInfluenceType.DIRECT, notificationInfluences.getInfluenceType());
        assertEquals(1, notificationInfluences.getIds().length());
        assertEquals(NOTIFICATION_ID, notificationInfluences.getIds().get(0));

        // We test that channel ending is working for both IAM and Notification
        assertEquals(1, lastInfluencesBySessionEnding.size());
        OSInfluence endingNotificationInfluence = lastInfluencesBySessionEnding.get(0);

        assertEquals(OSInfluenceChannel.NOTIFICATION, endingNotificationInfluence.getInfluenceChannel());
        assertEquals(OSInfluenceType.INDIRECT, endingNotificationInfluence.getInfluenceType());
        assertEquals(1, endingNotificationInfluence.getIds().length());
        assertEquals(GENERIC_ID, endingNotificationInfluence.getIds().get(0));
    }

    @Test
    public void testSessionUpgradeFromDirectToDirectDifferentID() throws Exception {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        sessionManager.onNotificationReceived(GENERIC_ID);
        sessionManager.onDirectInfluenceFromNotificationOpen(GENERIC_ID);

        OSInfluence notificationInfluences = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.DIRECT, notificationInfluences.getInfluenceType());
        assertEquals(GENERIC_ID, notificationInfluences.getIds().get(0));

        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);
        threadAndTaskWait();

        notificationInfluences = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.DIRECT, notificationInfluences.getInfluenceType());
        assertEquals(1, notificationInfluences.getIds().length());
        assertEquals(NOTIFICATION_ID, notificationInfluences.getIds().get(0));

        // We test that channel ending is working
        assertEquals(1, lastInfluencesBySessionEnding.size());
        assertEquals(OSInfluenceChannel.NOTIFICATION, lastInfluencesBySessionEnding.get(0).getInfluenceChannel());
        assertEquals(OSInfluenceType.DIRECT, lastInfluencesBySessionEnding.get(0).getInfluenceType());
        assertEquals(1, lastInfluencesBySessionEnding.get(0).getIds().length());
        assertEquals(GENERIC_ID, lastInfluencesBySessionEnding.get(0).getIds().get(0));
    }

    @Test
    public void testSessionUpgradeFromDirectToDirectSameID() throws Exception {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        sessionManager.onNotificationReceived(GENERIC_ID);
        sessionManager.onDirectInfluenceFromNotificationOpen(GENERIC_ID);

        OSInfluence notificationInfluences = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.DIRECT, notificationInfluences.getInfluenceType());
        assertEquals(GENERIC_ID, notificationInfluences.getIds().get(0));

        sessionManager.attemptSessionUpgrade(OneSignal.AppEntryAction.NOTIFICATION_CLICK);
        threadAndTaskWait();

        notificationInfluences = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.DIRECT, notificationInfluences.getInfluenceType());
        assertEquals(1, notificationInfluences.getIds().length());
        assertEquals(GENERIC_ID, notificationInfluences.getIds().get(0));

        // We test that channel ending is working
        assertEquals(1, lastInfluencesBySessionEnding.size());
        assertEquals(OSInfluenceChannel.NOTIFICATION, lastInfluencesBySessionEnding.get(0).getInfluenceChannel());
        assertEquals(OSInfluenceType.UNATTRIBUTED, lastInfluencesBySessionEnding.get(0).getInfluenceType());
        assertNull(lastInfluencesBySessionEnding.get(0).getIds());
    }

    @Test
    public void testSessionUpgradeFromDirectToDirectEndChannelsDirect() throws Exception {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        sessionManager.initSessionFromCache();

        sessionManager.onNotificationReceived(GENERIC_ID);
        sessionManager.onDirectInfluenceFromNotificationOpen(GENERIC_ID);
        sessionManager.onInAppMessageReceived(IAM_ID);
        sessionManager.onDirectInfluenceFromIAMClick(IAM_ID);
        threadAndTaskWait();

        OSInfluence iamInfluences = trackerFactory.getIAMChannelTracker().getCurrentSessionInfluence();
        OSInfluence notificationInfluences = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.DIRECT, iamInfluences.getInfluenceType());
        assertEquals(OSInfluenceType.DIRECT, notificationInfluences.getInfluenceType());
        assertEquals(IAM_ID, iamInfluences.getIds().get(0));
        assertEquals(GENERIC_ID, notificationInfluences.getIds().get(0));

        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);
        threadAndTaskWait();

        iamInfluences = trackerFactory.getIAMChannelTracker().getCurrentSessionInfluence();
        notificationInfluences = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.DIRECT, notificationInfluences.getInfluenceType());
        assertEquals(1, notificationInfluences.getIds().length());
        assertEquals(NOTIFICATION_ID, notificationInfluences.getIds().get(0));
        assertEquals(OSInfluenceType.INDIRECT, iamInfluences.getInfluenceType());
        assertEquals(IAM_ID, iamInfluences.getIds().get(0));

        // We test that channel ending is working for both IAM and Notification
        assertEquals(2, lastInfluencesBySessionEnding.size());
        OSInfluence endingNotificationInfluence = lastInfluencesBySessionEnding.get(0);
        OSInfluence endingIAMInfluence = lastInfluencesBySessionEnding.get(1);

        assertEquals(OSInfluenceChannel.NOTIFICATION, endingNotificationInfluence.getInfluenceChannel());
        assertEquals(OSInfluenceType.DIRECT, endingNotificationInfluence.getInfluenceType());
        assertEquals(1, endingNotificationInfluence.getIds().length());
        assertEquals(GENERIC_ID, endingNotificationInfluence.getIds().get(0));

        assertEquals(OSInfluenceChannel.IAM, endingIAMInfluence.getInfluenceChannel());
        assertEquals(OSInfluenceType.DIRECT, endingIAMInfluence.getInfluenceType());
        assertEquals(1, endingIAMInfluence.getIds().length());
        assertEquals(IAM_ID, endingIAMInfluence.getIds().get(0));
    }

    @Test
    public void testRestartSessionIfNeededFromOpen() throws JSONException {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        sessionManager.onInAppMessageReceived(IAM_ID);
        sessionManager.onNotificationReceived(NOTIFICATION_ID);

        OSInfluence iamInfluences = trackerFactory.getIAMChannelTracker().getCurrentSessionInfluence();
        assertEquals(OSInfluenceType.INDIRECT, iamInfluences.getInfluenceType());
        assertEquals(IAM_ID, iamInfluences.getIds().get(0));

        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);

        iamInfluences = trackerFactory.getIAMChannelTracker().getCurrentSessionInfluence();
        OSInfluence notificationInfluences = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.INDIRECT, iamInfluences.getInfluenceType());
        assertEquals(IAM_ID, iamInfluences.getIds().get(0));
        assertEquals(OSInfluenceType.INDIRECT, notificationInfluences.getInfluenceType());
        assertEquals(NOTIFICATION_ID, notificationInfluences.getIds().get(0));
    }

    @Test
    public void testRestartSessionIfNeededFromClose() throws JSONException {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        sessionManager.onInAppMessageReceived(IAM_ID);
        sessionManager.onNotificationReceived(NOTIFICATION_ID);

        OSInfluence iamInfluences = trackerFactory.getIAMChannelTracker().getCurrentSessionInfluence();
        assertEquals(OSInfluenceType.INDIRECT, iamInfluences.getInfluenceType());
        assertEquals(IAM_ID, iamInfluences.getIds().get(0));

        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_CLOSE);

        iamInfluences = trackerFactory.getIAMChannelTracker().getCurrentSessionInfluence();
        OSInfluence notificationInfluences = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.INDIRECT, iamInfluences.getInfluenceType());
        assertEquals(IAM_ID, iamInfluences.getIds().get(0));
        assertEquals(OSInfluenceType.UNATTRIBUTED, notificationInfluences.getInfluenceType());
        assertNull(notificationInfluences.getIds());
    }

    @Test
    public void testRestartSessionIfNeededFromNotification() throws JSONException {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        sessionManager.onInAppMessageReceived(IAM_ID);
        sessionManager.onNotificationReceived(NOTIFICATION_ID);

        OSInfluence iamInfluences = trackerFactory.getIAMChannelTracker().getCurrentSessionInfluence();
        assertEquals(OSInfluenceType.INDIRECT, iamInfluences.getInfluenceType());
        assertEquals(IAM_ID, iamInfluences.getIds().get(0));

        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.NOTIFICATION_CLICK);

        iamInfluences = trackerFactory.getIAMChannelTracker().getCurrentSessionInfluence();
        OSInfluence notificationInfluences = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();

        assertEquals(OSInfluenceType.INDIRECT, iamInfluences.getInfluenceType());
        assertEquals(IAM_ID, iamInfluences.getIds().get(0));
        assertEquals(OSInfluenceType.UNATTRIBUTED, notificationInfluences.getInfluenceType());
        assertNull(notificationInfluences.getIds());
    }

    @Test
    public void testIndirectNotificationQuantityInfluence() throws Exception {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        for (int i = 0; i < INFLUENCE_ID_LIMIT + 5; i++) {
            sessionManager.onNotificationReceived(GENERIC_ID + i);
        }

        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);

        OSInfluence influence = trackerFactory.getNotificationChannelTracker().getCurrentSessionInfluence();
        assertTrue(influence.getInfluenceType().isIndirect());
        assertEquals(INFLUENCE_ID_LIMIT, influence.getIds().length());
        assertEquals(GENERIC_ID + "5", influence.getIds().get(0));
    }

    @Test
    public void testIndirectIAMQuantityInfluence() throws Exception {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        for (int i = 0; i < INFLUENCE_ID_LIMIT + 5; i++) {
            sessionManager.onInAppMessageReceived(GENERIC_ID + i);
        }

        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);

        OSInfluence influence = trackerFactory.getIAMChannelTracker().getCurrentSessionInfluence();
        assertTrue(influence.getInfluenceType().isIndirect());
        assertEquals(INFLUENCE_ID_LIMIT, influence.getIds().length());
        assertEquals(GENERIC_ID + "5", influence.getIds().get(0));
    }

}