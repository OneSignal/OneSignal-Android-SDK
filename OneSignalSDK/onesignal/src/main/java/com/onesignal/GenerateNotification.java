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

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.R.drawable;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.widget.RemoteViews;

import com.onesignal.OneSignalDbContract.NotificationTable;

import static com.onesignal.OSUtils.getResourceString;

class GenerateNotification {
   private static Context currentContext = null;
   private static String packageName = null;
   private static Resources contextResources = null;
   private static Class<?> notificationOpenedClass;
   private static boolean openerIsBroadcast;

   private static class OneSignalNotificationBuilder {
      NotificationCompat.Builder compatBuilder;
      boolean hasLargeIcon;
   }

   private static void setStatics(Context inContext) {
      currentContext = inContext;
      packageName = currentContext.getPackageName();
      contextResources = currentContext.getResources();

      PackageManager packageManager = currentContext.getPackageManager();
      Intent intent = new Intent(currentContext, NotificationOpenedReceiver.class);
      intent.setPackage(currentContext.getPackageName());
      if (packageManager.queryBroadcastReceivers(intent, 0).size() > 0) {
         openerIsBroadcast = true;
         notificationOpenedClass = NotificationOpenedReceiver.class;
      }
      else
         notificationOpenedClass = NotificationOpenedActivity.class;
   }

   static void fromJsonPayload(NotificationGenerationJob notifJob) {
      setStatics(notifJob.context);

      if (!notifJob.restoring && notifJob.showAsAlert && ActivityLifecycleHandler.curActivity != null) {
         showNotificationAsAlert(notifJob.jsonPayload, ActivityLifecycleHandler.curActivity, notifJob.getAndroidId());
         return;
      }

      showNotification(notifJob);
   }

   private static void showNotificationAsAlert(final JSONObject gcmJson, final Activity activity, final int notificationId) {
      activity.runOnUiThread(new Runnable() {
         @Override
         public void run() {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(getTitle(gcmJson));
            builder.setMessage(gcmJson.optString("alert"));

            List<String> buttonsLabels = new ArrayList<>();
            List<String> buttonIds = new ArrayList<>();

            addAlertButtons(activity, gcmJson, buttonsLabels, buttonIds);

            final List<String> finalButtonIds = buttonIds;

            Intent buttonIntent = getNewBaseIntent(notificationId);
            buttonIntent.putExtra("action_button", true);
            buttonIntent.putExtra("from_alert", true);
            buttonIntent.putExtra("onesignal_data", gcmJson.toString());
            if (gcmJson.has("grp"))
               buttonIntent.putExtra("grp", gcmJson.optString("grp"));

            final Intent finalButtonIntent = buttonIntent;

            DialogInterface.OnClickListener buttonListener = new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int which) {
                  int index = which + 3;

                  if (finalButtonIds.size() > 1) {
                     try {
                        JSONObject newJsonData = new JSONObject(gcmJson.toString());
                        newJsonData.put("actionSelected", finalButtonIds.get(index));
                        finalButtonIntent.putExtra("onesignal_data", newJsonData.toString());

                        NotificationOpenedProcessor.processIntent(activity, finalButtonIntent);
                     } catch (Throwable t) {}
                  } else // No action buttons, close button simply pressed.
                     NotificationOpenedProcessor.processIntent(activity, finalButtonIntent);
               }
            };

            // Back button pressed
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
               @Override
               public void onCancel(DialogInterface dialogInterface) {
                  NotificationOpenedProcessor.processIntent(activity, finalButtonIntent);
               }
            });

            for (int i = 0; i < buttonsLabels.size(); i++) {
               if (i == 0)
                  builder.setNeutralButton(buttonsLabels.get(i), buttonListener);
               else if (i == 1)
                  builder.setNegativeButton(buttonsLabels.get(i), buttonListener);
               else if (i == 2)
                  builder.setPositiveButton(buttonsLabels.get(i), buttonListener);
            }

