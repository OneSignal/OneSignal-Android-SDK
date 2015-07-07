package com.onesignal.example;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

public class OneSignalBackgroundDataReceiver extends WakefulBroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        Bundle dataBundle = intent.getBundleExtra("data");

        try {
            //Log.i("OneSignalExample", "NotificationTable content: " + dataBundle.getString("alert"));
            Log.i("OneSignalExample", "NotificationTable title: " + dataBundle.getString("title"));
            Log.i("OneSignalExample", "Is Your App Active: " + dataBundle.getBoolean("isActive"));
            Log.i("OneSignalExample", "data addt: " + dataBundle.getString("custom"));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
