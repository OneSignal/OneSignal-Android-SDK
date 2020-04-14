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

@Config(packageName = "com.onesignal.example",
        instrumentedPackages = {"com.onesignal"},
        shadows = {
                ShadowOSUtils.class,
        },
        sdk = 21)
@RunWith(RobolectricTestRunner.class)
public class OutcomeEventUnitTests {

    private static final String OUTCOME_NAME = "testing";
    private static final String NOTIFICATION_ID = "testing";

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
        dbHelper.close();
        StaticResetHelper.restSetStaticFields();
        threadAndTaskWait();
    }

    @Test
    public void testDirectOutcomeSuccess() throws Exception {
        service.setSuccess(true);
        sessionManager.initSessionFromCache();
        // Set DIRECT notification id influence
        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_SUCCESS").start();

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"notification_ids\":[\"testing\"],\"id\":\"testing\",\"device_type\":1,\"direct\":true}", service.getLastJsonObjectSent());
    }

    @Test
    public void testIndirectOutcomeSuccess() throws Exception {
        service.setSuccess(true);
        sessionManager.initSessionFromCache();
        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        // Set DIRECT notification id influence
        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);
        // Restart session by app open should set INDIRECT influence
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_SUCCESS").start();

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"notification_ids\":[\"testing\"],\"id\":\"testing\",\"device_type\":1,\"direct\":false}", service.getLastJsonObjectSent());
    }

    @Test
    public void testUnattributedOutcomeSuccess() throws Exception {
        service.setSuccess(true);
        // Init session should set UNATTRIBUTED influence
        sessionManager.initSessionFromCache();

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_SUCCESS").start();

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"device_type\":1}", service.getLastJsonObjectSent());
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

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_SUCCESS").start();

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

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_SUCCESS").start();

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"weight\":1.1,\"device_type\":1}", service.getLastJsonObjectSent());
    }

    @Test
    public void testDisableOutcomeWithValueSuccess() throws Exception {
        service.setSuccess(true);
        // DISABLED influence
        trackerFactory.saveInfluenceParams(disabledInfluenceParams);

        controller.sendOutcomeEventWithValue(OUTCOME_NAME, 1.1f);
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_SUCCESS").start();

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

        controller.sendOutcomeEventWithValue(OUTCOME_NAME, 1.1f);
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_SUCCESS").start();

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"notification_ids\":[\"testing\"],\"id\":\"testing\",\"weight\":1.1,\"device_type\":1,\"direct\":true}", service.getLastJsonObjectSent());
    }

    @Test
    public void testIndirectOutcomeWithValueSuccess() throws Exception {
        service.setSuccess(true);
        sessionManager.initSessionFromCache();
        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        // Set DIRECT notification id influence
        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);
        // Restart session by app open should set INDIRECT influence
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);

        controller.sendOutcomeEventWithValue(OUTCOME_NAME, 1.1f);
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_SUCCESS").start();

        threadAndTaskWait();
        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"notification_ids\":[\"testing\"],\"id\":\"testing\",\"weight\":1.1,\"device_type\":1,\"direct\":false}", service.getLastJsonObjectSent());
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

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAIL").start();

        threadAndTaskWait();
        assertEquals(1, outcomeEvents.size());
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getOutcomeId());
        assertEquals("{\"id\":\"testing\",\"device_type\":1}", service.getLastJsonObjectSent());

        controller.cleanOutcomes();

        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);
        controller.sendUniqueOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAIL").start();

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

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAIL").start();

        threadAndTaskWait();
        assertEquals(1, outcomeEvents.size());
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getOutcomeId());
    }

    @Test
    public void testOutcomeFailSavedOnDB() throws Exception {
        service.setSuccess(false);
        // Restart session by app open should set UNATTRIBUTED influence
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);

        controller.sendOutcomeEvent(OUTCOME_NAME);
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAIL").start();

        threadAndTaskWait();
        assertTrue(outcomeEvents.size() > 0);
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getOutcomeId());
    }

    @Test
    public void testOutcomeMultipleFailsSavedOnDB() throws Exception {
        lockTimeTo(0);
        service.setSuccess(false);

        // Restart session by app open should set UNATTRIBUTED influence
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);
        controller.sendOutcomeEvent(OUTCOME_NAME);

        sessionManager.onNotificationReceived(NOTIFICATION_ID);
        // Set DIRECT notification id influence
        sessionManager.onDirectInfluenceFromNotificationOpen(NOTIFICATION_ID);
        controller.sendOutcomeEvent(OUTCOME_NAME + "1");

        // Restart session by app open should set INDIRECT influence
        sessionManager.restartSessionIfNeeded(OneSignal.AppEntryAction.APP_OPEN);
        controller.sendOutcomeEvent(OUTCOME_NAME + "2");
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAIL").start();
        threadAndTaskWait();

        assertEquals(3, outcomeEvents.size());

        // DISABLED influence
        trackerFactory.saveInfluenceParams(disabledInfluenceParams);
        controller.sendOutcomeEvent(OUTCOME_NAME + "3");
        controller.sendOutcomeEvent(OUTCOME_NAME + "4");
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAILS").start();

        threadAndTaskWait();
        assertEquals(3, outcomeEvents.size());
        for (OSOutcomeEventParams outcomeEvent : outcomeEvents) {
            // UNATTRIBUTED Case
            if (outcomeEvent.isUnattributed()) {
                assertEquals("OSOutcomeEventParams{outcomeId='testing', outcomeSource=null, weight=0.0, timestamp=0}", outcomeEvent.toString());
            } else if (outcomeEvent.getOutcomeSource().getIndirectBody() != null) { // INDIRECT Case
                assertEquals("OSOutcomeEventParams{outcomeId='testing2', outcomeSource=OSOutcomeSource{directBody=null, indirectBody=OSOutcomeSourceBody{notificationIds=[\"testing\"], inAppMessagesIds=[]}}, weight=0.0, timestamp=0}", outcomeEvent.toString());
            } else { // DIRECT Case
                assertEquals("OSOutcomeEventParams{outcomeId='testing1', outcomeSource=OSOutcomeSource{directBody=OSOutcomeSourceBody{notificationIds=[\"testing\"], inAppMessagesIds=[]}, indirectBody=null}, weight=0.0, timestamp=0}", outcomeEvent.toString());
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

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAILS").start();

        threadAndTaskWait();
        assertEquals(5, outcomeEvents.size());

        service.setSuccess(true);

        controller.sendSavedOutcomes();
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAILS").start();
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

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAILS").start();

        threadAndTaskWait();
        assertEquals(1, outcomeEvents.size());
        assertEquals(1.1f, outcomeEvents.get(0).getWeight(), 0);
        assertEquals("{\"id\":\"testing\",\"weight\":1.1,\"device_type\":1}", service.getLastJsonObjectSent());

        service.setSuccess(true);
        service.resetLastJsonObjectSent();
        controller.sendSavedOutcomes();
        threadAndTaskWait();

        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.setOutcomes(repository.getSavedOutcomeEvents());
            }
        }, "OS_GET_SAVED_OUTCOMES_FAILS").start();
        threadAndTaskWait();

        assertEquals(0, outcomeEvents.size());
        assertEquals("{\"id\":\"testing\",\"weight\":1.1,\"device_type\":1}", service.getLastJsonObjectSent());
    }

}