            AlertDialog alertDialog = builder.create();
            alertDialog.setCanceledOnTouchOutside(false);
            alertDialog.show();
         }
      });
   }
   
   private static CharSequence getTitle(JSONObject gcmBundle) {
      CharSequence title = gcmBundle.optString("title", null);

      if (title != null)
         return title;

      return currentContext.getPackageManager().getApplicationLabel(currentContext.getApplicationInfo());
   }

   private static PendingIntent getNewActionPendingIntent(int requestCode, Intent intent) {
      if (openerIsBroadcast)
         return PendingIntent.getBroadcast(currentContext, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
      return PendingIntent.getActivity(currentContext, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
   }

   private static Intent getNewBaseIntent(int notificationId) {
      Intent intent = new Intent(currentContext, notificationOpenedClass)
                        .putExtra("notificationId", notificationId);

      if (openerIsBroadcast)
         return intent;
      return intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
   }

   private static Intent getNewBaseDeleteIntent(int notificationId) {
      Intent intent = new Intent(currentContext, notificationOpenedClass)
          .putExtra("notificationId", notificationId)
          .putExtra("dismissed", true);
      
      if (openerIsBroadcast)
         return intent;
      return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
   }
   
   private static OneSignalNotificationBuilder getBaseOneSignalNotificationBuilder(NotificationGenerationJob notifJob) {
      JSONObject gcmBundle = notifJob.jsonPayload;
      OneSignalNotificationBuilder oneSignalNotificationBuilder = new OneSignalNotificationBuilder();
      
      NotificationCompat.Builder notifBuilder;
      try {
         String channelId = NotificationChannelManager.createNotificationChannel(notifJob);
         // Will throw if app is using 26.0.0-beta1 or older of the support library.
         notifBuilder = new NotificationCompat.Builder(currentContext, channelId);
      } catch(Throwable t) {
         notifBuilder = new NotificationCompat.Builder(currentContext);
      }
      
      String message = gcmBundle.optString("alert", null);
      
      notifBuilder
         .setAutoCancel(true)
         .setSmallIcon(getSmallIconId(gcmBundle))
         .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
         .setContentText(message)
         .setTicker(message);

      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
          !gcmBundle.optString("title").equals(""))
         notifBuilder.setContentTitle(getTitle(gcmBundle));
      
      int notificationDefaults = 0;
      
      if (OneSignal.getVibrate(currentContext) && gcmBundle.optInt("vib", 1) == 1) {
         if (gcmBundle.has("vib_pt")) {
            long[] vibrationPattern = OSUtils.parseVibrationPattern(gcmBundle);
            if (vibrationPattern != null)
               notifBuilder.setVibrate(vibrationPattern);
         }
         else
            notificationDefaults = Notification.DEFAULT_VIBRATE;
      }
      
      if (gcmBundle.has("ledc") && gcmBundle.optInt("led", 1) == 1) {
         try {
            BigInteger ledColor = new BigInteger(gcmBundle.optString("ledc"), 16);
            notifBuilder.setLights(ledColor.intValue(), 2000, 5000);
         } catch (Throwable t) {
            notificationDefaults |= Notification.DEFAULT_LIGHTS;
         } // Can throw if an old android support lib is used or parse error.
      }
      else
         notificationDefaults |= Notification.DEFAULT_LIGHTS;
   
      if (notifJob.shownTimeStamp != null) {
         try {
            notifBuilder.setWhen(notifJob.shownTimeStamp * 1000L);
         } catch (Throwable t) {} // Can throw if an old android support lib is used.
      }
   
      try {
         BigInteger accentColor = getAccentColor(gcmBundle);
         if (accentColor != null)
            notifBuilder.setColor(accentColor.intValue());
      } catch (Throwable t) {} // Can throw if an old android support lib is used.

      try {
         int visibility = NotificationCompat.VISIBILITY_PUBLIC;
         if (gcmBundle.has("vis"))
            visibility = Integer.parseInt(gcmBundle.optString("vis"));
         notifBuilder.setVisibility(visibility);
      } catch (Throwable t) {} // Can throw if an old android support lib is used or parse error

      Bitmap largeIcon = getLargeIcon(gcmBundle);
      if (largeIcon != null) {
         oneSignalNotificationBuilder.hasLargeIcon = true;
         notifBuilder.setLargeIcon(largeIcon);
      }

      Bitmap bigPictureIcon = getBitmap(gcmBundle.optString("bicon", null));
      if (bigPictureIcon != null)
         notifBuilder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bigPictureIcon).setSummaryText(message));

      if (isSoundEnabled(gcmBundle)) {
         Uri soundUri = OSUtils.getSoundUri(currentContext, gcmBundle.optString("sound", null));
         if (soundUri != null)
            notifBuilder.setSound(soundUri);
         else
            notificationDefaults |= Notification.DEFAULT_SOUND;
      }

      notifBuilder.setDefaults(notificationDefaults);
   
      int payload_priority = gcmBundle.optInt("pri", 6);
      notifBuilder.setPriority(osPriorityToAndroidPriority(payload_priority));
      
      oneSignalNotificationBuilder.compatBuilder = notifBuilder;
      return oneSignalNotificationBuilder;
   }

   private static void removeNotifyOptions(NotificationCompat.Builder builder) {
      builder.setOnlyAlertOnce(true)
             .setDefaults(0)
             .setSound(null)
             .setVibrate(null)
             .setTicker(null);
   }

   // Put the message into a notification and post it.
   private static void showNotification(NotificationGenerationJob notifJob) {
      Random random = new Random();

      int notificationId = notifJob.getAndroidId();
      JSONObject gcmBundle = notifJob.jsonPayload;
      String group = gcmBundle.optString("grp", null);

      OneSignalNotificationBuilder oneSignalNotificationBuilder = getBaseOneSignalNotificationBuilder(notifJob);
      NotificationCompat.Builder notifBuilder = oneSignalNotificationBuilder.compatBuilder;

      addNotificationActionButtons(gcmBundle, notifBuilder, notificationId, null);
      
      try {
         addBackgroundImage(gcmBundle, notifBuilder);
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Could not set background notification image!", t);
      }

      applyNotificationExtender(notifJob, notifBuilder);
      
      // Keeps notification from playing sound + vibrating again
      if (notifJob.restoring)
         removeNotifyOptions(notifBuilder);
   
      Notification notification;

      if (group != null) {
         PendingIntent contentIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseIntent(notificationId).putExtra("onesignal_data", gcmBundle.toString()).putExtra("grp", group));
         notifBuilder.setContentIntent(contentIntent);
         PendingIntent deleteIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseDeleteIntent(notificationId).putExtra("grp", group));
         notifBuilder.setDeleteIntent(deleteIntent);
         notifBuilder.setGroup(group);

         try {
            notifBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
         }
         catch (Throwable t) {
            //do nothing in this case...Android support lib 26 isn't in the project
         }

         notification = createSingleNotificationBeforeSummaryBuilder(notifJob, notifBuilder);
         
         createSummaryNotification(notifJob, oneSignalNotificationBuilder);
      }
      else {
         PendingIntent contentIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseIntent(notificationId).putExtra("onesignal_data", gcmBundle.toString()));
         notifBuilder.setContentIntent(contentIntent);
         PendingIntent deleteIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseDeleteIntent(notificationId));
         notifBuilder.setDeleteIntent(deleteIntent);
         notification = notifBuilder.build();
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
   }

   private static void applyNotificationExtender(
           NotificationGenerationJob notifJob,
           NotificationCompat.Builder notifBuilder) {
      if (notifJob.overrideSettings == null || notifJob.overrideSettings.extender == null)
         return;

      try {
         Field mNotificationField = NotificationCompat.Builder.class.getDeclaredField("mNotification");
         mNotificationField.setAccessible(true);
         Notification mNotification = (Notification) mNotificationField.get(notifBuilder);

         notifJob.orgFlags = mNotification.flags;
         notifJob.orgSound = mNotification.sound;
         notifBuilder.extend(notifJob.overrideSettings.extender);

         mNotification = (Notification)mNotificationField.get(notifBuilder);

         Field mContentTextField = NotificationCompat.Builder.class.getDeclaredField("mContentText");
         mContentTextField.setAccessible(true);
         CharSequence mContentText = (CharSequence)mContentTextField.get(notifBuilder);

         Field mContentTitleField = NotificationCompat.Builder.class.getDeclaredField("mContentTitle");
         mContentTitleField.setAccessible(true);
         CharSequence mContentTitle = (CharSequence)mContentTitleField.get(notifBuilder);

         notifJob.overriddenBodyFromExtender = mContentText;
         notifJob.overriddenTitleFromExtender = mContentTitle;
         if (!notifJob.restoring) {
            notifJob.overriddenFlags = mNotification.flags;
            notifJob.overriddenSound = mNotification.sound;
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }

   }
   
   // Removes custom sound set from the extender from non-summary notification before building it.
   //   This prevents the sound from playing twice or both the default sound + a custom one.
   private static Notification createSingleNotificationBeforeSummaryBuilder(NotificationGenerationJob notifJob, NotificationCompat.Builder notifBuilder) {
      // Includes Android 4.3 through 6.0.1. Android 7.1 handles this correctly without this.
      // Android 4.2 and older just post the summary only.
      boolean singleNotifWorkArounds = Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1 && Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                                       && !notifJob.restoring;
      
      if (singleNotifWorkArounds) {
         if (notifJob.overriddenSound != null && !notifJob.overriddenSound.equals(notifJob.orgSound))
            notifBuilder.setSound(null);
      }

      Notification notification = notifBuilder.build();

      if (singleNotifWorkArounds)
         notifBuilder.setSound(notifJob.overriddenSound);

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
   
   static void updateSummaryNotification(NotificationGenerationJob notifJob) {
      setStatics(notifJob.context);
      createSummaryNotification(notifJob, null);
   }
   
   // This summary notification will be visible instead of the normal one on pre-Android 7.0 devices.
   private static void createSummaryNotification(NotificationGenerationJob notifJob, OneSignalNotificationBuilder notifBuilder) {
      boolean updateSummary = notifJob.restoring;
      JSONObject gcmBundle = notifJob.jsonPayload;

      String group = gcmBundle.optString("grp", null);

      Random random = new Random();
      PendingIntent summaryDeleteIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseDeleteIntent(0).putExtra("summary", group));
      
      Notification summaryNotification;
      Integer summaryNotificationId = null;
   
      String firstFullData = null;
      Collection<SpannableString> summaryList = null;
      
      OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(currentContext);
      Cursor cursor = null;
      
      try {
         SQLiteDatabase readableDb = dbHelper.getReadableDbWithRetries();
   
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
         if (!updateSummary && notifJob.getAndroidId() != -1)
            whereStr += " AND " + NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " <> " + notifJob.getAndroidId();
         
         cursor = readableDb.query(
             NotificationTable.TABLE_NAME,
             retColumn,
             whereStr,
             whereArgs,
             null,                                                    // group by
             null,                                                    // filter by row groups
             NotificationTable._ID + " DESC"                          // sort order, new to old
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

                  // Html.fromHtml("<strong>" + line1Title + "</strong> " + gcmBundle.getString("alert"));

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
                  gcmBundle = new JSONObject(firstFullData);
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
      
      PendingIntent summaryContentIntent = getNewActionPendingIntent(random.nextInt(), createBaseSummaryIntent(summaryNotificationId, gcmBundle, group));
      
      
      // 2 or more notifications with a group received, group them together as a single notification.
      if (summaryList != null &&
          ((updateSummary && summaryList.size() > 1) ||
           (!updateSummary && summaryList.size() > 0))) {
         int notificationCount = summaryList.size() + (updateSummary ? 0 : 1);

         String summaryMessage = gcmBundle.optString("grp_msg", null);
         if (summaryMessage == null)
            summaryMessage = notificationCount + " new messages";
         else
            summaryMessage = summaryMessage.replace("$[notif_count]", "" + notificationCount);

         NotificationCompat.Builder summaryBuilder = getBaseOneSignalNotificationBuilder(notifJob).compatBuilder;
         if (updateSummary)
            removeNotifyOptions(summaryBuilder);
         else {
            if (notifJob.overriddenSound != null)
               summaryBuilder.setSound(notifJob.overriddenSound);
   
            if (notifJob.overriddenFlags != null)
               summaryBuilder.setDefaults(notifJob.overriddenFlags);
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
            
            if (notifJob.getTitle() != null)
               line1Title = notifJob.getTitle().toString();

            if (line1Title == null)
               line1Title = "";
            else
               line1Title += " ";
            
            String message = notifJob.getBody().toString();
            
            SpannableString spannableString = new SpannableString(line1Title + message);
            if (line1Title.length() > 0)
               spannableString.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, line1Title.length(), 0);
            inboxStyle.addLine(spannableString);
         }

         for(SpannableString line : summaryList)
            inboxStyle.addLine(line);
         inboxStyle.setBigContentTitle(summaryMessage);
         summaryBuilder.setStyle(inboxStyle);

         summaryNotification = summaryBuilder.build();
      }
      else {
         // First notification with this group key, post like a normal notification.
         NotificationCompat.Builder summaryBuilder = notifBuilder.compatBuilder;
            
         // We are re-using the notifBuilder from the normal notification so if a developer as an
         //    extender setup all the settings will carry over.
         // Note: However their buttons will not carry over as we need to be setup with this new summaryNotificationId.
         summaryBuilder.mActions.clear();
         addNotificationActionButtons(gcmBundle, summaryBuilder, summaryNotificationId, group);
         
         summaryBuilder.setContentIntent(summaryContentIntent)
                       .setDeleteIntent(summaryDeleteIntent)
                       .setOnlyAlertOnce(updateSummary)
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
   
   private static Intent createBaseSummaryIntent(int summaryNotificationId, JSONObject gcmBundle, String group) {
     return getNewBaseIntent(summaryNotificationId).putExtra("onesignal_data", gcmBundle.toString()).putExtra("summary", group);
   }
   
   private static void createSummaryIdDatabaseEntry(OneSignalDbHelper dbHelper, String group, int id) {
      // There currently isn't a visible notification from for this groupid.
      // Save the group summary notification id so it can be updated later.
      SQLiteDatabase writableDb = null;
      try {
         writableDb = dbHelper.getWritableDbWithRetries();
         writableDb.beginTransaction();
      
         ContentValues values = new ContentValues();
         values.put(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID, id);
         values.put(NotificationTable.COLUMN_NAME_GROUP_ID, group);
         values.put(NotificationTable.COLUMN_NAME_IS_SUMMARY, 1);
         writableDb.insertOrThrow(NotificationTable.TABLE_NAME, null, values);
         writableDb.setTransactionSuccessful();
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error adding summary notification record! ", t);
      } finally {
         if (writableDb != null) {
            try {
               writableDb.endTransaction(); // May throw if transaction was never opened or DB is full.
            } catch (Throwable t) {
               OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error closing transaction! ", t);
            }
         }
      }
   }

   // Keep 'throws Throwable' as 'onesignal_bgimage_notif_layout' may not be available
   //    This maybe the case if a jar is used instead of an aar.
   private static void addBackgroundImage(JSONObject gcmBundle, NotificationCompat.Builder notifBuilder) throws Throwable {
      // Required to right align image
      if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
         return;

      Bitmap bg_image = null;
      JSONObject jsonBgImage = null;
      String jsonStrBgImage = gcmBundle.optString("bg_img", null);

      if (jsonStrBgImage != null) {
         jsonBgImage = new JSONObject(jsonStrBgImage);
         bg_image = getBitmap(jsonBgImage.optString("img", null));
      }

      if (bg_image == null)
         bg_image = getBitmapFromAssetsOrResourceName("onesignal_bgimage_default_image");

      if (bg_image != null) {
         RemoteViews customView = new RemoteViews(currentContext.getPackageName(), R.layout.onesignal_bgimage_notif_layout);
         customView.setTextViewText(R.id.os_bgimage_notif_title, getTitle(gcmBundle));
         customView.setTextViewText(R.id.os_bgimage_notif_body, gcmBundle.optString("alert"));
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

   private static void setTextColor(RemoteViews customView, JSONObject gcmBundle, int viewId, String colorPayloadKey, String colorDefaultResource) {
      Integer color = safeGetColorFromHex(gcmBundle, colorPayloadKey);
      if (color != null)
         customView.setTextColor(viewId, color);
      else {
         int colorId = contextResources.getIdentifier(colorDefaultResource, "color", packageName);
         if (colorId != 0)
            customView.setTextColor(viewId, AndroidSupportV4Compat.ContextCompat.getColor(currentContext, colorId));
      }
   }

   private static Integer safeGetColorFromHex(JSONObject gcmBundle, String colorKey) {
      try {
         if (gcmBundle != null && gcmBundle.has(colorKey)) {
            return new BigInteger(gcmBundle.optString(colorKey), 16).intValue();
         }
      } catch (Throwable t) {}
      return null;
   }

   private static Bitmap getLargeIcon(JSONObject gcmBundle) {
      Bitmap bitmap = getBitmap(gcmBundle.optString("licon"));
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

   private static int getSmallIconId(JSONObject gcmBundle) {
      int notificationIcon = getResourceIcon(gcmBundle.optString("sicon", null));
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

   private static boolean isSoundEnabled(JSONObject gcmBundle) {
      String sound = gcmBundle.optString("sound", null);
      if ("null".equals(sound) || "nil".equals(sound))
         return false;
      return OneSignal.getSoundEnabled(currentContext);
   }

   // Android 5.0 accent color to use, only works when AndroidManifest.xml is targetSdkVersion >= 21
   private static BigInteger getAccentColor(JSONObject gcmBundle) {
      try {
         if (gcmBundle.has("bgac"))
            return new BigInteger(gcmBundle.optString("bgac", null), 16);
      } catch (Throwable t) {} // Can throw a parse error parse error.

      try {
         String defaultColor = OSUtils.getManifestMeta(currentContext, "com.onesignal.NotificationAccentColor.DEFAULT");
         if (defaultColor != null)
            return new BigInteger(defaultColor, 16);
      } catch (Throwable t) {} // Can throw a parse error parse error.

      return null;
   }

   private static void addNotificationActionButtons(JSONObject gcmBundle, NotificationCompat.Builder mBuilder, int notificationId, String groupSummary) {
      try {
         JSONObject customJson = new JSONObject(gcmBundle.optString("custom"));
         
         if (!customJson.has("a"))
            return;
         
         JSONObject additionalDataJSON = customJson.getJSONObject("a");
         if (!additionalDataJSON.has("actionButtons"))
            return;

         JSONArray buttons = additionalDataJSON.getJSONArray("actionButtons");

         for (int i = 0; i < buttons.length(); i++) {
            JSONObject button = buttons.optJSONObject(i);
            JSONObject bundle = new JSONObject(gcmBundle.toString());

            Intent buttonIntent = getNewBaseIntent(notificationId);
            buttonIntent.setAction("" + i); // Required to keep each action button from replacing extras of each other
            buttonIntent.putExtra("action_button", true);
            bundle.put("actionSelected", button.optString("id"));
            buttonIntent.putExtra("onesignal_data", bundle.toString());
            if (groupSummary != null)
               buttonIntent.putExtra("summary", groupSummary);
            else if (gcmBundle.has("grp"))
               buttonIntent.putExtra("grp", gcmBundle.optString("grp"));

            PendingIntent buttonPIntent = getNewActionPendingIntent(notificationId, buttonIntent);

            int buttonIcon = 0;
            if (button.has("icon"))
               buttonIcon = getResourceIcon(button.optString("icon"));
            
            mBuilder.addAction(buttonIcon, button.optString("text"), buttonPIntent);
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   private static void addAlertButtons(Context context, JSONObject gcmBundle, List<String> buttonsLabels, List<String> buttonsIds) {
      try {
         addCustomAlertButtons(context, gcmBundle, buttonsLabels, buttonsIds);
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Failed to parse JSON for custom buttons for alert dialog.", t);
      }

      if (buttonsLabels.size() == 0 || buttonsLabels.size() < 3) {
         buttonsLabels.add(getResourceString(context, "onesignal_in_app_alert_ok_button_text", "Ok"));
         buttonsIds.add(NotificationBundleProcessor.DEFAULT_ACTION);
      }
   }

   private static void addCustomAlertButtons(Context context, JSONObject gcmBundle, List<String> buttonsLabels, List<String> buttonsIds) throws JSONException {
      JSONObject customJson = new JSONObject(gcmBundle.optString("custom"));

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
   
   private static int osPriorityToAndroidPriority(int priority) {
      if (priority > 9)
         return NotificationCompat.PRIORITY_MAX;
      if (priority > 7)
         return NotificationCompat.PRIORITY_HIGH;
      if (priority > 5)
         return NotificationCompat.PRIORITY_DEFAULT;
      if (priority > 3)
         return NotificationCompat.PRIORITY_LOW;
      
      return NotificationCompat.PRIORITY_MIN;
   }
}