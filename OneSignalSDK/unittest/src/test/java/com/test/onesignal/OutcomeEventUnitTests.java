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

import com.onesignal.MockOutcomeEventsController;
import com.onesignal.MockOutcomeEventsRepository;
import com.onesignal.MockOutcomeEventsService;
import com.onesignal.MockOutcomesUtils;
import com.onesignal.MockSessionManager;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalDbHelper;
import com.onesignal.OneSignalPackagePrivateHelper.OSSessionManager;
import com.onesignal.OutcomeEvent;
import com.onesignal.ShadowOSUtils;
import com.onesignal.StaticResetHelper;

import org.json.JSONArray;
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
import static junit.framework.Assert.assertNull;
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
    private static final int NOTIFICATION_LIMIT = 10;

    private MockOutcomeEventsController controller;
    private MockOutcomeEventsRepository repository;
    private MockOutcomeEventsService service;
    private MockSessionManager sessionManager;
    private MockOutcomesUtils notificationData;
    private OneSignalDbHelper dbHelper;

    private static List<OutcomeEvent> outcomeEvents;

    public interface OutcomeEventsHandler {

        void setOutcomes(List<OutcomeEvent> outcomes);
    }

    private OutcomeEventsHandler handler = new OutcomeEventsHandler() {
        @Override
        public void setOutcomes(List<OutcomeEvent> outcomes) {
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

        sessionManager = new MockSessionManager();
        notificationData = new MockOutcomesUtils();
        dbHelper = OneSignalDbHelper.getInstance(RuntimeEnvironment.application);
        service = new MockOutcomeEventsService();
        repository = new MockOutcomeEventsRepository(service, dbHelper);
        controller = new MockOutcomeEventsController(sessionManager, repository);

        TestHelpers.beforeTestInitAndCleanup();
    }

    @After
    public void tearDown() throws Exception {
        dbHelper.cleanOutcomeDatabase();
        dbHelper.close();
        notificationData.clearNotificationSharedPreferences();
        sessionManager.resetMock();
        StaticResetHelper.restSetStaticFields();
        threadAndTaskWait();
    }

    @Test
    public void testDirectOutcomeSuccess() throws Exception {
        service.setSuccess(true);
        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setNotificationIds(new JSONArray().put(NOTIFICATION_ID))
                .setSession(OSSessionManager.Session.DIRECT)
                .build());

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
        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setNotificationIds(new JSONArray().put(NOTIFICATION_ID))
                .setSession(OSSessionManager.Session.INDIRECT)
                .build());

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
        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setSession(OSSessionManager.Session.UNATTRIBUTED)
                .build());

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
        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setSession(OSSessionManager.Session.DISABLED)
                .build());

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
    public void testOutcomeWithValueSuccess() throws Exception {
        service.setSuccess(true);
        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setSession(OSSessionManager.Session.UNATTRIBUTED)
                .build());

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
        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setSession(OSSessionManager.Session.DISABLED)
                .build());

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
        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setNotificationIds(new JSONArray().put(NOTIFICATION_ID))
                .setSession(OSSessionManager.Session.DIRECT)
                .build());

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
        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setNotificationIds(new JSONArray().put(NOTIFICATION_ID))
                .setSession(OSSessionManager.Session.INDIRECT)
                .build());

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
        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setSession(OSSessionManager.Session.UNATTRIBUTED)
                .build());

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
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getName());
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
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getName());
        assertEquals(OUTCOME_NAME, outcomeEvents.get(1).getName());
    }

    @Test
    public void testUniqueOutcomeFailSavedOnDB() throws Exception {
        service.setSuccess(false);
        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setSession(OSSessionManager.Session.UNATTRIBUTED)
                .build());

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
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getName());
    }

    @Test
    public void testOutcomeFailSavedOnDB() throws Exception {
        service.setSuccess(false);
        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setSession(OSSessionManager.Session.UNATTRIBUTED)
                .build());

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
        assertEquals(OUTCOME_NAME, outcomeEvents.get(0).getName());
    }

    @Test
    public void testOutcomeMultipleFailsSavedOnDB() throws Exception {
        lockTimeTo(0);
        service.setSuccess(false);

        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setSession(OSSessionManager.Session.UNATTRIBUTED)
                .build());
        controller.sendOutcomeEvent(OUTCOME_NAME);

        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setNotificationIds(new JSONArray().put(NOTIFICATION_ID))
                .setSession(OSSessionManager.Session.DIRECT)
                .build());
        controller.sendOutcomeEvent(OUTCOME_NAME + "1");

        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setNotificationIds(new JSONArray().put(NOTIFICATION_ID))
                .setSession(OSSessionManager.Session.INDIRECT)
                .build());
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

        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setSession(OSSessionManager.Session.DISABLED)
                .build());
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
        for (OutcomeEvent outcomeEvent : outcomeEvents) {
            if (outcomeEvent.getSession().isDirect()) {
                assertEquals("OutcomeEvent{session=DIRECT, notificationIds=[\"testing\"], name='testing1', timestamp=0, weight=0.0}", outcomeEvent.toString());
            } else if (outcomeEvent.getSession().isIndirect()) {
                assertEquals("OutcomeEvent{session=INDIRECT, notificationIds=[\"testing\"], name='testing2', timestamp=0, weight=0.0}", outcomeEvent.toString());
            } else {
                assertEquals("OutcomeEvent{session=UNATTRIBUTED, notificationIds=[], name='testing', timestamp=0, weight=0.0}", outcomeEvent.toString());
            }
        }
    }

    @Test
    public void testSendFailedOutcomesOnDB() throws Exception {
        service.setSuccess(false);
        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setSession(OSSessionManager.Session.UNATTRIBUTED)
                .build());

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
        sessionManager.setSessionResult(OSSessionManager.SessionResult.Builder.newInstance()
                .setSession(OSSessionManager.Session.UNATTRIBUTED)
                .build());
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

    @Test
    public void testIndirectSession() throws Exception {
        notificationData.markLastNotificationReceived(NOTIFICATION_ID);

        sessionManager.startSession();
        threadAndTaskWait();
        assertTrue(sessionManager.getSession().isIndirect());
        threadAndTaskWait();
        assertEquals(1, sessionManager.getLastNotificationsReceivedIds().length());
    }

    @Test
    public void testIndirectQuantitySession() throws Exception {
        for (int i = 0; i < NOTIFICATION_LIMIT + 5; i++) {
            notificationData.markLastNotificationReceived(NOTIFICATION_ID + i);
        }

        sessionManager.startSession();
        assertTrue(sessionManager.getSession().isIndirect());
        assertNull(sessionManager.getDirectNotificationId());
        assertEquals(NOTIFICATION_LIMIT, sessionManager.getLastNotificationsReceivedIds().length());
        assertEquals(NOTIFICATION_ID + "5", sessionManager.getLastNotificationsReceivedIds().get(0));
    }

    @Test
    public void testDirectSession() {
        for (int i = 0; i < NOTIFICATION_LIMIT + 5; i++) {
            notificationData.markLastNotificationReceived(NOTIFICATION_ID + i);
        }

        sessionManager.onDirectSessionFromNotificationOpen(NOTIFICATION_ID);
        assertTrue(sessionManager.getSession().isDirect());
        assertNull(sessionManager.getIndirectNotificationIds());
        assertEquals(NOTIFICATION_ID, sessionManager.getDirectNotificationId());
    }

    @Test
    public void testUnattributedSession() {
        sessionManager.startSession();

        assertTrue(sessionManager.getSession().isUnattributed());
        assertEquals(new JSONArray(), sessionManager.getLastNotificationsReceivedIds());
        assertNull(sessionManager.getDirectNotificationId());
    }

}