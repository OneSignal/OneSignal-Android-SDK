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

import com.onesignal.MockOSLog;
import com.onesignal.MockOSSharedPreferences;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.ShadowOSUtils;
import com.onesignal.StaticResetHelper;
import com.onesignal.influence.OSTrackerFactory;
import com.onesignal.influence.model.OSInfluence;

import org.json.JSONArray;
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
public class TrackerUnitTests {

    private static final String NOTIFICATION_ID = "notification_id";
    private static final String IAM_ID = "iam_id";

    private OSTrackerFactory trackerFactory;
    private MockOSSharedPreferences preferences;

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

        TestHelpers.beforeTestInitAndCleanup();
    }

    @After
    public void tearDown() throws Exception {
        StaticResetHelper.restSetStaticFields();
        threadAndTaskWait();
    }

    @Test
    public void testUnattributedInitInfluence() {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        trackerFactory.initFromCache();
        List<OSInfluence> influences = trackerFactory.getInfluences();
        for (OSInfluence influence : influences) {
            assertTrue(influence.getInfluenceType().isUnattributed());
            assertNull(influence.getIds());
        }
    }

    @Test
    public void testInfluenceIdsSaved() throws Exception {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());

        assertEquals(0, trackerFactory.getNotificationChannelTracker().getLastReceivedIds().length());
        assertEquals(0, trackerFactory.getIAMChannelTracker().getLastReceivedIds().length());

        trackerFactory.getNotificationChannelTracker().saveLastId(NOTIFICATION_ID);
        trackerFactory.getIAMChannelTracker().saveLastId(IAM_ID);

        JSONArray lastNotificationIds = trackerFactory.getNotificationChannelTracker().getLastReceivedIds();
        JSONArray lastIAMIds = trackerFactory.getIAMChannelTracker().getLastReceivedIds();

        assertEquals(1, lastNotificationIds.length());
        assertEquals(NOTIFICATION_ID, lastNotificationIds.get(0));
        assertEquals(1, lastIAMIds.length());
        assertEquals(IAM_ID, lastIAMIds.get(0));
    }

    @Test
    public void testDisabledInitInfluence() {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams(false, false, false));
        trackerFactory.initFromCache();
        List<OSInfluence> influences = trackerFactory.getInfluences();
        for (OSInfluence influence : influences) {
            assertTrue(influence.getInfluenceType().isDisabled());
            assertNull(influence.getIds());
        }
    }

    @Test
    public void testUnattributedAddSessionData() {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams());
        trackerFactory.initFromCache();

        JSONObject object = new JSONObject();
        trackerFactory.addSessionData(object, trackerFactory.getInfluences());

        assertEquals(0, object.length());
    }

    @Test
    public void testDisabledAddSessionData() {
        trackerFactory.saveInfluenceParams(new OneSignalPackagePrivateHelper.RemoteOutcomeParams(false, false, false));
        trackerFactory.initFromCache();

        JSONObject object = new JSONObject();
        trackerFactory.addSessionData(object, trackerFactory.getInfluences());

        assertEquals(0, object.length());
    }

    @Test
    public void testGetChannelsByEntryPoint() {
        assertNull(trackerFactory.getChannelByEntryAction(OneSignal.AppEntryAction.APP_OPEN));
        assertNull(trackerFactory.getChannelByEntryAction(OneSignal.AppEntryAction.APP_CLOSE));
        assertEquals(NOTIFICATION_ID, trackerFactory.getChannelByEntryAction(OneSignal.AppEntryAction.NOTIFICATION_CLICK).getIdTag());
    }

    @Test
    public void testGetChannelToResetByEntryAction() {
        assertEquals(2, trackerFactory.getChannelsToResetByEntryAction(OneSignal.AppEntryAction.APP_OPEN).size());
        assertEquals(0, trackerFactory.getChannelsToResetByEntryAction(OneSignal.AppEntryAction.APP_CLOSE).size());
        assertEquals(1, trackerFactory.getChannelsToResetByEntryAction(OneSignal.AppEntryAction.NOTIFICATION_CLICK).size());
        assertEquals(IAM_ID, trackerFactory.getChannelsToResetByEntryAction(OneSignal.AppEntryAction.NOTIFICATION_CLICK).get(0).getIdTag());
    }

}