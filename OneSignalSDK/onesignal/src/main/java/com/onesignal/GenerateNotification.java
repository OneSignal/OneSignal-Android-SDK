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

   static void fromJsonPayload(Context inContext, boolean restoring, int notificationId, JSONObject jsonPayload, boolean showAsAlert, NotificationExtenderService.OverrideSettings overrideSettings) {
      setStatics(inContext);

      if (!restoring && showAsAlert && ActivityLifecycleHandler.curActivity != null) {
         showNotificationAsAlert(jsonPayload, ActivityLifecycleHandler.curActivity, notificationId);
         return;
      }

      showNotification(notificationId, restoring, jsonPayload, overrideSettings);
   }

   private static void showNotificationAsAlert(final JSONObject gcmJson, final Activity activity, final int notificationId) {
      activity.runOnUiThread(new Runnable() {
         @Override
         public void run() {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(getTitle(gcmJson));
            builder.setMessage(gcmJson.optString("alert"));

            List<String> buttonsLabels = new ArrayList<String>();
            List<String> buttonIds = new ArrayList<String>();

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

   private static NotificationCompat.Builder getBaseNotificationCompatBuilder(JSONObject gcmBundle) {
      int notificationIcon = getSmallIconId(gcmBundle);

      int notificationDefaults = 0;

      if (OneSignal.getVibrate(currentContext))
         notificationDefaults = Notification.DEFAULT_VIBRATE;

      String message = gcmBundle.optString("alert", null);

      NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(currentContext)
            .setAutoCancel(true)
            .setSmallIcon(notificationIcon) // Small Icon required or notification doesn't display
            .setContentTitle(getTitle(gcmBundle))
            .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
            .setContentText(message)
            .setTicker(message);

      try {
         BigInteger accentColor = getAccentColor(gcmBundle);
         if (accentColor != null)
            notifBuilder.setColor(accentColor.intValue());
      } catch (Throwable t) {} // Can throw if an old android support lib is used.

      BigInteger ledColor = null;

      if (gcmBundle.has("ledc")) {
         try {
            ledColor = new BigInteger(gcmBundle.optString("ledc"), 16);
            notifBuilder.setLights(ledColor.intValue(), 2000, 5000);
         } catch (Throwable t) {
            notificationDefaults |= Notification.DEFAULT_LIGHTS;
         } // Can throw if an old android support lib is used or parse error.
      } else
         notificationDefaults |= Notification.DEFAULT_LIGHTS;

      try {
         int visibility = Notification.VISIBILITY_PUBLIC;
         if (gcmBundle.has("vis"))
            visibility = Integer.parseInt(gcmBundle.optString("vis"));
         notifBuilder.setVisibility(visibility);
      } catch (Throwable t) {} // Can throw if an old android support lib is used or parse error

      Bitmap largeIcon = getLargeIcon(gcmBundle);
      if (largeIcon != null)
         notifBuilder.setLargeIcon(largeIcon);

      Bitmap bigPictureIcon = getBitmap(gcmBundle.optString("bicon", null));
      if (bigPictureIcon != null)
         notifBuilder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bigPictureIcon).setSummaryText(message));

      if (isSoundEnabled(gcmBundle)) {
         Uri soundUri = getCustomSound(gcmBundle);
         if (soundUri != null)
            notifBuilder.setSound(soundUri);
         else
            notificationDefaults |= Notification.DEFAULT_SOUND;
      }

      notifBuilder.setDefaults(notificationDefaults);

      return notifBuilder;
   }

   private static void removeNotifyOptions(NotificationCompat.Builder builder) {
      builder.setDefaults(0)
             .setSound(null)
             .setVibrate(null)
             .setTicker(null);
   }

   // Put the message into a notification and post it.
   static void showNotification(int notificationId, boolean restoring, JSONObject gcmBundle, NotificationExtenderService.OverrideSettings overrideSettings) {
      Random random = new Random();

      String group = gcmBundle.optString("grp", null);

      NotificationCompat.Builder notifBuilder = getBaseNotificationCompatBuilder(gcmBundle);

      addNotificationActionButtons(gcmBundle, notifBuilder, notificationId, null);
      try {
         addBackgroundImage(gcmBundle, notifBuilder);
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Could not set background notification image!", t);
      }

      if (overrideSettings != null && overrideSettings.extender != null)
         notifBuilder.extend(overrideSettings.extender);

      if (group != null) {
         PendingIntent contentIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseIntent(notificationId).putExtra("onesignal_data", gcmBundle.toString()).putExtra("grp", group));
         notifBuilder.setContentIntent(contentIntent);
         PendingIntent deleteIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseDeleteIntent(notificationId).putExtra("grp", group));
         notifBuilder.setDeleteIntent(deleteIntent);
         notifBuilder.setGroup(group);

         createSummaryNotification(restoring, gcmBundle);
      }
      else {
         PendingIntent contentIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseIntent(notificationId).putExtra("onesignal_data", gcmBundle.toString()));
         notifBuilder.setContentIntent(contentIntent);
         PendingIntent deleteIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseDeleteIntent(notificationId));
         notifBuilder.setDeleteIntent(deleteIntent);
      }

      // Keeps notification from playing sound + vibrating again
      if (restoring)
         removeNotifyOptions(notifBuilder);

      // NotificationManagerCompat does not auto omit the individual notification on the device when using
      //   stacked notifications on Android 4.2 and older
      // The benefits of calling notify for individual notifications in-addition to the summary above it is shows
      //   each notification in a stack on Android Wear and each one is actionable just like the Gmail app does per email.
      if (group == null || Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) {
         NotificationManagerCompat.from(currentContext).notify(notificationId, notifBuilder.build());
      }
   }

   private static void createSummaryNotification(boolean restoring, JSONObject gcmBundle) {
      createSummaryNotification(null, restoring, gcmBundle);
   }

   static void createSummaryNotification(Context inContext,  boolean updateSummary, JSONObject gcmBundle) {
      if (updateSummary && inContext != null)
         setStatics(inContext);

      String group = gcmBundle.optString("grp", null);

      Random random = new Random();
      PendingIntent summaryDeleteIntent = getNewActionPendingIntent(random.nextInt(), getNewBaseDeleteIntent(0).putExtra("summary", group));

      OneSignalDbHelper dbHelper = OneSignalDbHelper.getInstance(currentContext);
      SQLiteDatabase readableDb = dbHelper.getReadableDatabase();

      String[] retColumn = { NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID,
                             NotificationTable.COLUMN_NAME_FULL_DATA,
                             NotificationTable.COLUMN_NAME_IS_SUMMARY,
                             NotificationTable.COLUMN_NAME_TITLE,
                             NotificationTable.COLUMN_NAME_MESSAGE };

      String[] whereArgs = { group };

      Cursor cursor = readableDb.query(
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

      try {
         if (cursor.moveToFirst()) {
            SpannableString spannableString;
            summeryList = new ArrayList<>();

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


      if (summeryList != null && (!updateSummary || summeryList.size() > 1)) {
         int notificationCount = summeryList.size() + (updateSummary ? 0 : 1);

         String summaryMessage = gcmBundle.optString("grp_msg", null);
         if (summaryMessage == null)
            summaryMessage = notificationCount + " new messages";
         else
            summaryMessage = summaryMessage.replace("$[notif_count]", "" + notificationCount);

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

         NotificationCompat.Builder summeryBuilder = getBaseNotificationCompatBuilder(gcmBundle);
         if (updateSummary)
            removeNotifyOptions(summeryBuilder);

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

         // Add the latest notification to the summary
         if (!updateSummary) {
            String line1Title = gcmBundle.optString("title", null);

            if (line1Title == null)
               line1Title = "";
            else
               line1Title += " ";

            String message = gcmBundle.optString("alert");
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
         SQLiteDatabase writableDb = dbHelper.getWritableDatabase();
         writableDb.beginTransaction();

         try {
            ContentValues values = new ContentValues();
            values.put(NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID, summaryNotificationId);
            values.put(NotificationTable.COLUMN_NAME_GROUP_ID, group);
            values.put(NotificationTable.COLUMN_NAME_IS_SUMMARY, 1);
            writableDb.insertOrThrow(NotificationTable.TABLE_NAME, null, values);
            writableDb.setTransactionSuccessful();
         } catch (Exception e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error adding summary notification record! ", e);
         } finally {
            writableDb.endTransaction();
         }

         NotificationCompat.Builder notifBuilder = getBaseNotificationCompatBuilder(gcmBundle);
         if (updateSummary)
            removeNotifyOptions(notifBuilder);

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
   }

   // Keep 'throws Throwable' as 'onesignal_bgimage_notif_layout' may not be available if the app project isn't setup correctly.
   private static void addBackgroundImage(JSONObject gcmBundle, NotificationCompat.Builder notifBuilder) throws Throwable {
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

         customView.setImageViewBitmap(R.id.os_bgimage_notif_bgimage, bg_image);
         notifBuilder.setContent(customView);

         // Remove style to prevent expanding by the user.
         // Will need to create an extended image option and consider the layout.
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

   private static boolean isValidResourceName(String name) {
      return (name != null && !name.matches("^[0-9]"));
   }

   private static Bitmap getLargeIcon(JSONObject gcmBundle) {
      if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB)
         return null;

      Bitmap bitmap = getBitmap(gcmBundle.optString("licon"));
      if (bitmap == null)
         bitmap = getBitmapFromAssetsOrResourceName("ic_onesignal_large_icon_default");

      if (bitmap == null)
         return null;

      // Resize to prevent extra cropping and boarders.
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

   private static Bitmap getBitmap(String name) {
      if (name == null)
         return null;
      if (name.startsWith("http://") || name.startsWith("https://"))
         return getBitmapFromURL(name);

      return getBitmapFromAssetsOrResourceName(name);
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
      int notificationIcon = getResourceIcon(gcmBundle.optString("sicon", null));
      if (notificationIcon != 0)
         return notificationIcon;

      notificationIcon = getDrawableId("ic_stat_onesignal_default");
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

   private static Uri getCustomSound(JSONObject gcmBundle) {
      int soundId;
      String sound = gcmBundle.optString("sound", null);
      
      if (isValidResourceName(sound)) {
         soundId = contextResources.getIdentifier(sound, "raw", packageName);
         if (soundId != 0)
            return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundId);
      }

      soundId = contextResources.getIdentifier("onesignal_default_sound", "raw", packageName);
      if (soundId != 0)
         return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundId);

      return null;
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

         if (customJson.has("a")) {
            JSONObject additionalDataJSON = customJson.getJSONObject("a");
            if (additionalDataJSON.has("actionButtons")) {

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
            }
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   private static void addAlertButtons(Context context, JSONObject gcmBundle, List<String> buttonsLabels, List<String> buttonsIds) {
      try {
         JSONObject customJson = new JSONObject(gcmBundle.optString("custom"));

         if (customJson.has("a")) {
            JSONObject additionalDataJSON = customJson.getJSONObject("a");
            if (additionalDataJSON.has("actionButtons")) {

               JSONArray buttons = additionalDataJSON.optJSONArray("actionButtons");

               for (int i = 0; i < buttons.length(); i++) {
                  JSONObject button = buttons.getJSONObject(i);

                  buttonsLabels.add(button.optString("text"));
                  buttonsIds.add(button.optString("id"));
               }
            }
         }

         if (buttonsLabels.size() < 3) {
            buttonsLabels.add(getResourceString(context, "onesignal_in_app_alert_ok_button_text", "Ok"));
            buttonsIds.add(NotificationBundleProcessor.DEFAULT_ACTION);
         }
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Failed to parse buttons for alert dialog.", t);
      }
   }
}