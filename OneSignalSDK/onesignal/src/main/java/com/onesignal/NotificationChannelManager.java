/**
 * Modified MIT License
 *
 * Copyright 2017 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class NotificationChannelManager {
   
   // Can't create a channel with the id 'miscellaneous' as an exception is thrown.
   // Using it results in the notification not being displayed.
   // private static final String DEFAULT_CHANNEL_ID = "miscellaneous"; // NotificationChannel.DEFAULT_CHANNEL_ID;

   private static final String DEFAULT_CHANNEL_ID = "fcm_fallback_notification_channel";
   private static final String RESTORE_CHANNEL_ID = "restored_OS_notifications";
   
   static String createNotificationChannel(NotificationGenerationJob notifJob) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
         return DEFAULT_CHANNEL_ID;

      Context context = notifJob.context;
      JSONObject jsonPayload = notifJob.jsonPayload;

      NotificationManager notificationManager =
            (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);

      if (notifJob.restoring)
         return createRestoreChannel(notificationManager);
      
      // Allow channels created outside the SDK
      if (jsonPayload.has("oth_chnl")) {
         String otherChannel = jsonPayload.optString("oth_chnl");
         if (notificationManager.getNotificationChannel(otherChannel) != null)
            return otherChannel;
      }
      
      if (!jsonPayload.has("chnl"))
         return createDefaultChannel(notificationManager);
      
      try {
         return createChannel(context, notificationManager, jsonPayload);
      } catch (JSONException e) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Could not create notification channel due to JSON payload error!", e);
      }
      
      return DEFAULT_CHANNEL_ID;
   }

   // Creates NotificationChannel and NotificationChannelGroup based on a json payload.
   // Returns channel id after it is created.
   // Language dependent fields will be passed localized
   @RequiresApi(api = Build.VERSION_CODES.O)
   private static String createChannel(Context context, NotificationManager notificationManager, JSONObject payload) throws JSONException {
      // 'chnl' will be a string if coming from FCM and it will be a JSONObject when coming from
      //   a cold start sync.
      Object objChannelPayload = payload.opt("chnl");
      JSONObject channelPayload = null;
      if (objChannelPayload instanceof String)
         channelPayload = new JSONObject((String)objChannelPayload);
      else
         channelPayload = (JSONObject)objChannelPayload;
      
      String channel_id = channelPayload.optString("id", DEFAULT_CHANNEL_ID);
      // Ensure we don't try to use the system reserved id
      if (channel_id.equals(NotificationChannel.DEFAULT_CHANNEL_ID))
         channel_id = DEFAULT_CHANNEL_ID;
      
      JSONObject payloadWithText = channelPayload;
      if (channelPayload.has("langs")) {
         JSONObject langList = channelPayload.getJSONObject("langs");
         String deviceLanguage = OSUtils.getCorrectedLanguage();
         if (langList.has(deviceLanguage))
            payloadWithText = langList.optJSONObject(deviceLanguage);
      }
      
      String channel_name = payloadWithText.optString("nm", "Miscellaneous");
   
      int importance = priorityToImportance(payload.optInt("pri", 6));
      NotificationChannel channel = new NotificationChannel(channel_id, channel_name, importance);
      channel.setDescription(payloadWithText.optString("dscr", null));

      if (channelPayload.has("grp_id")) {
         String group_id = channelPayload.optString("grp_id");
         CharSequence group_name = payloadWithText.optString("grp_nm");
         notificationManager.createNotificationChannelGroup(new NotificationChannelGroup(group_id, group_name));
         channel.setGroup(group_id);
      }

      if (payload.has("ledc")) {
         BigInteger ledColor = new BigInteger(payload.optString("ledc"), 16);
         channel.setLightColor(ledColor.intValue());
      }
      channel.enableLights(payload.optInt("led", 1) == 1);

      if (payload.has("vib_pt")) {
         long[] vibrationPattern = OSUtils.parseVibrationPattern(payload);
         if (vibrationPattern != null)
            channel.setVibrationPattern(vibrationPattern);
      }
      channel.enableVibration(payload.optInt("vib", 1) == 1);

      if (payload.has("sound")) {
         // Sound will only play if Importance is set to High or Urgent
         String sound = payload.optString("sound", null);
         Uri uri = OSUtils.getSoundUri(context, sound);
         if (uri != null)
            channel.setSound(uri, null);
         else if ("null".equals(sound) || "nil".equals(sound))
            channel.setSound(null, null);
         // null = None for a sound.
      }
      // Setting sound to null makes it 'None' in the Settings.
      // Otherwise not calling setSound makes it the default notification sound.

      channel.setLockscreenVisibility(payload.optInt("vis", Notification.VISIBILITY_PRIVATE));
      channel.setShowBadge(payload.optInt("bdg", 1) == 1);
      channel.setBypassDnd(payload.optInt("bdnd", 0) == 1);

      notificationManager.createNotificationChannel(channel);
      return channel_id;
   }
   
   @RequiresApi(api = Build.VERSION_CODES.O)
   private static String createDefaultChannel(NotificationManager notificationManager) {
      NotificationChannel channel = new NotificationChannel(DEFAULT_CHANNEL_ID,
          "Miscellaneous",
          NotificationManager.IMPORTANCE_DEFAULT);
      
      channel.enableLights(true);
      channel.enableVibration(true);
      notificationManager.createNotificationChannel(channel);
      
      return DEFAULT_CHANNEL_ID;
   }

   @RequiresApi(api = Build.VERSION_CODES.O)
   private static String createRestoreChannel(NotificationManager notificationManager) {
      NotificationChannel channel = new NotificationChannel(RESTORE_CHANNEL_ID,
            "Restored",
            NotificationManager.IMPORTANCE_LOW);

      notificationManager.createNotificationChannel(channel);

      return RESTORE_CHANNEL_ID;
   }
   
   static void processChannelList(Context context, JSONObject payload) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
         return;

      if (!payload.has("chnl_lst"))
         return;

      NotificationManager notificationManager =
         (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
      
      Set<String> sycnedChannelSet = new HashSet<>();
      JSONArray chnlList = payload.optJSONArray("chnl_lst");
      int jsonArraySize = chnlList.length();
      for (int i = 0; i < jsonArraySize; i++) {
         try {
            sycnedChannelSet.add(createChannel(context, notificationManager, chnlList.getJSONObject(i)));
         } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Could not create notification channel due to JSON payload error!", e);
         }
      }
      
      // Delete old channels - Payload will include all changes for the app. Any extra OS_ ones must
      //                         have been deleted from the dashboard and should be removed.
      List<NotificationChannel> existingChannels = notificationManager.getNotificationChannels();
      for(NotificationChannel existingChannel : existingChannels) {
         String id = existingChannel.getId();
         if (id.startsWith("OS_") && !sycnedChannelSet.contains(id))
            notificationManager.deleteNotificationChannel(id);
      }
   }
   
   private static int priorityToImportance(int priority) {
      if (priority > 9)
         return NotificationManagerCompat.IMPORTANCE_MAX;
      if (priority > 7)
         return NotificationManagerCompat.IMPORTANCE_HIGH;
      if (priority > 5)
         return NotificationManagerCompat.IMPORTANCE_DEFAULT;
      if (priority > 3)
         return NotificationManagerCompat.IMPORTANCE_LOW;
      if (priority > 1)
         return NotificationManagerCompat.IMPORTANCE_MIN;
      
      return NotificationManagerCompat.IMPORTANCE_NONE;
   }
}
