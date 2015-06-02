package com.onesignal.example;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Created by Josh on 5/18/2015.
 */
public class TestNotificationOpenedReceiver extends BroadcastReceiver {

    // You may consider adding a wake lock here if you need to make sure the devices doesn't go to sleep while processing.
    // We recommend starting a service of your own here if your doing any async calls or doing any heavy processing.
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle dataBundle = intent.getBundleExtra("data");
        Log.i("OneSignalExample", "Notification content: ");

    }
}