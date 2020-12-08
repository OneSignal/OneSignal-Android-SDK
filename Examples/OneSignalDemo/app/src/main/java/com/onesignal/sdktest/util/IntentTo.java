package com.onesignal.sdktest.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.onesignal.sdktest.R;
import com.onesignal.sdktest.activity.MainActivity;

public class IntentTo {

    private Context context;


    public IntentTo(Context context) {
        this.context = context;
    }

    public void mainActivity() {
        Intent mainActivityIntent = new Intent(context, MainActivity.class);
        ComponentName componentName = mainActivityIntent.getComponent();
        mainActivityIntent = Intent.makeRestartActivityTask(componentName);
        context.startActivity(mainActivityIntent);
        ((Activity) context).overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    public void notificationPermissions() {
        Intent notificationPermissionIntent = new Intent();
        notificationPermissionIntent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8 and above
            notificationPermissionIntent.putExtra("android.provider.extra.APP_PACKAGE", context.getPackageName());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Android 5-7
            notificationPermissionIntent.putExtra("app_package", context.getPackageName());
            notificationPermissionIntent.putExtra("app_uid", context.getApplicationInfo().uid);
        }
        context.startActivity(notificationPermissionIntent);
        ((Activity) context).overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    public void resetApplication() {
        Intent resetApplicationIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (resetApplicationIntent != null) {
            resetApplicationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        context.startActivity(resetApplicationIntent);
        ((Activity) context).overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

}
