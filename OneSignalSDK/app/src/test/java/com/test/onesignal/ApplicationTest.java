package com.test.onesignal;

import android.app.Activity;

import com.onesignal.BuildConfig;
import com.onesignal.OneSignal;
import com.onesignal.ShadowOneSignalRestClient;
import com.onesignal.ShadowPushRegistratorGPS;
import com.onesignal.example.BlankActivity;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.lang.reflect.Field;


@Config(packageName = "com.onesignal.example",
        constants = BuildConfig.class,
        shadows = {ShadowOneSignalRestClient.class, ShadowPushRegistratorGPS.class},
        sdk = 21)
@RunWith(CustomRobolectricTestRunner.class)
public class ApplicationTest {

    private static Field OneSignal_CurrentSubscription;
    private Activity blankActiviy;
    private static String callBackUseId, getCallBackRegId;

    public static void GetIdsAvailable() {
        OneSignal.idsAvailable(new OneSignal.IdsAvailableHandler() {
            @Override
            public void idsAvailable(String userId, String registrationId) {
                callBackUseId = userId;
                getCallBackRegId = registrationId;
            }
        });
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        ShadowLog.stream = System.out;

        OneSignal_CurrentSubscription = OneSignal.class.getDeclaredField("currentSubscription");
        OneSignal_CurrentSubscription.setAccessible(true);

        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        StaticResetHelper.saveStaticValues();
    }

    @Before // Before each test
    public void beforeEachTest() throws Exception {
        callBackUseId = getCallBackRegId = null;
        StaticResetHelper.restSetStaticFields();
        blankActiviy = Robolectric.buildActivity(BlankActivity.class).create().get();

        ShadowOneSignalRestClient.failNext = false;
        ShadowOneSignalRestClient.testThread = Thread.currentThread();
        GetIdsAvailable();
    }

    @Test
    public void testInvalidGoogleProjectNumber() throws Exception {
        OneSignal.init(blankActiviy, "NOT A VALID Google project number", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");

        try {Thread.sleep(5000);} catch (Throwable t) {}
        Assert.assertEquals(-6, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

        // Test that idsAvailable still fires
        Robolectric.getForegroundThreadScheduler().runOneTask();
        Assert.assertEquals(ShadowOneSignalRestClient.testUserId, callBackUseId);
    }

    @Test
    public void testUnsubcribeShouldMakeRegIdNullToIdsAvailable() throws Exception {
        OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
        try {Thread.sleep(5000);} catch (Throwable t) {}
        Assert.assertEquals(ShadowPushRegistratorGPS.regId, ShadowOneSignalRestClient.lastPost.getString("identifier"));

        Robolectric.getForegroundThreadScheduler().runOneTask();
        Assert.assertEquals(ShadowPushRegistratorGPS.regId, getCallBackRegId);

        OneSignal.setSubscription(false);
        GetIdsAvailable();
        Assert.assertNull(getCallBackRegId);
    }

    @Test
    public void testSetSubscriptionShouldNotOverrideSubscribeError() throws Exception {
        OneSignal.init(blankActiviy, "NOT A VALID Google project number", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
        try {Thread.sleep(5000);} catch (Throwable t) {}

        // Should not try to update server
        ShadowOneSignalRestClient.lastPost = null;
        OneSignal.setSubscription(true);
        Assert.assertNull(ShadowOneSignalRestClient.lastPost);

        // Restart app - Should omit notification_types
        StaticResetHelper.restSetStaticFields();
        OneSignal.init(blankActiviy, "NOT A VALID Google project number", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
        try {Thread.sleep(5000);} catch (Throwable t) {}
        Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
    }

    @Test
    public void shouldNotResetSubscriptionOnSession() throws Exception {
        OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
        OneSignal.setSubscription(false);

        try {Thread.sleep(5000);} catch (Throwable t) {}
        Assert.assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));
        StaticResetHelper.restSetStaticFields();

        OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
        try {Thread.sleep(5000);} catch (Throwable t) {}
        Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
    }

    @Test
    public void shouldSetSubscriptionCorrectlyEvenAfterFirstInitFail() throws Exception {
        // Failed to register with OneSignal but SetSubscription was called with false
        ShadowOneSignalRestClient.failNext = true;
        OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
        OneSignal.setSubscription(false);
        try {Thread.sleep(5000);} catch (Throwable t) {}
        ShadowOneSignalRestClient.failNext = false;


        // Restart app - Should send unsubscribe with create player call.
        StaticResetHelper.restSetStaticFields();
        OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
        try {Thread.sleep(5000);} catch (Throwable t) {}
        Assert.assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

        // Restart app again - Value synced last time so don't send again.
        StaticResetHelper.restSetStaticFields();
        OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
        try {Thread.sleep(5000);} catch (Throwable t) {}
        Assert.assertFalse(ShadowOneSignalRestClient.lastPost.has("notification_types"));
    }

    @Test
    public void shouldAllowMultipleSetSubscription() throws Exception {
        OneSignal.init(blankActiviy, "123456789", "b2f7f966-d8cc-11e4-bed1-df8f05be55ba");
        try {Thread.sleep(5000);} catch (Throwable t) {}

        OneSignal.setSubscription(false);
        Assert.assertEquals(-2, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

        // Should not resend same value
        ShadowOneSignalRestClient.lastPost = null;
        OneSignal.setSubscription(false);
        Assert.assertNull(ShadowOneSignalRestClient.lastPost);



        OneSignal.setSubscription(true);
        Assert.assertEquals(1, ShadowOneSignalRestClient.lastPost.getInt("notification_types"));

        // Should not resend same value
        ShadowOneSignalRestClient.lastPost = null;
        OneSignal.setSubscription(true);
        Assert.assertNull(ShadowOneSignalRestClient.lastPost);
    }
}