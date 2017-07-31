package com.test.onesignal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;

import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowRoboNotificationManager;
import com.onesignal.example.BlankActivity;

import org.json.JSONArray;
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

import static com.onesignal.OneSignalPackagePrivateHelper.NotificationChannelManager_createNotificationChannel;
import static org.junit.Assert.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Config(packageName = "com.onesignal.example",
      shadows = {
         ShadowOSUtils.class,
         ShadowRoboNotificationManager.class},
      instrumentedPackages = {"com.onesignal"},
      sdk = 10000)
@RunWith(RobolectricTestRunner.class)
public class NotificationChannelManagerRunner {

   private BlankActivity blankActivity;

   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;
   }

   @Before
   public void beforeEachTest() throws Exception {
      ActivityController<BlankActivity> blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
      blankActivity = blankActivityController.get();
   }

   @Test
   public void createNotificationChannelShouldReturnDefaultChannelWithEmptyPayload() throws Exception {
      JSONObject payload = new JSONObject();

      String ret = NotificationChannelManager_createNotificationChannel(blankActivity, payload);

      assertEquals("fcm_fallback_notification_channel", ret);
      NotificationChannel lastChannel = ShadowRoboNotificationManager.lastChannel;
      assertEquals("fcm_fallback_notification_channel", lastChannel.getId());
      assertNotNull(lastChannel.getSound());
      assertTrue(lastChannel.shouldShowLights());
      assertTrue(lastChannel.shouldVibrate());
   }

   @Test
   public void createNotificationChannelCreateBasicChannel() throws Exception {
      JSONObject payload = new JSONObject();
      JSONObject chnl = new JSONObject();
      chnl.put("id", "test_id");
      payload.put("chnl", chnl);

      String ret = NotificationChannelManager_createNotificationChannel(blankActivity, payload);

      NotificationChannel channel = ShadowRoboNotificationManager.lastChannel;
      assertEquals("test_id", ret);
      assertEquals("test_id", ShadowRoboNotificationManager.lastChannel.getId());
      assertNotNull(channel.getSound());
      assertTrue(channel.shouldShowLights());
      assertTrue(channel.shouldVibrate());
   }

   @Test
   public void createNotificationChannelWithALlOptionsl() throws Exception {
      JSONObject payload = new JSONObject();
      JSONObject chnl = new JSONObject();

      chnl.put("id", "test_id");
      chnl.put("nm", "Test Name");
      chnl.put("grp", "grp_id");
      chnl.put("grp_nm", "Group Name");
      chnl.put("imp", NotificationManager.IMPORTANCE_MAX);
      chnl.put("lght", false);
      chnl.put("ledc", "FFFF0000");
      chnl.put("vib", false);
      chnl.put("vib_pt", new JSONArray("[1,2,3,4]"));
      chnl.put("snd_nm", "notification");
      chnl.put("lck", Notification.VISIBILITY_SECRET);
      chnl.put("bdg", true);
      chnl.put("bdnd", true);

      payload.put("chnl", chnl);

      String ret = NotificationChannelManager_createNotificationChannel(blankActivity, payload);

      NotificationChannel channel = ShadowRoboNotificationManager.lastChannel;
      assertEquals("test_id", ret);
      assertEquals("test_id", ShadowRoboNotificationManager.lastChannel.getId());
      assertEquals("Test Name", channel.getName());
      assertEquals("grp_id", channel.getGroup());
      NotificationChannelGroup group = ShadowRoboNotificationManager.lastChannelGroup;
      assertEquals("grp_id", group.getId());
      assertEquals("Group Name", group.getName());
      assertNotNull(channel.getSound());
      assertFalse(channel.shouldShowLights());
      assertEquals(-65536, channel.getLightColor());
      assertTrue(channel.shouldVibrate()); // Setting a pattern enables vibration
      assertArrayEquals(new long[]{1,2,3,4}, channel.getVibrationPattern());
      assertEquals(NotificationManager.IMPORTANCE_MAX, channel.getImportance());
      assertEquals("content://settings/system/notification_sound", channel.getSound().toString());
      assertEquals(Notification.VISIBILITY_SECRET, channel.getLockscreenVisibility());
      assertTrue(channel.canShowBadge());
      assertTrue(channel.canBypassDnd());
   }
}
