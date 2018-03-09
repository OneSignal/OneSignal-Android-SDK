package com.test.onesignal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;

import com.onesignal.ShadowOSUtils;
import com.onesignal.ShadowRoboNotificationManager;
import com.onesignal.example.BlankActivity;

import org.json.JSONArray;
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

import static com.onesignal.OneSignalPackagePrivateHelper.NotificationChannelManager_createNotificationChannel;
import static com.onesignal.OneSignalPackagePrivateHelper.NotificationChannelManager_processChannelList;
import static org.junit.Assert.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Config(packageName = "com.onesignal.example",
      shadows = {
         ShadowOSUtils.class,
         ShadowRoboNotificationManager.class},
      instrumentedPackages = {"com.onesignal"},
      sdk = 26)
@RunWith(RobolectricTestRunner.class)
public class NotificationChannelManagerRunner {

   private Context mContext;
   private BlankActivity blankActivity;
   
   NotificationChannelManagerRunner setContext(Context context) {
      mContext = context;
      return this;
   }
   
   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;
      TestHelpers.beforeTestSuite();
   }

   @Before
   public void beforeEachTest() throws Exception {
      ActivityController<BlankActivity> blankActivityController = Robolectric.buildActivity(BlankActivity.class).create();
      blankActivity = blankActivityController.get();
      mContext = blankActivity;
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
      payload.put("chnl", chnl.toString());

      String ret = NotificationChannelManager_createNotificationChannel(blankActivity, payload);

      NotificationChannel channel = ShadowRoboNotificationManager.lastChannel;
      assertEquals("test_id", ret);
      assertEquals("test_id", ShadowRoboNotificationManager.lastChannel.getId());
      assertNotNull(channel.getSound());
      assertTrue(channel.shouldShowLights());
      assertTrue(channel.shouldVibrate());
   }

   @Test
   public void createNotificationChannelWithALlOptions() throws Exception {
      JSONObject payload = new JSONObject();
      JSONObject chnl = new JSONObject();

      chnl.put("id", "test_id");
      chnl.put("nm", "Test Name");
      chnl.put("dscr", "Some description");
      chnl.put("grp_id", "grp_id");
      chnl.put("grp_nm", "Group Name");
   
      payload.put("pri", 10);
      payload.put("led", 0);
      payload.put("ledc", "FFFF0000");
      payload.put("vib", 0);
      payload.put("vib_pt", new JSONArray("[1,2,3,4]"));
      payload.put("sound", "notification");
      payload.put("vis", Notification.VISIBILITY_SECRET);
      payload.put("bdg", 1);
      payload.put("bdnd", 1);

      payload.put("chnl", chnl.toString());

      String ret = NotificationChannelManager_createNotificationChannel(blankActivity, payload);

      NotificationChannel channel = ShadowRoboNotificationManager.lastChannel;
      assertEquals("test_id", ret);
      assertEquals("test_id", ShadowRoboNotificationManager.lastChannel.getId());
      assertEquals("Test Name", channel.getName());
      assertEquals("Some description", channel.getDescription());
      assertEquals("grp_id", channel.getGroup());
      NotificationChannelGroup group = ShadowRoboNotificationManager.lastChannelGroup;
      assertEquals("grp_id", group.getId());
      assertEquals("Group Name", group.getName());
      assertNotNull(channel.getSound());
      assertFalse(channel.shouldShowLights()); // Setting a led color should NOT override enableLights
      assertEquals(-65536, channel.getLightColor());
      assertFalse(channel.shouldVibrate()); // Setting a pattern should NOT override enableVibration
      assertArrayEquals(new long[]{1,2,3,4}, channel.getVibrationPattern());
      assertEquals(NotificationManager.IMPORTANCE_MAX, channel.getImportance());
      assertEquals("content://settings/system/notification_sound", channel.getSound().toString());
      assertEquals(Notification.VISIBILITY_SECRET, channel.getLockscreenVisibility());
      assertTrue(channel.canShowBadge());
      assertTrue(channel.canBypassDnd());
   }
   
   @Test
   public void useOtherChannelWhenItIsAvailable() throws Exception {
      JSONObject payload = new JSONObject();
      payload.put("oth_chnl", "existing_id");
      
      JSONObject chnl = new JSONObject();
      chnl.put("id", "test_id");
      payload.put("chnl", chnl.toString());
      
      String ret = NotificationChannelManager_createNotificationChannel(blankActivity, payload);
      
      // Should create and use the payload type as the "existing_id" didn't exist.
      assertEquals("test_id", ret);
    
      // Create the missing channel and using the same payload we should use this existing_id now.
      createChannel("existing_id");
      ret = NotificationChannelManager_createNotificationChannel(blankActivity, payload);
      assertEquals("existing_id", ret);
   }
   
   
   // Start - Cold start sync tests
   
   @Test
   public void processPayloadWithOutChannelList() throws Exception {
      createChannel("local_existing_id");
      createChannel("OS_existing_id");
   
      NotificationChannelManager_processChannelList(blankActivity, new JSONObject());
      
      assertNotNull(getChannel("local_existing_id"));
      assertNotNull(getChannel("OS_existing_id"));
   }
   
   @Test
   public void processPayloadCreatingNewChannel() throws Exception {
      createChannel("local_existing_id");
      
      JSONArray channelList = new JSONArray();
      JSONObject channelItem = new JSONObject();
      JSONObject channelItemChnl = new JSONObject();
   
      channelItemChnl.put("id", "OS_id1");
      channelItem.put("chnl", channelItemChnl);
   
      channelList.put(channelItem);
      JSONObject payload = new JSONObject();
      payload.put("chnl_lst", channelList);
      
      NotificationChannelManager_processChannelList(blankActivity, payload);
      
      assertNotNull(getChannel("local_existing_id"));
      assertNotNull(getChannel("OS_id1"));
   }
   
   @Test
   public void processPayloadDeletingOldChannel() throws Exception {
      NotificationChannelManager_processChannelList(blankActivity, createBasicChannelListPayload());
      assertChannelsForBasicChannelList();
   }
   
   // Test that specific "en" defined keys name and descriptions are used when
   //    the device language is English.
   // Top level keys under no language key are considered the default language.
   @Test
   public void processChannelListWithMultiLanguage() throws Exception {
      JSONObject payload = createBasicChannelListPayload();
   
      JSONObject channelItem = (JSONObject)payload.optJSONArray("chnl_lst").get(0);
      JSONObject channelProperties = channelItem.optJSONObject("chnl");
      
      // Add "langs" key with a "en" sub key.
      JSONObject langs = new JSONObject();
      JSONObject en = new JSONObject();
      en.put("nm", "en_nm");
      en.put("dscr", "en_dscr");
      en.put("grp_nm", "en_grp_nm");
      langs.put("en", en);
      channelProperties.put("langs", langs);
      
      channelProperties.put("grp_id", "grp_id1");
   
      NotificationChannelManager_processChannelList(blankActivity, payload);
      
      NotificationChannel channel = getChannel("OS_id1");
      assertEquals("en_nm", channel.getName());
      assertEquals("en_dscr", channel.getDescription());
      assertEquals("en_grp_nm", ShadowRoboNotificationManager.lastChannelGroup.getName());
   }
   
   // Starting helper methods
   
   JSONObject createBasicChannelListPayload() throws JSONException {
      createChannel("local_existing_id");
      createChannel("OS_existing_id");
      
      JSONArray channelList = new JSONArray();
      JSONObject channelItem = new JSONObject();
      JSONObject channelItemChnl = new JSONObject();
   
      channelItemChnl.put("id", "OS_id1");
      channelItem.put("chnl", channelItemChnl);
   
      channelList.put(channelItem);
      JSONObject payload = new JSONObject();
      payload.put("chnl_lst", channelList);
      return payload;
   }
   
   void assertChannelsForBasicChannelList() {
      assertNotNull(getChannel("local_existing_id"));
      assertNull(getChannel("OS_existing_id"));
      assertNotNull(getChannel("OS_id1"));
   }
   
   private NotificationChannel getChannel(String id) {
      NotificationManager notificationManager =
          (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
      return notificationManager.getNotificationChannel(id);
   }
   
   private void createChannel(String id) {
      NotificationManager notificationManager =
          (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
      NotificationChannel channel = new NotificationChannel(id,"name", NotificationManager.IMPORTANCE_DEFAULT);
      notificationManager.createNotificationChannel(channel);
   }
}