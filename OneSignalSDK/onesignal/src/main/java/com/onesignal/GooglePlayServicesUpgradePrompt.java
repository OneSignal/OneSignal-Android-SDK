package com.onesignal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.pm.PackageManager;

import com.google.android.gms.common.GoogleApiAvailability;

import static com.onesignal.OSUtils.getResourceString;

class GooglePlayServicesUpgradePrompt {
   private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9_000;
   private static boolean isGooglePlayStoreInstalled() {
      GetPackageInfoResult result =
          PackageInfoHelper.Companion.getInfo(
              OneSignal.appContext,
              GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE,
              PackageManager.GET_META_DATA
          );
      if (!result.getSuccessful()) {
         return false;
      }
      if (result.getPackageInfo() == null) {
         return false;
      }

      PackageManager pm = OneSignal.appContext.getPackageManager();
      String label = (String) result.getPackageInfo().applicationInfo.loadLabel(pm);
      return !label.equals("Market");
   }

   static void showUpdateGPSDialog() {
      if (!OSUtils.isAndroidDeviceType())
         return;

      if (!isGooglePlayStoreInstalled() || OneSignal.getDisableGMSMissingPrompt())
         return;

      boolean userSelectedSkip =
         OneSignalPrefs.getBool(
            OneSignalPrefs.PREFS_ONESIGNAL,
            OneSignalPrefs.PREFS_GT_DO_NOT_SHOW_MISSING_GPS,
            false
         );
      if (userSelectedSkip)
         return;

      OSUtils.runOnMainUIThread(new Runnable() {
         @Override
         public void run() {
            final Activity activity = OneSignal.getCurrentActivity();
            if (activity == null)
               return;

            // Load resource strings so a developer can customize this dialog
            String alertBodyText = getResourceString(activity, "onesignal_gms_missing_alert_text", "To receive push notifications please press 'Update' to enable 'Google Play services'.");
            String alertButtonUpdate = getResourceString(activity, "onesignal_gms_missing_alert_button_update", "Update");
            String alertButtonSkip = getResourceString(activity, "onesignal_gms_missing_alert_button_skip", "Skip");
            String alertButtonClose = getResourceString(activity, "onesignal_gms_missing_alert_button_close", "Close");

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setMessage(alertBodyText).setPositiveButton(alertButtonUpdate, new DialogInterface.OnClickListener() {
               @Override
               public void onClick(DialogInterface dialog, int which) {
                  OpenPlayStoreToApp(activity);
               }
            }).setNegativeButton(alertButtonSkip, new DialogInterface.OnClickListener() {
               @Override
               public void onClick(DialogInterface dialog, int which) {
                  OneSignalPrefs.saveBool(OneSignalPrefs.PREFS_ONESIGNAL,
                     OneSignalPrefs.PREFS_GT_DO_NOT_SHOW_MISSING_GPS,true);

               }
            }).setNeutralButton(alertButtonClose, null).create().show();
         }
      });
   }

   // Take the user to the Google Play store to update or enable the Google Play Services app
   private static void OpenPlayStoreToApp(Activity activity) {
      try {
         GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
         int resultCode = apiAvailability.isGooglePlayServicesAvailable(OneSignal.appContext);
         // Send the Intent to trigger opening the store
         PendingIntent pendingIntent =
             apiAvailability.getErrorResolutionPendingIntent(
                 activity,
                 resultCode,
                 PLAY_SERVICES_RESOLUTION_REQUEST
             );
         if (pendingIntent != null)
            pendingIntent.send();
      } catch (PendingIntent.CanceledException e) {
         e.printStackTrace();
      }
   }

}
