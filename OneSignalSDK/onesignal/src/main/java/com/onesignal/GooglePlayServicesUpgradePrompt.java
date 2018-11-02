package com.onesignal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.google.android.gms.common.GoogleApiAvailability;

import static com.onesignal.OSUtils.getResourceString;

class GooglePlayServicesUpgradePrompt {
   private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9_000;

   static boolean isGMSInstalledAndEnabled() {
      try {
         PackageManager pm = OneSignal.appContext.getPackageManager();
         PackageInfo info = pm.getPackageInfo(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE, PackageManager.GET_META_DATA);

         return info.applicationInfo.enabled;
      } catch (PackageManager.NameNotFoundException e) {}

      return false;
   }

   private static boolean isGooglePlayStoreInstalled() {
      try {
         PackageManager pm = OneSignal.appContext.getPackageManager();
         PackageInfo info = pm.getPackageInfo(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE, PackageManager.GET_META_DATA);
         String label = (String) info.applicationInfo.loadLabel(pm);
         return (label != null && !label.equals("Market"));
      } catch (Throwable e) {}

      return false;
   }

   static void ShowUpdateGPSDialog() {
      if (isGMSInstalledAndEnabled() || !isGooglePlayStoreInstalled())
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
            final Activity activity = ActivityLifecycleHandler.curActivity;
            if (activity == null || OneSignal.mInitBuilder.mDisableGmsMissingPrompt)
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
         apiAvailability.getErrorResolutionPendingIntent(
            activity,
            resultCode,
            PLAY_SERVICES_RESOLUTION_REQUEST
         ).send();
      } catch (PendingIntent.CanceledException e) {
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

}
