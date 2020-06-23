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
import com.onesignal.MockOneSignalAPIClient;
import com.onesignal.MockOneSignalDBHelper;
import com.onesignal.MockOutcomeEventsController;
import com.onesignal.MockSessionManager;
import com.onesignal.OSSessionManager;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.OneSignalRemoteParams;
import com.onesignal.ShadowOSUtils;
import com.onesignal.StaticResetHelper;
import com.onesignal.influence.OSTrackerFactory;
import com.onesignal.influence.model.OSInfluence;
import com.onesignal.influence.model.OSInfluenceType;
import com.onesignal.outcomes.OSOutcomeEventsFactory;
import com.onesignal.outcomes.domain.OSOutcomeEventsRepository;
import com.onesignal.outcomes.model.OSOutcomeEventParams;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.List;

import static com.test.onesignal.TestHelpers.lockTimeTo;
import static com.test.onesignal.TestHelpers.threadAndTaskWait;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Config(packageName = "com.onesignal.example",
        instrumentedPackages = {"com.onesignal"},
        shadows = {
                ShadowOSUtils.class,
        },
        sdk = 21)
@RunWith(RobolectricTestRunner.class)
public class OutcomeEventV2UnitTests {

    private static final String OUTCOME_NAME = "testing";
    private static final String IAM_ID = "iam_id";
    private static final String NOTIFICATION_ID = "notification_id";

    private MockOutcomeEventsController controller;
    private MockOneSignalAPIClient service;
    private OSOutcomeEventsRepository repository;
    private MockOSSharedPreferences preferences;
    private OSTrackerFactory trackerFactory;
    private MockSessionManager sessionManager;
    private MockOneSignalDBHelper dbHelper;
    private MockOSLog logWrapper = new MockOSLog();
    private OSSessionManager.SessionListener sessionListener = new OSSessionManager.SessionListener() {
        @Override
        public void onSessionEnding(@NonNull List<OSInfluence> lastInfluences) {

        }
    };

    private OneSignalRemoteParams.InfluenceParams disabledInfluenceParams = new OneSignalPackagePrivateHelper.RemoteOutcomeParams(false, false, false);

    private static List<OSOutcomeEventParams> outcomeEvents;

    public interface OutcomeEventsHandler {

        void setOutcomes(List<OSOutcomeEventParams> outcomes);
    }

    private OutcomeEventsHandler handler = new OutcomeEventsHandler() {
        @Override
        public void setOutcomes(List<OSOutcomeEventParams> outcomes) {
            outcomeEvents = outcomes;
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
        outcomeEvents = null;

        dbHelper = new MockOneSignalDBHelper(RuntimeEnvironment.application);
        // Mock on a custom HashMap in order to not use custom context
        preferences = new MockOSSharedPreferences();
        // Save v2 flag
        String v2Name = preferences.getOutcomesV2KeyName();
        preferences.saveBool(preferences.getPreferencesName(), v2Name, true);

        trackerFactory = new OSTrackerFactory(preferences, logWrapper);
        sessionManager = new MockSessionManager(sessionListener, trackerFactory, logWrapper);
        service = new MockOneSignalAPIClient();
        OSOutcomeEventsFactory factory = new OSOutcomeEventsFactory(logWrapper, service, dbHelper, preferences);
        controller = new MockOutcomeEventsController(sessionManager, factory);

        TestHelpers.beforeTestInitAndCleanup();
        repository = factory.getRepository();
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
    }

    @After
    public void tearDown() throws Exception {
        StaticResetHelper.restSetStaticFields();
        threadAndTaskWait();
    }

    @Test
    public void testDirectNotificationOutcomeSuccess() throws Exception {
        service.setSuccess(true);
        sessionManager.initSessionFromCache();
        // Set DIRECT notification id influence
        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"sources\":{\"direct\":{\"notification_ids\":[\"notification_id\"],\"in_app_message_ids\":[]}},\"device_type\":1}", service.getLastJsonObjectSent());
    }

