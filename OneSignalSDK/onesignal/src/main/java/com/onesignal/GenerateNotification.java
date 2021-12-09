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

import android.R.drawable;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.widget.RemoteViews;

import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.onesignal.OneSignalDbContract.NotificationTable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import static com.onesignal.OSUtils.getResourceString;

class GenerateNotification {

   public static final String OS_SHOW_NOTIFICATION_THREAD = "OS_SHOW_NOTIFICATION_THREAD";

   public static final String BUNDLE_KEY_ANDROID_NOTIFICATION_ID = "androidNotificationId";
   public static final String BUNDLE_KEY_ACTION_ID = "actionId";
   // Bundle key the whole OneSignal payload will be placed into as JSON and attached to the
   //   notification Intent.
   public static final String BUNDLE_KEY_ONESIGNAL_DATA = "onesignalData";

   private static Class<?> notificationOpenedClass = NotificationOpenedReceiver.class;
   private static Class<?> notificationDismissedClass = NotificationDismissReceiver.class;
   private static Resources contextResources = null;
   private static Context currentContext = null;
   private static String packageName = null;

   private static class OneSignalNotificationBuilder {
      NotificationCompat.Builder compatBuilder;
      boolean hasLargeIcon;
   }

   private static void setStatics(Context inContext) {
      currentContext = inContext;
      packageName = currentContext.getPackageName();
      contextResources = currentContext.getResources();
   }

   @WorkerThread
   static boolean displayNotification(OSNotificationGenerationJob notificationJob) {
      setStatics(notificationJob.getContext());

      isRunningOnMainThreadCheck();

      return showNotification(notificationJob);
   }

   static boolean displayIAMPreviewNotification(OSNotificationGenerationJob notificationJob) {
      setStatics(notificationJob.getContext());

      return showNotification(notificationJob);
   }

   /**
    * For shadow test purpose
    */
   static void isRunningOnMainThreadCheck() {
      // Runtime check against showing the notification from the main thread
      if (OSUtils.isRunningOnMainThread())
         throw new OSThrowable.OSMainThreadException("Process for showing a notification should never been done on Main Thread!");
   }
   
   private static CharSequence getTitle(JSONObject fcmJson) {
      CharSequence title = fcmJson.optString("title", null);

      if (title != null)
         return title;

      return currentContext.getPackageManager().getApplicationLabel(currentContext.getApplicationInfo());
   }

