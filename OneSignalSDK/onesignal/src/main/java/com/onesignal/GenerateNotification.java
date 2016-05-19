/**
 * Modified MIT License
 * 
 * Copyright 2016 OneSignal
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
import android.content.ContentResolver;
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
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.SpannableString;
import android.text.style.StyleSpan;

import com.onesignal.OneSignalDbContract.NotificationTable;

class GenerateNotification {
   private static Context currentContext = null;
   private static String packageName = null;
   private static Resources contextResources = null;
   private static Class<?> notificationOpenedClass;
   private static boolean openerIsBroadcast;

   static void setStatics(Context inContext) {
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

   static int fromBundle(Context inContext, Bundle bundle, boolean showAsAlert, NotificationExtenderService.OverrideSettings overrideSettings) {
      setStatics(inContext);

      JSONObject jsonBundle = NotificationBundleProcessor.bundleAsJSONObject(bundle);

      if (showAsAlert && ActivityLifecycleHandler.curActivity != null)
         return showNotificationAsAlert(jsonBundle, ActivityLifecycleHandler.curActivity);

      return showNotification(jsonBundle, overrideSettings);
   }

   private static int showNotificationAsAlert(final JSONObject gcmJson, final Activity activity) {
      final int aNotificationId = new Random().nextInt();

      activity.runOnUiThread(new Runnable() {
         @Override
         public void run() {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(getTitle(gcmJson));
            try {
               builder.setMessage(gcmJson.getString("alert"));
            } catch (Throwable t) {}

            List<String> buttonsLabels = new ArrayList<String>();
            List<String> buttonIds = new ArrayList<String>();

            addAlertButtons(gcmJson, buttonsLabels, buttonIds);

            final List<String> finalButtonIds = buttonIds;

            Intent buttonIntent = getNewBaseIntent(aNotificationId);
            buttonIntent.putExtra("action_button", true);
            buttonIntent.putExtra("from_alert", true);
            buttonIntent.putExtra("onesignal_data", gcmJson.toString());
            try {
               if (gcmJson.has("grp"))
                  buttonIntent.putExtra("grp", gcmJson.getString("grp"));
            } catch (JSONException e) {}

            final Intent finalButtonIntent = buttonIntent;

            DialogInterface.OnClickListener buttonListener = new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int which) {
                  int index = which + 3;

                  if (finalButtonIds.size() > 1) {
                     try {
                        JSONObject customJson = new JSONObject(gcmJson.getString("custom"));
                        JSONObject additionalDataJSON = customJson.getJSONObject("a");
                        additionalDataJSON.put("actionSelected", finalButtonIds.get(index));

                        JSONObject newJsonData = new JSONObject(gcmJson.toString());
                        newJsonData.put("custom", customJson.toString());

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

      return aNotificationId;
   }

   private static CharSequence getTitle(JSONObject gcmBundle) {
      CharSequence title = null;
      try { title = gcmBundle.getString("title"); } catch (Throwable t) {}

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

   private static NotificationCompat.Builder getBaseNotificationCompatBuilder(JSONObject gcmBundle, boolean notify) {
      int notificationIcon = getSmallIconId(gcmBundle);

      int notificationDefaults = 0;

      if (OneSignal.getVibrate(currentContext))
         notificationDefaults = Notification.DEFAULT_VIBRATE;

      String message = null;
      try {
         message = gcmBundle.getString("alert");
      } catch (Throwable t) {}

      NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(currentContext)
            .setAutoCancel(true)
            .setSmallIcon(notificationIcon) // Small Icon required or notification doesn't display
            .setContentTitle(getTitle(gcmBundle))
            .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
            .setContentText(message);
      if (notify)
         notifBuilder.setTicker(message);

      try {
         BigInteger accentColor = getAccentColor(gcmBundle);
         if (accentColor != null)
            notifBuilder.setColor(accentColor.intValue());
      } catch (Throwable t) {} // Can throw if an old android support lib is used.

      BigInteger ledColor = null;

      if (notify && gcmBundle.has("ledc")) {
         try {
            ledColor = new BigInteger(gcmBundle.getString("ledc"), 16);
            notifBuilder.setLights(ledColor.intValue(), 2000, 5000);
         } catch (Throwable t) {
            notificationDefaults |= Notification.DEFAULT_LIGHTS;
         } // Can throw if an old android support lib is used or parse error.
      } else
         notificationDefaults |= Notification.DEFAULT_LIGHTS;

      try {
         int visibility = Notification.VISIBILITY_PUBLIC;
         if (gcmBundle.has("vis"))
            visibility = Integer.parseInt(gcmBundle.getString("vis"));
         notifBuilder.setVisibility(visibility);
      } catch (Throwable t) {} // Can throw if an old android support lib is used or parse error

      Bitmap largeIcon = getLargeIcon(gcmBundle);
      if (largeIcon != null)
         notifBuilder.setLargeIcon(largeIcon);

      Bitmap bigPictureIcon = getBitmapIcon(gcmBundle, "bicon");
      if (bigPictureIcon != null)
         notifBuilder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bigPictureIcon).setSummaryText(message));

      if (notify && OneSignal.getSoundEnabled(currentContext)) {
         Uri soundUri = getCustomSound(gcmBundle);
         if (soundUri != null)
            notifBuilder.setSound(soundUri);
         else
            notificationDefaults |= Notification.DEFAULT_SOUND;
      }

      if (!notify)
         notificationDefaults = 0;

      notifBuilder.setDefaults(notificationDefaults);

      return notifBuilder;
   }

   // Put the message into a notification and post it.
   private static int showNotification(JSONObject gcmBundle, NotificationExtenderService.OverrideSettings overrideSettings) {
      Random random = new Random();

      String group = null;
      try {
         group = gcmBundle.getString("grp");
      } catch (Throwable t) {}

      int notificationId = random.nextInt();

      NotificationCompat.Builder notifBuilder = getBaseNotificationCompatBuilder(gcmBundle, true);

      addNotificationActionButtons(gcmBundle, notifBuilder, notificationId, null);

      if (overrideSettings != null && overrideSettings.extender != null)
         notifBuilder.extend(overrideSettings.extender);

      if (group != null) {
         PendingIntent contentIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseIntent(notificationId).putExtra("onesignal_data", gcmBundle.toString()).putExtra("grp", group));
         notifBuilder.setContentIntent(contentIntent);
         PendingIntent deleteIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseDeleteIntent(notificationId).putExtra("grp", group));
         notifBuilder.setDeleteIntent(deleteIntent);
         notifBuilder.setGroup(group);

         createSummaryNotification(gcmBundle);
      }
      else {
         PendingIntent contentIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseIntent(notificationId).putExtra("onesignal_data", gcmBundle.toString()));
         notifBuilder.setContentIntent(contentIntent);
         PendingIntent deleteIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseDeleteIntent(notificationId));
         notifBuilder.setDeleteIntent(deleteIntent);
      }

      // NotificationManagerCompat does not auto omit the individual notification on the device when using
      //   stacked notifications on Android 4.2 and older
      // The benefits of calling notify for individual notifications in-addition to the summary above it is shows
      //   each notification in a stack on Android Wear and each one is actionable just like the Gmail app does per email.
      if (group == null || android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) {
         NotificationManagerCompat.from(currentContext).notify(notificationId, notifBuilder.build());
      }

      return notificationId;
   }

   private static void createSummaryNotification(JSONObject gcmBundle) {
      createSummaryNotification(null, false, gcmBundle);
   }

   static void createSummaryNotification(Context inContext,  boolean updateSummary, JSONObject gcmBundle) {
      if (updateSummary)
         setStatics(inContext);

      String group = null;
      try {
         group = gcmBundle.getString("grp");
      } catch (Throwable t) {}

      Random random = new Random();
      PendingIntent summaryDeleteIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseDeleteIntent(0).putExtra("summary", group));

      OneSignalDbHelper dbHelper = new OneSignalDbHelper(currentContext);
      SQLiteDatabase writableDb = dbHelper.getWritableDatabase();

      String[] retColumn = { NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID,
                             NotificationTable.COLUMN_NAME_FULL_DATA,
                             NotificationTable.COLUMN_NAME_IS_SUMMARY,
                             NotificationTable.COLUMN_NAME_TITLE,
                             NotificationTable.COLUMN_NAME_MESSAGE };

      String[] whereArgs = { group };

      Cursor cursor = writableDb.query(
                     NotificationTable.TABLE_NAME,
                     retColumn,
                     NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +   // Where String
                     NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                     NotificationTable.COLUMN_NAME_OPENED + " = 0",
                     whereArgs,
                     null,                                                    // group by
                     null,                                                    // filter by row groups
                     NotificationTable._ID + " DESC"                          // sort order, new to old
      );

      Notification summaryNotification;
      int summaryNotificationId = random.nextInt();

      String firstFullData = null;
      Collection<SpannableString> summeryList = null;

      if (cursor.moveToFirst()) {
         SpannableString spannableString;
         summeryList = new ArrayList<SpannableString>();

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
               summeryList.add(spannableString);

               if (firstFullData == null)
                  firstFullData = cursor.getString(cursor.getColumnIndex(NotificationTable.COLUMN_NAME_FULL_DATA));
            }
         } while (cursor.moveToNext());

         if (updateSummary) {
            try {
               gcmBundle = new JSONObject(firstFullData);
            } catch (JSONException e) {
               e.printStackTrace();
            }
         }
      }

      if (summeryList != null && (!updateSummary || summeryList.size() > 1)) {
         int notificationCount = summeryList.size() + (updateSummary ? 0 : 1);

         String summaryMessage = null;

         if (gcmBundle.has("grp_msg")) {
            try {
               summaryMessage = gcmBundle.getString("grp_msg").replace("$[notif_count]", "" + notificationCount);
            } catch (Throwable t) {}
         }
         if (summaryMessage == null)
            summaryMessage = notificationCount + " new messages";

         JSONObject summaryDataBundle = new JSONObject();
         try {
            summaryDataBundle.put("alert", summaryMessage);
         } catch (JSONException e) {
            e.printStackTrace();
         }
         Intent summaryIntent = getNewBaseIntent(summaryNotificationId)
                              .putExtra("summary", group)
                              .putExtra("onesignal_data", summaryDataBundle.toString());

         PendingIntent summaryContentIntent = getNewActionPendingIntent(random.nextInt(), summaryIntent);

         NotificationCompat.Builder summeryBuilder = getBaseNotificationCompatBuilder(gcmBundle, !updateSummary);

         summeryBuilder.setContentIntent(summaryContentIntent)
              .setDeleteIntent(summaryDeleteIntent)
              .setContentTitle(currentContext.getPackageManager().getApplicationLabel(currentContext.getApplicationInfo()))
              .setContentText(summaryMessage)
              .setNumber(notificationCount)
              .setOnlyAlertOnce(updateSummary)
              .setGroup(group)
              .setGroupSummary(true);

         if (!updateSummary)
            summeryBuilder.setTicker(summaryMessage);

         NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
         String line1Title = null;

         // Add the latest notification to the summary
         if (!updateSummary) {
            try {
               line1Title = gcmBundle.getString("title");
            } catch (Throwable t) {}

            if (line1Title == null)
               line1Title = "";
            else
               line1Title += " ";

            String message = "";
            try {
               message = gcmBundle.getString("alert");
            } catch (Throwable t) {}

            SpannableString spannableString = new SpannableString(line1Title + message);
            if (line1Title.length() > 0)
               spannableString.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, line1Title.length(), 0);
            inboxStyle.addLine(spannableString);
         }

         for(SpannableString line : summeryList)
            inboxStyle.addLine(line);
         inboxStyle.setBigContentTitle(summaryMessage);
         summeryBuilder.setStyle(inboxStyle);

         summaryNotification = summeryBuilder.build();
      }
      else {
         // There currently isn't a visible notification from this group, save the group summary notification id and post it so it looks like a normal notification.
         ContentValues values = new ContentValues();
         values.put(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID, summaryNotificationId);
         values.put(NotificationTable.COLUMN_NAME_GROUP_ID, group);
         values.put(NotificationTable.COLUMN_NAME_IS_SUMMARY, 1);

         writableDb.insert(NotificationTable.TABLE_NAME, null, values);

         NotificationCompat.Builder notifBuilder = getBaseNotificationCompatBuilder(gcmBundle, !updateSummary);

         PendingIntent summaryContentIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseIntent(summaryNotificationId).putExtra("onesignal_data", gcmBundle.toString()).putExtra("summary", group));

         addNotificationActionButtons(gcmBundle, notifBuilder, summaryNotificationId, group);
         notifBuilder.setContentIntent(summaryContentIntent)
                     .setDeleteIntent(summaryDeleteIntent)
                     .setOnlyAlertOnce(updateSummary)
                     .setGroup(group)
                     .setGroupSummary(true);

         summaryNotification = notifBuilder.build();
      }

      NotificationManagerCompat.from(currentContext).notify(summaryNotificationId, summaryNotification);

      cursor.close();
      writableDb.close();
   }

   private static boolean isValidResourceName(String name) {
      return (name != null && !name.matches("^[0-9]"));
   }

   private static Bitmap getLargeIcon(JSONObject gcmBundle) {
      if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB)
         return null;

      Bitmap bitmap = getBitmapIcon(gcmBundle, "licon");
      if (bitmap == null)
         bitmap = getBitmapFromAssetsOrResourceName("ic_onesignal_large_icon_default");
      if (bitmap == null)
         bitmap = getBitmapFromAssetsOrResourceName("ic_gamethrive_large_icon_default");

      if (bitmap == null)
         return null;

      // Check to see if we need to shrink the bitmap to prevent cropping when
      // displayed.
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
      } catch (Throwable t) {}

      return null;
   }

   private static Bitmap getBitmapIcon(JSONObject gcmBundle, String key) {
      if (gcmBundle.has(key)) {
         String bitmapStr = null;
         try {
            bitmapStr = gcmBundle.getString(key);
         } catch (Throwable t) {}

         if (bitmapStr.startsWith("http://") || bitmapStr.startsWith("https://"))
            return getBitmapFromURL(bitmapStr);

         return getBitmapFromAssetsOrResourceName(bitmapStr);
      }

      return null;
   }

   private static int getResourceIcon(String iconName) {
      if (!isValidResourceName(iconName))
         return 0;

      int notificationIcon = getDrawableId(iconName);
      if (notificationIcon != 0)
         return notificationIcon;

      // Get system icon resource
      try {
         return drawable.class.getField(iconName).getInt(null);
      } catch (Throwable t) {}

      return 0;
   }

   private static int getSmallIconId(JSONObject gcmBundle) {
      int notificationIcon = 0;

      if (gcmBundle.has("sicon")) {
         try {
            notificationIcon = getResourceIcon(gcmBundle.getString("sicon"));
         } catch (Throwable t) {}
         if (notificationIcon != 0)
            return notificationIcon;
      }

      notificationIcon = getDrawableId("ic_stat_onesignal_default");
      if (notificationIcon != 0)
         return notificationIcon;

      notificationIcon = getDrawableId("ic_stat_gamethrive_default");
      if (notificationIcon != 0)
         return notificationIcon;

      notificationIcon = getDrawableId("corona_statusbar_icon_default");
      if (notificationIcon != 0)
         return notificationIcon;

      // Launcher icon
      notificationIcon = currentContext.getApplicationInfo().icon;
      if (notificationIcon != 0)
         return notificationIcon;

      return drawable.sym_def_app_icon; // Catches case where icon isn't set in the AndroidManifest.xml
   }

   private static int getDrawableId(String name) {
      return contextResources.getIdentifier(name, "drawable", packageName);
   }

   private static Uri getCustomSound(JSONObject gcmBundle) {
      int soundId;
      String sound = null;
      try {
         if (gcmBundle.has("sound"))
            sound = gcmBundle.getString("sound");
      } catch (Throwable t) {
         return null;
      }

      if (isValidResourceName(sound)) {
         soundId = contextResources.getIdentifier(sound, "raw", packageName);
         if (soundId != 0)
            return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundId);
      }

      soundId = contextResources.getIdentifier("onesignal_default_sound", "raw", packageName);
      if (soundId != 0)
         return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundId);

      soundId = contextResources.getIdentifier("gamethrive_default_sound", "raw", packageName);
      if (soundId != 0)
         return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundId);

      return null;
   }

   // Android 5.0 accent color to use, only works when AndroidManifest.xml is targetSdkVersion >= 21
   private static BigInteger getAccentColor(JSONObject gcmBundle) {
      try {
         if (gcmBundle.has("bgac"))
            return new BigInteger(gcmBundle.getString("bgac"), 16);
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
         JSONObject customJson = new JSONObject(gcmBundle.getString("custom"));

         if (customJson.has("a")) {
            JSONObject additionalDataJSON = customJson.getJSONObject("a");
            if (additionalDataJSON.has("actionButtons")) {

               JSONArray buttons = additionalDataJSON.getJSONArray("actionButtons");

               for (int i = 0; i < buttons.length(); i++) {
                  JSONObject button = buttons.getJSONObject(i);
                  additionalDataJSON.put("actionSelected", button.getString("id"));
                  
                  JSONObject bundle = new JSONObject(gcmBundle.toString());
                  bundle.put("custom", customJson.toString());

                  Intent buttonIntent = getNewBaseIntent(notificationId);
                  buttonIntent.setAction("" + i); // Required to keep each action button from replacing extras of each other
                  buttonIntent.putExtra("action_button", true);
                  buttonIntent.putExtra("onesignal_data", bundle.toString());
                  if (groupSummary != null)
                     buttonIntent.putExtra("summary", groupSummary);
                  else if (gcmBundle.has("grp"))
                     buttonIntent.putExtra("grp", gcmBundle.getString("grp"));

                  PendingIntent buttonPIntent = getNewActionPendingIntent(notificationId, buttonIntent);

                  int buttonIcon = 0;
                  if (button.has("icon"))
                     buttonIcon = getResourceIcon(button.getString("icon"));

                  mBuilder.addAction(buttonIcon, button.getString("text"), buttonPIntent);
               }
            }
         }
      } catch (JSONException e) {
         e.printStackTrace();
      }
   }

   private static void addAlertButtons(JSONObject gcmBundle, List<String> buttonsLabels, List<String> buttonsIds) {
      try {
         JSONObject customJson = new JSONObject(gcmBundle.getString("custom"));

         if (customJson.has("a")) {
            JSONObject additionalDataJSON = customJson.getJSONObject("a");
            if (additionalDataJSON.has("actionButtons")) {

               JSONArray buttons = additionalDataJSON.getJSONArray("actionButtons");

               for (int i = 0; i < buttons.length(); i++) {
                  JSONObject button = buttons.getJSONObject(i);

                  buttonsLabels.add(button.getString("text"));
                  buttonsIds.add(button.getString("id"));
               }
            }
         }

         if (buttonsLabels.size() < 3) {
            buttonsLabels.add("Close");
            buttonsIds.add(NotificationBundleProcessor.DEFAULT_ACTION);
         }
      } catch (JSONException e) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Failed to parse buttons for alert dialog.", e);
      }
   }
}