    @Test
    public void testDirectIAMOutcomeSuccess() throws Exception {
        service.setSuccess(true);
        sessionManager.initSessionFromCache();
        // Set DIRECT iam id influence
        sessionManager.onDirectInfluenceFromIAMClick(IAM_ID);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"sources\":{\"direct\":{\"notification_ids\":[],\"in_app_message_ids\":[\"iam_id\"]}},\"device_type\":1}", service.getLastJsonObjectSent());
    }

    @Test
    public void testDirectIAMAndNotificationOutcomeSuccess() throws Exception {
        service.setSuccess(true);
        sessionManager.initSessionFromCache();
        // Set DIRECT notification id influence
        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);
        // Set DIRECT iam id influence
        sessionManager.onDirectInfluenceFromIAMClick(IAM_ID);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"sources\":{\"direct\":{\"notification_ids\":[\"notification_id\"],\"in_app_message_ids\":[\"iam_id\"]}},\"device_type\":1}", service.getLastJsonObjectSent());
    }

    @Test
    public void testIndirectNotificationOutcomeSuccess() throws Exception {
        service.setSuccess(true);
        sessionManager.initSessionFromCache();
        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        // Set DIRECT notification id influence
        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);
        // Restart session by app open should set INDIRECT influence
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"sources\":{\"indirect\":{\"notification_ids\":[\"notification_id\"],\"in_app_message_ids\":[]}},\"device_type\":1}", service.getLastJsonObjectSent());
    }

    @Test
    public void testIndirectIAMOutcomeSuccess() throws Exception {
        service.setSuccess(true);
        sessionManager.initSessionFromCache();
        sessionManager.onInAppMessageReceived(IAM_ID);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"sources\":{\"indirect\":{\"notification_ids\":[],\"in_app_message_ids\":[\"iam_id\"]}},\"device_type\":1}", service.getLastJsonObjectSent());
    }

    @Test
    public void testIndirectIAMAndNotificationOutcomeSuccess() throws Exception {
        service.setSuccess(true);
        sessionManager.initSessionFromCache();
        sessionManager.onInAppMessageReceived(IAM_ID);
        sessionManager.onNotificationReceived(NOTIFICATION_ID);

        // Restart session by app open should set INDIRECT influence
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"sources\":{\"indirect\":{\"notification_ids\":[\"notification_id\"],\"in_app_message_ids\":[\"iam_id\"]}},\"device_type\":1}", service.getLastJsonObjectSent());
    }

    @Test
    public void testUnattributedOutcomeSuccess() throws Exception {
        service.setSuccess(true);
        // Init session should set UNATTRIBUTED influence
        sessionManager.initSessionFromCache();

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"sources\":{},\"device_type\":1}", service.getLastJsonObjectSent());
    }

    @Test
    public void testDisabledOutcomeSuccess() throws Exception {
        service.setSuccess(true);
        // DISABLED influence
        trackerFactory.saveInfluenceParams(disabledInfluenceParams);
        // Init session should set UNATTRIBUTED influence but is DISABLED
        sessionManager.initSessionFromCache();

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{}", service.getLastJsonObjectSent());
    }

    @Test
    public void testUnattributedOutcomeWithValueSuccess() throws Exception {
        service.setSuccess(true);
        // Init session should set UNATTRIBUTED influence
        sessionManager.initSessionFromCache();

        controller.sendOutcomeEventWithValue(OUTCOME_NAME, 1.1f);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"sources\":{},\"weight\":1.1,\"device_type\":1}", service.getLastJsonObjectSent());
    }

    @Test
    public void testDisableOutcomeWithValueSuccess() throws Exception {
        service.setSuccess(true);
        // DISABLED influence
        trackerFactory.saveInfluenceParams(disabledInfluenceParams);

        controller.sendOutcomeEventWithValue(OUTCOME_NAME, 1.1f);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{}", service.getLastJsonObjectSent());
    }

    @Test
    public void testDirectOutcomeWithValueSuccess() throws Exception {
        service.setSuccess(true);
        sessionManager.initSessionFromCache();
        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        // Set DIRECT notification id influence
        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);
        sessionManager.onDirectInfluenceFromIAMClick(IAM_ID);

        controller.sendOutcomeEventWithValue(OUTCOME_NAME, 1.1f);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"sources\":{\"direct\":{\"notification_ids\":[\"notification_id\"],\"in_app_message_ids\":[\"iam_id\"]}},\"weight\":1.1,\"device_type\":1}", service.getLastJsonObjectSent());
    }

    @Test
    public void testDirectOutcomeSaveIndirectSuccess() throws Exception {
        service.setSuccess(true);
        sessionManager.initSessionFromCache();
        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        sessionManager.onInAppMessageReceived(IAM_ID);
        // Set DIRECT notification id influence
        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);
        sessionManager.onDirectInfluenceFromIAMClick(IAM_ID);

        controller.sendOutcomeEventWithValue(OUTCOME_NAME, 1.1f);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"sources\":{\"direct\":{\"notification_ids\":[\"notification_id\"],\"in_app_message_ids\":[\"iam_id\"]}},\"weight\":1.1,\"device_type\":1}", service.getLastJsonObjectSent());

        sessionManager.initSessionFromCache();
        assertEquals(OSInfluenceType.INDIRECT, trackerFactory.getIAMChannelTracker().getInfluenceType());
        assertEquals(1, trackerFactory.getIAMChannelTracker().getIndirectIds().length());
        assertEquals(IAM_ID, trackerFactory.getIAMChannelTracker().getIndirectIds().get(0));
    }

    @Test
    public void testIndirectOutcomeWithValueSuccess() throws Exception {
        service.setSuccess(true);
        sessionManager.initSessionFromCache();
        sessionManager.onInAppMessageReceived(IAM_ID);
        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        // Restart session by app open should set INDIRECT influence
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);

        controller.sendOutcomeEventWithValue(OUTCOME_NAME, 1.1f);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"sources\":{\"indirect\":{\"notification_ids\":[\"notification_id\"],\"in_app_message_ids\":[\"iam_id\"]}},\"weight\":1.1,\"device_type\":1}", service.getLastJsonObjectSent());
    }

    @Test
    public void testUniqueOutcomeFailSavedOnDBResetSession() throws Exception {
        service.setSuccess(false);
        // Init session should set UNATTRIBUTED influence
        sessionManager.initSessionFromCache();

        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);
        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);
        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(1, outcomeEvents.size());
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getOutcomeId());
        assertEquals("{\"id\":\"testing\",\"sources\":{},\"device_type\":1}", service.getLastJsonObjectSent());

        controller.cleanOutcomes();

        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);
        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(2, outcomeEvents.size());
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getOutcomeId());
        assertEquals(OUTCOME_NAME, outcomeEvents.get(1).getOutcomeId());
    }

    @Test
    public void testUniqueOutcomeFailSavedOnDB() throws Exception {
        service.setSuccess(false);
        // Restart session by app open should set UNATTRIBUTED influence
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);

        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);
        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);
        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);

        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(1, outcomeEvents.size());
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getOutcomeId());
    }

    @Test
    public void testOutcomeDirectFailSavedOnDB() throws Exception {
        service.setSuccess(false);
        sessionManager.initSessionFromCache();
        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);
        sessionManager.onInAppMessageReceived(IAM_ID);
        sessionManager.onDirectInfluenceFromIAMClick(IAM_ID);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertTrue(outcomeEvents.size() > 0);
        OSOutcomeEventParams params = outcomeEvents.get(0);
        assertEquals(OUTCOME_NAME, params.getOutcomeId());
        assertEquals(new Float(0), params.getWeight());
        assertTrue(params.getTimestamp() > 0);
        assertNotNull(params.getOutcomeSource());
        // Direct body
        assertEquals(1, params.getOutcomeSource().getDirectBody().getInAppMessagesIds().length());
        assertEquals(IAM_ID, params.getOutcomeSource().getDirectBody().getInAppMessagesIds().get(0));
        assertEquals(1, params.getOutcomeSource().getDirectBody().getNotificationIds().length());
        assertEquals(NOTIFICATION_ID, params.getOutcomeSource().getDirectBody().getNotificationIds().get(0));
        // Indirect body
        assertNull(params.getOutcomeSource().getIndirectBody());
    }

    @Test
    public void testOutcomeIndirectFailSavedOnDB() throws Exception {
        service.setSuccess(false);
        sessionManager.initSessionFromCache();
        sessionManager.onInAppMessageReceived(IAM_ID);
        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertTrue(outcomeEvents.size() > 0);
        OSOutcomeEventParams params = outcomeEvents.get(0);
        assertEquals(OUTCOME_NAME, params.getOutcomeId());
        assertEquals(new Float(0), params.getWeight());
        assertTrue(params.getTimestamp() > 0);
        assertNotNull(params.getOutcomeSource());
        // Indirect body
        assertEquals(1, params.getOutcomeSource().getIndirectBody().getInAppMessagesIds().length());
        assertEquals(IAM_ID, params.getOutcomeSource().getIndirectBody().getInAppMessagesIds().get(0));
        assertEquals(1, params.getOutcomeSource().getIndirectBody().getNotificationIds().length());
        assertEquals(NOTIFICATION_ID, params.getOutcomeSource().getIndirectBody().getNotificationIds().get(0));
        // Direct body
        assertNull(params.getOutcomeSource().getDirectBody());
    }

    @Test
    public void testOutcomeUnattributedFailSavedOnDB() throws Exception {
        service.setSuccess(false);
        // Restart session by app open should set UNATTRIBUTED influence
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertTrue(outcomeEvents.size() > 0);
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getOutcomeId());
    }

    @Test
    public void testOutcomeMultipleFailsSavedOnDB() throws Exception {
        lockTimeTo(0);
        service.setSuccess(false);

        // Init session should set UNATTRIBUTED influence
        sessionManager.initSessionFromCache();
        controller.sendOutcomeEvent(OUTCOME_NAME);
        // Set last influence ids
        sessionManager.onInAppMessageReceived(IAM_ID);
        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        // Set DIRECT notification id influence and INDIRECT iam id influence because of upgrade
        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);
        controller.sendOutcomeEvent(OUTCOME_NAME + "1");

        // Restart session by app open should set INDIRECT influence for both channels
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);
        // Set DIRECT for iam id influence
        sessionManager.onDirectInfluenceFromIAMClick(IAM_ID);
        controller.sendOutcomeEvent(OUTCOME_NAME + "2");
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();

        assertEquals(3, outcomeEvents.size());

        // DISABLED influence
        trackerFactory.saveInfluenceParams(disabledInfluenceParams);
        controller.sendOutcomeEvent(OUTCOME_NAME + "3");
        controller.sendOutcomeEvent(OUTCOME_NAME + "4");
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        // Disables outcomes should not be sent
        assertEquals(3, outcomeEvents.size());
        for (OSOutcomeEventParams outcomeEvent : outcomeEvents) {
            // UNATTRIBUTED Case
            if (outcomeEvent.getOutcomeId().equals(OUTCOME_NAME)) {
                assertEquals("OSOutcomeEventParams{outcomeId='testing', outcomeSource=null, weight=0.0, timestamp=0}", outcomeEvent.toString());
            } else if (outcomeEvent.getOutcomeId().equals(OUTCOME_NAME + "1")) { // DIRECT By Notification INDIRECT by iam
                assertEquals("OSOutcomeEventParams{outcomeId='testing1', outcomeSource=OSOutcomeSource{directBody=OSOutcomeSourceBody{notificationIds=[\"notification_id\"], inAppMessagesIds=[]}, " +
                        "indirectBody=OSOutcomeSourceBody{notificationIds=[], inAppMessagesIds=[\"iam_id\"]}}, weight=0.0, timestamp=0}", outcomeEvent.toString());
            } else if (outcomeEvent.getOutcomeId().equals(OUTCOME_NAME + "2")) { // INDIRECT By Notification DIRECT by iam
                assertEquals("OSOutcomeEventParams{outcomeId='testing2', outcomeSource=OSOutcomeSource{directBody=OSOutcomeSourceBody{notificationIds=[], inAppMessagesIds=[\"iam_id\"]}, " +
                        "indirectBody=OSOutcomeSourceBody{notificationIds=[\"notification_id\"], inAppMessagesIds=[]}}, weight=0.0, timestamp=0}", outcomeEvent.toString());
            } // DISABLED Case should not be save
        }
    }

    @Test
    public void testSendFailedOutcomesOnDB() throws Exception {
        service.setSuccess(false);
        // Restart session by app open should set UNATTRIBUTED influence
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        controller.sendOutcomeEvent(OUTCOME_NAME + "1");
        controller.sendOutcomeEventWithValue(OUTCOME_NAME + "2", 1);
        controller.sendOutcomeEvent(OUTCOME_NAME + "3");
        controller.sendOutcomeEventWithValue(OUTCOME_NAME + "4", 1.1f);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(5, outcomeEvents.size());

        service.setSuccess(true);

        controller.sendSavedOutcomes();
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();

        assertEquals(0, outcomeEvents.size());
    }

    @Test
    public void testSendFailedOutcomeWithValueOnDB() throws Exception {
        lockTimeTo(0);
        service.setSuccess(false);
        // Restart session by app open should set UNATTRIBUTED influence
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);
        controller.sendOutcomeEventWithValue(OUTCOME_NAME, 1.1f);
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();
        assertEquals(1, outcomeEvents.size());
        assertEquals(1.1f, outcomeEvents.get(0).getWeight(), 0);
        assertEquals("{\"id\":\"testing\",\"sources\":{},\"weight\":1.1,\"device_type\":1}", service.getLastJsonObjectSent());

        service.setSuccess(true);
        service.resetLastJsonObjectSent();
        controller.sendSavedOutcomes();
        threadAndTaskWait();

        handler.setOutcomes(repository.getSavedOutcomeEvents());

        threadAndTaskWait();

        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"weight\":1.1,\"device_type\":1}", service.getLastJsonObjectSent());
    }

}