   private static PendingIntent getNewDismissActionPendingIntent(int requestCode, Intent intent) {
      return PendingIntent.getBroadcast(currentContext, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
   }

   private static Intent getNewBaseDismissIntent(int notificationId) {
      return new Intent(currentContext, notificationDismissedClass)
              .putExtra(BUNDLE_KEY_ANDROID_NOTIFICATION_ID, notificationId)
              .putExtra("dismissed", true);
   }
   
   private static OneSignalNotificationBuilder getBaseOneSignalNotificationBuilder(OSNotificationGenerationJob notificationJob) {
      JSONObject fcmJson = notificationJob.getJsonPayload();
      OneSignalNotificationBuilder oneSignalNotificationBuilder = new OneSignalNotificationBuilder();
      
      NotificationCompat.Builder notificationBuilder;
      try {
         String channelId = NotificationChannelManager.createNotificationChannel(notificationJob);
         // Will throw if app is using 26.0.0-beta1 or older of the support library.
         notificationBuilder = new NotificationCompat.Builder(currentContext, channelId);
      } catch(Throwable t) {
         notificationBuilder = new NotificationCompat.Builder(currentContext);
      }
      
      String message = fcmJson.optString("alert", null);

      notificationBuilder
         .setAutoCancel(true)
         .setSmallIcon(getSmallIconId(fcmJson))
         .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
         .setContentText(message)
         .setTicker(message);

      // If title is blank; Set to app name if less than Android 7.
      //    Android 7.0 always displays the app title now in it's own section
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
          !fcmJson.optString("title").equals(""))
         notificationBuilder.setContentTitle(getTitle(fcmJson));
   
      try {
         BigInteger accentColor = getAccentColor(fcmJson);
         if (accentColor != null)
            notificationBuilder.setColor(accentColor.intValue());
      } catch (Throwable t) {} // Can throw if an old android support lib is used.

      try {
         int lockScreenVisibility = NotificationCompat.VISIBILITY_PUBLIC;
         if (fcmJson.has("vis"))
            lockScreenVisibility = Integer.parseInt(fcmJson.optString("vis"));
         notificationBuilder.setVisibility(lockScreenVisibility);
      } catch (Throwable t) {} // Can throw if an old android support lib is used or parse error

      Bitmap largeIcon = getLargeIcon(fcmJson);
      if (largeIcon != null) {
         oneSignalNotificationBuilder.hasLargeIcon = true;
         notificationBuilder.setLargeIcon(largeIcon);
      }

      Bitmap bigPictureIcon = getBitmap(fcmJson.optString("bicon", null));
      if (bigPictureIcon != null)
         notificationBuilder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bigPictureIcon).setSummaryText(message));

      if (notificationJob.getShownTimeStamp() != null) {
         try {
            notificationBuilder.setWhen(notificationJob.getShownTimeStamp() * 1_000L);
         } catch (Throwable t) {} // Can throw if an old android support lib is used.
      }

      setAlertnessOptions(fcmJson, notificationBuilder);
      
      oneSignalNotificationBuilder.compatBuilder = notificationBuilder;
      return oneSignalNotificationBuilder;
   }

   // Sets visibility options including; Priority, LED, Sounds, and Vibration.
   private static void setAlertnessOptions(JSONObject fcmJson, NotificationCompat.Builder notifBuilder) {
      int payloadPriority = fcmJson.optInt("pri", 6);
      int androidPriority = convertOSToAndroidPriority(payloadPriority);
      notifBuilder.setPriority(androidPriority);
      boolean lowDisplayPriority = androidPriority < NotificationCompat.PRIORITY_DEFAULT;

      // If notification is a low priority don't set Sound, Vibration, or LED
      if (lowDisplayPriority)
         return;

      int notificationDefaults = 0;

      if (fcmJson.has("ledc") && fcmJson.optInt("led", 1) == 1) {
         try {
            BigInteger ledColor = new BigInteger(fcmJson.optString("ledc"), 16);
            notifBuilder.setLights(ledColor.intValue(), 2000, 5000);
         } catch (Throwable t) {
            notificationDefaults |= Notification.DEFAULT_LIGHTS;
         } // Can throw if an old android support lib is used or parse error.
      } else {
         notificationDefaults |= Notification.DEFAULT_LIGHTS;
      }

      if (fcmJson.optInt("vib", 1) == 1) {
         if (fcmJson.has("vib_pt")) {
            long[] vibrationPattern = OSUtils.parseVibrationPattern(fcmJson);
            if (vibrationPattern != null)
               notifBuilder.setVibrate(vibrationPattern);
         }
         else
            notificationDefaults |= Notification.DEFAULT_VIBRATE;
      }

      if (isSoundEnabled(fcmJson)) {
         Uri soundUri = OSUtils.getSoundUri(currentContext, fcmJson.optString("sound", null));
         if (soundUri != null)
            notifBuilder.setSound(soundUri);
         else
            notificationDefaults |= Notification.DEFAULT_SOUND;
      }

      notifBuilder.setDefaults(notificationDefaults);
   }

   private static void removeNotifyOptions(NotificationCompat.Builder builder) {
      builder.setOnlyAlertOnce(true)
             .setDefaults(0)
             .setSound(null)
             .setVibrate(null)
             .setTicker(null);
   }

   // Put the message into a notification and post it.
   private static boolean showNotification(OSNotificationGenerationJob notificationJob) {
      int notificationId = notificationJob.getAndroidId();
      JSONObject fcmJson = notificationJob.getJsonPayload();
      String group = fcmJson.optString("grp", null);

      GenerateNotificationOpenIntent intentGenerator = GenerateNotificationOpenIntentFromPushPayload.INSTANCE.create(
          currentContext,
          fcmJson
      );

      ArrayList<StatusBarNotification> grouplessNotifs = new ArrayList<>();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
         /* Android 7.0 auto groups 4 or more notifications so we find these groupless active
          * notifications and add a generic group to them */
         grouplessNotifs = OneSignalNotificationManager.getActiveGrouplessNotifications(currentContext);
         // If the null this makes the 4th notification and we want to check that 3 or more active groupless exist
         if (group == null && grouplessNotifs.size() >= 3) {
            group = OneSignalNotificationManager.getGrouplessSummaryKey();
            OneSignalNotificationManager.assignGrouplessNotifications(currentContext, grouplessNotifs);
         }
      }

      OneSignalNotificationBuilder oneSignalNotificationBuilder = getBaseOneSignalNotificationBuilder(notificationJob);
      NotificationCompat.Builder notifBuilder = oneSignalNotificationBuilder.compatBuilder;

      addNotificationActionButtons(
          fcmJson,
          intentGenerator,
          notifBuilder,
          notificationId,
          null
      );
      
      try {
         addBackgroundImage(fcmJson, notifBuilder);
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Could not set background notification image!", t);
      }

      applyNotificationExtender(notificationJob, notifBuilder);
      
      // Keeps notification from playing sound + vibrating again
      if (notificationJob.isRestoring())
         removeNotifyOptions(notifBuilder);

      int makeRoomFor = 1;
      if (group != null)
         makeRoomFor = 2;
      NotificationLimitManager.clearOldestOverLimit(currentContext, makeRoomFor);

      Notification notification;
      if (group != null) {
         createGenericPendingIntentsForGroup(
             notifBuilder,
             intentGenerator,
             fcmJson,
             group,
             notificationId
         );
         notification = createSingleNotificationBeforeSummaryBuilder(notificationJob, notifBuilder);

         // Create PendingIntents for notifications in a groupless or defined summary
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                 group.equals(OneSignalNotificationManager.getGrouplessSummaryKey())) {
             createGrouplessSummaryNotification(
                 notificationJob,
                 intentGenerator,
 grouplessNotifs.size() + 1
             );
         }
         else
            createSummaryNotification(notificationJob, oneSignalNotificationBuilder);
      } else {
         notification = createGenericPendingIntentsForNotif(
             notifBuilder,
             intentGenerator,
             fcmJson,
             notificationId
         );
      }
      // NotificationManagerCompat does not auto omit the individual notification on the device when using
      //   stacked notifications on Android 4.2 and older
      // The benefits of calling notify for individual notifications in-addition to the summary above it is shows
      //   each notification in a stack on Android Wear and each one is actionable just like the Gmail app does per email.
      //   Note that on Android 7.0 this is the opposite. Only individual notifications will show and mBundle / group is
      //     created by Android itself.
      if (group == null || Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) {
         addXiaomiSettings(oneSignalNotificationBuilder, notification);
         NotificationManagerCompat.from(currentContext).notify(notificationId, notification);
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
         return OneSignalNotificationManager.areNotificationsEnabled(currentContext, notification.getChannelId());
      return true;
   }

   private static Notification createGenericPendingIntentsForNotif(
       NotificationCompat.Builder notifBuilder,
       GenerateNotificationOpenIntent intentGenerator,
       JSONObject gcmBundle,
       int notificationId
   ) {
      Random random = new SecureRandom();
      PendingIntent contentIntent = intentGenerator.getNewActionPendingIntent(
          random.nextInt(),
          intentGenerator.getNewBaseIntent(notificationId).putExtra(BUNDLE_KEY_ONESIGNAL_DATA, gcmBundle.toString())
      );
      notifBuilder.setContentIntent(contentIntent);
      PendingIntent deleteIntent = getNewDismissActionPendingIntent(random.nextInt(), getNewBaseDismissIntent(notificationId));
      notifBuilder.setDeleteIntent(deleteIntent);
      return notifBuilder.build();
   }

   private static void createGenericPendingIntentsForGroup(
       NotificationCompat.Builder notifBuilder,
       GenerateNotificationOpenIntent intentGenerator,
       JSONObject gcmBundle,
       String group,
       int notificationId
   ) {
      Random random = new SecureRandom();
      PendingIntent contentIntent = intentGenerator.getNewActionPendingIntent(
          random.nextInt(),
          intentGenerator.getNewBaseIntent(notificationId).putExtra(BUNDLE_KEY_ONESIGNAL_DATA, gcmBundle.toString()).putExtra("grp", group)
      );
      notifBuilder.setContentIntent(contentIntent);
      PendingIntent deleteIntent = getNewDismissActionPendingIntent(random.nextInt(), getNewBaseDismissIntent(notificationId).putExtra("grp", group));
      notifBuilder.setDeleteIntent(deleteIntent);
      notifBuilder.setGroup(group);

      try {
         notifBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
      } catch (Throwable t) {
         //do nothing in this case...Android support lib 26 isn't in the project
      }
   }

   private static void applyNotificationExtender(
           OSNotificationGenerationJob notificationJob,
           NotificationCompat.Builder notificationBuilder) {
      if (!notificationJob.hasExtender())
         return;

      try {
         Field mNotificationField = NotificationCompat.Builder.class.getDeclaredField("mNotification");
         mNotificationField.setAccessible(true);
         Notification mNotification = (Notification)mNotificationField.get(notificationBuilder);

         notificationJob.setOrgFlags(mNotification.flags);
         notificationJob.setOrgSound(mNotification.sound);
         notificationBuilder.extend(notificationJob.getNotification().getNotificationExtender());

         mNotification = (Notification)mNotificationField.get(notificationBuilder);

         Field mContentTextField = NotificationCompat.Builder.class.getDeclaredField("mContentText");
         mContentTextField.setAccessible(true);
         CharSequence mContentText = (CharSequence)mContentTextField.get(notificationBuilder);

         Field mContentTitleField = NotificationCompat.Builder.class.getDeclaredField("mContentTitle");
         mContentTitleField.setAccessible(true);
         CharSequence mContentTitle = (CharSequence)mContentTitleField.get(notificationBuilder);

         notificationJob.setOverriddenBodyFromExtender(mContentText);
         notificationJob.setOverriddenTitleFromExtender(mContentTitle);
         if (!notificationJob.isRestoring()) {
            notificationJob.setOverriddenFlags(mNotification.flags);
            notificationJob.setOverriddenSound(mNotification.sound);
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }

   }
   
   // Removes custom sound set from the extender from non-summary notification before building it.
   //   This prevents the sound from playing twice or both the default sound + a custom one.
   private static Notification createSingleNotificationBeforeSummaryBuilder(OSNotificationGenerationJob notificationJob, NotificationCompat.Builder notifBuilder) {
      // Includes Android 4.3 through 6.0.1. Android 7.1 handles this correctly without this.
      // Android 4.2 and older just post the summary only.
      boolean singleNotifWorkArounds = Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1 && Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                                       && !notificationJob.isRestoring();
      
      if (singleNotifWorkArounds) {
         if (notificationJob.getOverriddenSound() != null && !notificationJob.getOverriddenSound().equals(notificationJob.getOrgSound()))
            notifBuilder.setSound(null);
      }

      Notification notification = notifBuilder.build();

      if (singleNotifWorkArounds)
         notifBuilder.setSound(notificationJob.getOverriddenSound());

      return notification;
   }

   // Xiaomi requires the following to show a custom notification icons.
   // Without this MIUI 8 will only show the app icon on the left.
   //  When a large icon is set the small icon will no longer show.
   private static void addXiaomiSettings(OneSignalNotificationBuilder oneSignalNotificationBuilder, Notification notification) {
      // Don't use unless a large icon is set.
      // The small white notification icon is hard to see with MIUI default light theme.
      if (!oneSignalNotificationBuilder.hasLargeIcon)
         return;

      try {
         Object miuiNotification = Class.forName("android.app.MiuiNotification").newInstance();
         Field customizedIconField = miuiNotification.getClass().getDeclaredField("customizedIcon");
         customizedIconField.setAccessible(true);
         customizedIconField.set(miuiNotification, true);

         Field extraNotificationField = notification.getClass().getField("extraNotification");
         extraNotificationField.setAccessible(true);
         extraNotificationField.set(notification, miuiNotification);
      } catch (Throwable t) {} // Ignore if not a Xiaomi device
   }

   static void updateSummaryNotification(OSNotificationGenerationJob notificationJob) {
      setStatics(notificationJob.getContext());
      createSummaryNotification(notificationJob, null);
   }

   // This summary notification will be visible instead of the normal one on pre-Android 7.0 devices.
   private static void createSummaryNotification(OSNotificationGenerationJob notificationJob, OneSignalNotificationBuilder notifBuilder) {
      boolean updateSummary = notificationJob.isRestoring();
      JSONObject fcmJson = notificationJob.getJsonPayload();
      GenerateNotificationOpenIntent intentGenerator = GenerateNotificationOpenIntentFromPushPayload.INSTANCE.create(
           currentContext,
           fcmJson
      );

      String group = fcmJson.optString("grp", null);

      SecureRandom random = new SecureRandom();
      PendingIntent summaryDeleteIntent = getNewDismissActionPendingIntent(random.nextInt(), getNewBaseDismissIntent(0).putExtra("summary", group));
      
      Notification summaryNotification;
      Integer summaryNotificationId = null;
   
      String firstFullData = null;
      Collection<SpannableString> summaryList = null;
      
      OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(currentContext);
      Cursor cursor = null;
      
      try {
         String[] retColumn = { NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID,
             NotificationTable.COLUMN_NAME_FULL_DATA,
             NotificationTable.COLUMN_NAME_IS_SUMMARY,
             NotificationTable.COLUMN_NAME_TITLE,
             NotificationTable.COLUMN_NAME_MESSAGE };
   
         String whereStr =  NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +   // Where String
             NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
             NotificationTable.COLUMN_NAME_OPENED + " = 0";
         String[] whereArgs = { group };
         
         // Make sure to omit any old existing matching android ids in-case we are replacing it.
         if (!updateSummary && notificationJob.getAndroidId() != -1)
            whereStr += " AND " + NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " <> " + notificationJob.getAndroidId();
         
         cursor = dbHelper.query(
             NotificationTable.TABLE_NAME,
             retColumn,
             whereStr,
             whereArgs,
             null,                              // group by
             null,                              // filter by row groups
             NotificationTable._ID + " DESC"    // sort order, new to old
         );
         
         if (cursor.moveToFirst()) {
            SpannableString spannableString;
            summaryList = new ArrayList<>();

            do {
               if (cursor.getInt(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_IS_SUMMARY)) == 1)
                  summaryNotificationId = cursor.getInt(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID));
               else {
                  String title = cursor.getString(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_TITLE));
                  if (title == null)
                     title = "";
                  else
                     title += " ";

                  String msg = cursor.getString(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_MESSAGE));

                  spannableString = new SpannableString(title + msg);
                  if (title.length() > 0)
                     spannableString.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, title.length(), 0);
                  summaryList.add(spannableString);

                  if (firstFullData == null)
                     firstFullData = cursor.getString(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_FULL_DATA));
               }
            } while (cursor.moveToNext());

            if (updateSummary && firstFullData != null) {
               try {
                  fcmJson = new JSONObject(firstFullData);
               } catch (JSONException e) {
                  e.printStackTrace();
               }
            }
         }
      }
      finally {
         if (cursor != null && !cursor.isClosed())
            cursor.close();
      }
      
      if (summaryNotificationId == null) {
         summaryNotificationId = random.nextInt();
         createSummaryIdDatabaseEntry(dbHelper, group, summaryNotificationId);
      }
      
      PendingIntent summaryContentIntent = intentGenerator.getNewActionPendingIntent(
          random.nextInt(),
          createBaseSummaryIntent(summaryNotificationId, intentGenerator, fcmJson, group)
      );
      
      // 2 or more notifications with a group received, group them together as a single notification.
      if (summaryList != null &&
          ((updateSummary && summaryList.size() > 1) ||
           (!updateSummary && summaryList.size() > 0))) {
         int notificationCount = summaryList.size() + (updateSummary ? 0 : 1);

         String summaryMessage = fcmJson.optString("grp_msg", null);
         if (summaryMessage == null)
            summaryMessage = notificationCount + " new messages";
         else
            summaryMessage = summaryMessage.replace("$[notif_count]", "" + notificationCount);

         NotificationCompat.Builder summaryBuilder = getBaseOneSignalNotificationBuilder(notificationJob).compatBuilder;
         if (updateSummary)
            removeNotifyOptions(summaryBuilder);
         else {
            if (notificationJob.getOverriddenSound() != null)
               summaryBuilder.setSound(notificationJob.getOverriddenSound());
   
            if (notificationJob.getOverriddenFlags() != null)
               summaryBuilder.setDefaults(notificationJob.getOverriddenFlags());
         }

         // The summary is designed to fit all notifications.
         //   Default small and large icons are used instead of the payload options to enforce this.
         summaryBuilder.setContentIntent(summaryContentIntent)
              .setDeleteIntent(summaryDeleteIntent)
              .setContentTitle(currentContext.getPackageManager().getApplicationLabel(currentContext.getApplicationInfo()))
              .setContentText(summaryMessage)
              .setNumber(notificationCount)
              .setSmallIcon(getDefaultSmallIconId())
              .setLargeIcon(getDefaultLargeIcon())
              .setOnlyAlertOnce(updateSummary)
              .setAutoCancel(false)
              .setGroup(group)
              .setGroupSummary(true);

         try {
            summaryBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
         }
         catch (Throwable t) {
            //do nothing in this case...Android support lib 26 isn't in the project
         }

         if (!updateSummary)
            summaryBuilder.setTicker(summaryMessage);

         NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

         // Add the latest notification to the summary
         if (!updateSummary) {
            String line1Title = null;
            
            if (notificationJob.getTitle() != null)
               line1Title = notificationJob.getTitle().toString();

            if (line1Title == null)
               line1Title = "";
            else
               line1Title += " ";
            
            String message = notificationJob.getBody().toString();
            
            SpannableString spannableString = new SpannableString(line1Title + message);
            if (line1Title.length() > 0)
               spannableString.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, line1Title.length(), 0);
            inboxStyle.addLine(spannableString);
         }

         for (SpannableString line : summaryList)
            inboxStyle.addLine(line);
         inboxStyle.setBigContentTitle(summaryMessage);
         summaryBuilder.setStyle(inboxStyle);

         summaryNotification = summaryBuilder.build();
      }
      else {
         // First notification with this group key, post like a normal notification.
         NotificationCompat.Builder summaryBuilder = notifBuilder.compatBuilder;

         // TODO: We are re-using the notifBuilder from the normal notification so if a developer as an
         //  extender setup all the settings will carry over.
         //  Note: However their buttons will not carry over as we need to be setup with this new summaryNotificationId.
         summaryBuilder.mActions.clear();
         addNotificationActionButtons(
                 fcmJson,
                 intentGenerator,
                 summaryBuilder,
                 summaryNotificationId,
                 group
         );

         summaryBuilder.setContentIntent(summaryContentIntent)
                       .setDeleteIntent(summaryDeleteIntent)
                       .setOnlyAlertOnce(updateSummary)
                       .setAutoCancel(false)
                       .setGroup(group)
                       .setGroupSummary(true);

         try {
            summaryBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
         }
         catch (Throwable t) {
            //do nothing in this case...Android support lib 26 isn't in the project
         }

         summaryNotification = summaryBuilder.build();
         addXiaomiSettings(notifBuilder, summaryNotification);
      }

      NotificationManagerCompat.from(currentContext).notify(summaryNotificationId, summaryNotification);
   }

   @RequiresApi(api = Build.VERSION_CODES.M)
   private static void createGrouplessSummaryNotification(
       OSNotificationGenerationJob notificationJob,
       GenerateNotificationOpenIntent intentGenerator,
       int grouplessNotifCount
   ) {
      JSONObject fcmJson = notificationJob.getJsonPayload();

      Notification summaryNotification;

      SecureRandom random = new SecureRandom();
      String group = OneSignalNotificationManager.getGrouplessSummaryKey();
      String summaryMessage = grouplessNotifCount + " new messages";
      int summaryNotificationId = OneSignalNotificationManager.getGrouplessSummaryId();

      PendingIntent summaryContentIntent = intentGenerator.getNewActionPendingIntent(
          random.nextInt(),
          createBaseSummaryIntent(summaryNotificationId,intentGenerator, fcmJson, group)
      );
      PendingIntent summaryDeleteIntent = getNewDismissActionPendingIntent(random.nextInt(), getNewBaseDismissIntent(0).putExtra("summary", group));

      NotificationCompat.Builder summaryBuilder = getBaseOneSignalNotificationBuilder(notificationJob).compatBuilder;
      if (notificationJob.getOverriddenSound() != null)
         summaryBuilder.setSound(notificationJob.getOverriddenSound());

      if (notificationJob.getOverriddenFlags() != null)
         summaryBuilder.setDefaults(notificationJob.getOverriddenFlags());

      // The summary is designed to fit all notifications.
      //   Default small and large icons are used instead of the payload options to enforce this.
      summaryBuilder.setContentIntent(summaryContentIntent)
            .setDeleteIntent(summaryDeleteIntent)
            .setContentTitle(currentContext.getPackageManager().getApplicationLabel(currentContext.getApplicationInfo()))
            .setContentText(summaryMessage)
            .setNumber(grouplessNotifCount)
            .setSmallIcon(getDefaultSmallIconId())
            .setLargeIcon(getDefaultLargeIcon())
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setGroup(group)
            .setGroupSummary(true);

      try {
        summaryBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
      }
      catch (Throwable t) {
        // Do nothing in this case... Android support lib 26 isn't in the project
      }

      NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

      inboxStyle.setBigContentTitle(summaryMessage);
      summaryBuilder.setStyle(inboxStyle);
      summaryNotification = summaryBuilder.build();

      NotificationManagerCompat.from(currentContext).notify(summaryNotificationId, summaryNotification);
   }
   
   private static Intent createBaseSummaryIntent(
       int summaryNotificationId,
       GenerateNotificationOpenIntent intentGenerator,
       JSONObject fcmJson,
       String group
   ) {
     return intentGenerator.getNewBaseIntent(summaryNotificationId).putExtra(BUNDLE_KEY_ONESIGNAL_DATA, fcmJson.toString()).putExtra("summary", group);
   }
   
   private static void createSummaryIdDatabaseEntry(OneSignalDbHelper dbHelper, String group, int id) {
      // There currently isn't a visible notification from for this group_id.
      // Save the group summary notification id so it can be updated later.
      ContentValues values = new ContentValues();
      values.put(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID, id);
      values.put(NotificationTable.COLUMN_NAME_GROUP_ID, group);
      values.put(NotificationTable.COLUMN_NAME_IS_SUMMARY, 1);
      dbHelper.insertOrThrow(NotificationTable.TABLE_NAME, null, values);
   }

   // Keep 'throws Throwable' as 'onesignal_bgimage_notif_layout' may not be available
   //    This maybe the case if a jar is used instead of an aar.
   private static void addBackgroundImage(JSONObject fcmJson, NotificationCompat.Builder notifBuilder) throws Throwable {
      // Not adding Background Images to API Versions < 16 or >= 31
      if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN ||
          android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
         OneSignal.Log(OneSignal.LOG_LEVEL.VERBOSE,
              "Cannot use background images in notifications for device on version: " + android.os.Build.VERSION.SDK_INT);
         return;
      }

      Bitmap bg_image = null;
      JSONObject jsonBgImage = null;
      String jsonStrBgImage = fcmJson.optString("bg_img", null);

      if (jsonStrBgImage != null) {
         jsonBgImage = new JSONObject(jsonStrBgImage);
         bg_image = getBitmap(jsonBgImage.optString("img", null));
      }

      if (bg_image == null)
         bg_image = getBitmapFromAssetsOrResourceName("onesignal_bgimage_default_image");

      if (bg_image != null) {
         RemoteViews customView = new RemoteViews(currentContext.getPackageName(), R.layout.onesignal_bgimage_notif_layout);
         customView.setTextViewText(R.id.os_bgimage_notif_title, getTitle(fcmJson));
         customView.setTextViewText(R.id.os_bgimage_notif_body, fcmJson.optString("alert"));
         setTextColor(customView, jsonBgImage, R.id.os_bgimage_notif_title, "tc", "onesignal_bgimage_notif_title_color");
         setTextColor(customView, jsonBgImage, R.id.os_bgimage_notif_body, "bc", "onesignal_bgimage_notif_body_color");

         String alignSetting = null;
         if (jsonBgImage != null && jsonBgImage.has("img_align"))
            alignSetting = jsonBgImage.getString("img_align");
         else {
            int iAlignSetting = contextResources.getIdentifier("onesignal_bgimage_notif_image_align", "string", packageName);
            if (iAlignSetting != 0)
               alignSetting = contextResources.getString(iAlignSetting);
         }

         if ("right".equals(alignSetting)) {
            // Use os_bgimage_notif_bgimage_right_aligned instead of os_bgimage_notif_bgimage
            //    which has scaleType="fitEnd" set.
            // This is required as setScaleType can not be called through RemoteViews as it is an enum.
            customView.setViewPadding(R.id.os_bgimage_notif_bgimage_align_layout, -5000, 0, 0, 0);
            customView.setImageViewBitmap(R.id.os_bgimage_notif_bgimage_right_aligned, bg_image);
            customView.setViewVisibility(R.id.os_bgimage_notif_bgimage_right_aligned, 0); // visible
            customView.setViewVisibility(R.id.os_bgimage_notif_bgimage, 2); // gone
         }
         else
            customView.setImageViewBitmap(R.id.os_bgimage_notif_bgimage, bg_image);

         notifBuilder.setContent(customView);

         // Remove style to prevent expanding by the user.
         // Future: Create an extended image option, will need its own layout.
         notifBuilder.setStyle(null);
      }
   }

   private static void setTextColor(RemoteViews customView, JSONObject fcmJson, int viewId, String colorPayloadKey, String colorDefaultResource) {
      Integer color = safeGetColorFromHex(fcmJson, colorPayloadKey);
      if (color != null)
         customView.setTextColor(viewId, color);
      else {
         int colorId = contextResources.getIdentifier(colorDefaultResource, "color", packageName);
         if (colorId != 0)
            customView.setTextColor(viewId, AndroidSupportV4Compat.ContextCompat.getColor(currentContext, colorId));
      }
   }

   private static Integer safeGetColorFromHex(JSONObject fcmJson, String colorKey) {
      try {
         if (fcmJson != null && fcmJson.has(colorKey)) {
            return new BigInteger(fcmJson.optString(colorKey), 16).intValue();
         }
      } catch (Throwable t) {}
      return null;
   }

   private static Bitmap getLargeIcon(JSONObject fcmJson) {
      Bitmap bitmap = getBitmap(fcmJson.optString("licon"));
      if (bitmap == null)
         bitmap = getBitmapFromAssetsOrResourceName("ic_onesignal_large_icon_default");
      
      if (bitmap == null)
         return null;
   
      return resizeBitmapForLargeIconArea(bitmap);
   }
   
   private static Bitmap getDefaultLargeIcon() {
      Bitmap bitmap = getBitmapFromAssetsOrResourceName("ic_onesignal_large_icon_default");
      return resizeBitmapForLargeIconArea(bitmap);
   }
   
   // Resize to prevent extra cropping and boarders.
   private static Bitmap resizeBitmapForLargeIconArea(Bitmap bitmap) {
      if (bitmap == null)
         return null;
      
      try {
         int systemLargeIconHeight = (int) contextResources.getDimension(android.R.dimen.notification_large_icon_height);
         int systemLargeIconWidth = (int) contextResources.getDimension(android.R.dimen.notification_large_icon_width);
         int bitmapHeight = bitmap.getHeight();
         int bitmapWidth = bitmap.getWidth();
      
         if (bitmapWidth > systemLargeIconWidth || bitmapHeight > systemLargeIconHeight) {
            int newWidth = systemLargeIconWidth, newHeight = systemLargeIconHeight;
            if (bitmapHeight > bitmapWidth) {
               float ratio = (float) bitmapWidth / (float) bitmapHeight;
               newWidth = (int) (newHeight * ratio);
            } else if (bitmapWidth > bitmapHeight) {
               float ratio = (float) bitmapHeight / (float) bitmapWidth;
               newHeight = (int) (newWidth * ratio);
            }
         
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
         }
      } catch (Throwable t) {}
   
      return bitmap;
   }

   private static Bitmap getBitmapFromAssetsOrResourceName(String bitmapStr) {
      try {
         Bitmap bitmap = null;

         try {
            bitmap = BitmapFactory.decodeStream(currentContext.getAssets().open(bitmapStr));
         } catch (Throwable t) {}

         if (bitmap != null)
            return bitmap;

         final List<String> image_extensions = Arrays.asList(".png", ".webp", ".jpg", ".gif", ".bmp");
         for (String extension : image_extensions) {
            try {
               bitmap = BitmapFactory.decodeStream(currentContext.getAssets().open(bitmapStr + extension));
            } catch (Throwable t) {}
            if (bitmap != null)
               return bitmap;
         }

         int bitmapId = getResourceIcon(bitmapStr);
         if (bitmapId != 0)
            return BitmapFactory.decodeResource(contextResources, bitmapId);
      } catch (Throwable t) {}

      return null;
   }

   private static Bitmap getBitmapFromURL(String location) {
      try {
         return BitmapFactory.decodeStream(new URL(location).openConnection().getInputStream());
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Could not download image!", t);
      }

      return null;
   }

   private static Bitmap getBitmap(String name) {
      if (name == null)
         return null;
      String trimmedName = name.trim();
      
      if (trimmedName.startsWith("http://") || trimmedName.startsWith("https://"))
         return getBitmapFromURL(trimmedName);

      return getBitmapFromAssetsOrResourceName(name);
   }

   private static int getResourceIcon(String iconName) {
      if (iconName == null)
         return 0;
      
      String trimmedIconName = iconName.trim();
      if (!OSUtils.isValidResourceName(trimmedIconName))
         return 0;

      int notificationIcon = getDrawableId(trimmedIconName);
      if (notificationIcon != 0)
         return notificationIcon;

      // Get system icon resource
      try {
         return drawable.class.getField(iconName).getInt(null);
      } catch (Throwable t) {}

      return 0;
   }

   private static int getSmallIconId(JSONObject fcmJson) {
      int notificationIcon = getResourceIcon(fcmJson.optString("sicon", null));
      if (notificationIcon != 0)
         return notificationIcon;

     return getDefaultSmallIconId();
   }
   
   private static int getDefaultSmallIconId() {
      int notificationIcon = getDrawableId("ic_stat_onesignal_default");
      if (notificationIcon != 0)
         return notificationIcon;
   
      notificationIcon = getDrawableId("corona_statusbar_icon_default");
      if (notificationIcon != 0)
         return notificationIcon;
   
      notificationIcon = getDrawableId("ic_os_notification_fallback_white_24dp");
      if (notificationIcon != 0)
         return notificationIcon;
   
      return drawable.ic_popup_reminder;
   }

   private static int getDrawableId(String name) {
      return contextResources.getIdentifier(name, "drawable", packageName);
   }

   private static boolean isSoundEnabled(JSONObject fcmJson) {
      String sound = fcmJson.optString("sound", null);
      return !"null".equals(sound) && !"nil".equals(sound);
   }

   // Android 5.0 accent color to use, only works when AndroidManifest.xml is targetSdkVersion >= 21
   static BigInteger getAccentColor(JSONObject fcmJson) {
      try {
         if (fcmJson.has("bgac"))
            return new BigInteger(fcmJson.optString("bgac", null), 16);
      } catch (Throwable t) {} // Can throw a parse error.

      // Try to get "onesignal_notification_accent_color" from resources
      // This will get the correct color for day and dark modes
      try {
         String defaultColor = getResourceString(OneSignal.appContext, "onesignal_notification_accent_color", null);
         if (defaultColor != null) {
            return new BigInteger(defaultColor, 16);
         }
      } catch (Throwable t) {} // Can throw a parse error.

      // Get accent color from Manifest
      try {
         String defaultColor = OSUtils.getManifestMeta(OneSignal.appContext, "com.onesignal.NotificationAccentColor.DEFAULT");
         if (defaultColor != null) {
            return new BigInteger(defaultColor, 16);
         }
      } catch (Throwable t) {} // Can throw a parse error.

      return null;
   }

   private static void addNotificationActionButtons(
       JSONObject fcmJson,
       GenerateNotificationOpenIntent intentGenerator,
       NotificationCompat.Builder mBuilder,
       int notificationId,
       String groupSummary
   ) {
      try {
         JSONObject customJson = new JSONObject(fcmJson.optString("custom"));
         
         if (!customJson.has("a"))
            return;
         
         JSONObject additionalDataJSON = customJson.getJSONObject("a");
         if (!additionalDataJSON.has("actionButtons"))
            return;

         JSONArray buttons = additionalDataJSON.getJSONArray("actionButtons");

         for (int i = 0; i < buttons.length(); i++) {
            JSONObject button = buttons.optJSONObject(i);
            JSONObject bundle = new JSONObject(fcmJson.toString());

            Intent buttonIntent = intentGenerator.getNewBaseIntent(notificationId);
            buttonIntent.setAction("" + i); // Required to keep each action button from replacing extras of each other
            buttonIntent.putExtra("action_button", true);
            bundle.put(BUNDLE_KEY_ACTION_ID, button.optString("id"));
            buttonIntent.putExtra(BUNDLE_KEY_ONESIGNAL_DATA, bundle.toString());
            if (groupSummary != null)
               buttonIntent.putExtra("summary", groupSummary);
            else if (fcmJson.has("grp"))
               buttonIntent.putExtra("grp", fcmJson.optString("grp"));

            PendingIntent buttonPIntent = intentGenerator.getNewActionPendingIntent(notificationId, buttonIntent);

            int buttonIcon = 0;
            if (button.has("icon"))
               buttonIcon = getResourceIcon(button.optString("icon"));
            
            mBuilder.addAction(buttonIcon, button.optString("text"), buttonPIntent);
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   private static void addAlertButtons(Context context, JSONObject fcmJson, List<String> buttonsLabels, List<String> buttonsIds) {
      try {
         addCustomAlertButtons(fcmJson, buttonsLabels, buttonsIds);
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Failed to parse JSON for custom buttons for alert dialog.", t);
      }

      if (buttonsLabels.size() == 0 || buttonsLabels.size() < 3) {
         buttonsLabels.add(getResourceString(context, "onesignal_in_app_alert_ok_button_text", "Ok"));
         buttonsIds.add(NotificationBundleProcessor.DEFAULT_ACTION);
      }
   }

   private static void addCustomAlertButtons(JSONObject fcmJson, List<String> buttonsLabels, List<String> buttonsIds) throws JSONException {
      JSONObject customJson = new JSONObject(fcmJson.optString("custom"));

      if (!customJson.has("a"))
         return;

      JSONObject additionalDataJSON = customJson.getJSONObject("a");
      if (!additionalDataJSON.has("actionButtons"))
         return;

      JSONArray buttons = additionalDataJSON.optJSONArray("actionButtons");

      for (int i = 0; i < buttons.length(); i++) {
         JSONObject button = buttons.getJSONObject(i);

         buttonsLabels.add(button.optString("text"));
         buttonsIds.add(button.optString("id"));
      }
   }
   
   private static int convertOSToAndroidPriority(int priority) {
      if (priority > 9)
         return NotificationCompat.PRIORITY_MAX;
      if (priority > 7)
         return NotificationCompat.PRIORITY_HIGH;
      if (priority > 4)
         return NotificationCompat.PRIORITY_DEFAULT;
      if (priority > 2)
         return NotificationCompat.PRIORITY_LOW;
      
      return NotificationCompat.PRIORITY_MIN;
   }
}
