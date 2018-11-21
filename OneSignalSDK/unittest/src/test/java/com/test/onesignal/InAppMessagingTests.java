package com.test.onesignal;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;

import com.onesignal.BuildConfig;
import com.onesignal.OSEmailSubscriptionObserver;
import com.onesignal.OSEmailSubscriptionState;
import com.onesignal.OSEmailSubscriptionStateChanges;
import com.onesignal.OSNotification;
import com.onesignal.OSNotificationAction;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OSNotificationPayload;
import com.onesignal.OSPermissionObserver;
import com.onesignal.OSPermissionStateChanges;
import com.onesignal.OSPermissionSubscriptionState;
import com.onesignal.OSSubscriptionObserver;
import com.onesignal.OSSubscriptionStateChanges;
import com.onesignal.OSTrigger;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalDbHelper;
import com.onesignal.ShadowAdvertisingIdProviderGPS;
import com.onesignal.ShadowBadgeCountUpdater;
import com.onesignal.ShadowCustomTabsClient;
import com.onesignal.ShadowCustomTabsSession;
import com.onesignal.ShadowFirebaseAnalytics;
import com.onesignal.ShadowGoogleApiClientBuilder;
import com.onesignal.ShadowGoogleApiClientCompatProxy;
import com.onesignal.ShadowFusedLocationApiWrapper;
import com.onesignal.ShadowJobService;
import com.onesignal.ShadowLocationGMS;
import com.onesignal.ShadowLocationUpdateListener;
import com.onesignal.ShadowNotificationManagerCompat;
import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowOneSignal;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.OneSignalPackagePrivateHelper;
import com.onesignal.ShadowPushRegistratorGCM;
import com.onesignal.ShadowRoboNotificationManager;
import com.onesignal.StaticResetHelper;
import com.onesignal.SyncJobService;
import com.onesignal.SyncService;
import com.onesignal.example.BlankActivity;
import com.onesignal.OneSignal.ChangeTagsUpdateHandler;

import com.onesignal.OSInAppMessage;
import com.onesignal.InAppMessagingHelpers;
import com.onesignal.OSTrigger.OSTriggerOperatorType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.android.controller.ActivityController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;



import static com.onesignal.OneSignalPackagePrivateHelper.GcmBroadcastReceiver_processBundle;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationBundleProcessor_Process;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationOpenedProcessor_processFromContext;
import static com.onesignal.OneSignalPackagePrivateHelper.bundleAsJSONObject;
import static com.test.onesignal.GenerateNotificationRunner.getBaseNotifBundle;

import static com.test.onesignal.TestHelpers.afterTestCleanup;
import static com.test.onesignal.TestHelpers.fastAppRestart;
import static com.test.onesignal.TestHelpers.flushBufferedSharedPrefs;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.Is.is;

import static org.junit.Assert.assertThat;

import static org.robolectric.Shadows.shadowOf;

import static com.onesignal.ShadowOneSignalRestClient.REST_METHOD;

@Config(packageName = "com.onesignal.example",
        shadows = {
                ShadowOneSignalRestClient.class,
                ShadowPushRegistratorGCM.class,
                ShadowOSUtils.class,
                ShadowAdvertisingIdProviderGPS.class,
                ShadowCustomTabsClient.class,
                ShadowCustomTabsSession.class,
                ShadowNotificationManagerCompat.class,
                ShadowJobService.class
        },
        instrumentedPackages = {"com.onesignal"},
        constants = BuildConfig.class,
        sdk = 21)
@RunWith(RobolectricTestRunner.class)
public class InAppMessagingTests {
    public static OSInAppMessage message;

    public static final String testMessageId = "a4b3gj7f-d8cc-11e4-bed1-df8f05be55ba";
    public static final String testContentId = "d8cc-11e4-bed1-df8f05be55ba-a4b3gj7f";

    @BeforeClass
    public static void setupClass() {
        ShadowLog.stream = System.out;

        JSONObject json = new JSONObject();

        try {
            json.put("id", testMessageId);
            json.put("content_id", testContentId);
            json.put("max_display_time", 30);

            JSONArray orTriggers = new JSONArray();
            JSONArray andTriggers = new JSONArray();

            JSONObject triggerJson = new JSONObject();
            triggerJson.put("property", "os_session_duration");
            triggerJson.put("operator", ">=");
            triggerJson.put("value", 3);

            andTriggers.put(triggerJson);
            orTriggers.put(andTriggers);
            json.put("triggers", orTriggers);

            message = new OSInAppMessage(json);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testBuiltMessage() {
        assertEquals(message.messageId, testMessageId);
        assertEquals(message.contentId, testContentId);
        assertEquals(message.maxDisplayTime, 30.0);
    }

    @Test
    public void testBuiltMessageTrigger() {
        OSTrigger trigger = message.triggers.get(0).get(0);

        assertEquals(trigger.operatorType, OSTriggerOperatorType.GREATER_THAN_OR_EQUAL_TO);
        assertEquals(trigger.property, "os_session_duration");
        assertEquals(trigger.value, 3);

    }

}
