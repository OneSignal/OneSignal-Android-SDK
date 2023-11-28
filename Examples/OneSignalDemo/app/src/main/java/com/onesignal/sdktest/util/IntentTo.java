package com.onesignal.sdktest.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.onesignal.sdktest.R;
import com.onesignal.sdktest.activity.MainActivity;

public class IntentTo {

    private final Context context;

    public IntentTo(Context context) {
        this.context = context;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void mainActivity() {
        Intent mainActivityIntent = new Intent(context, MainActivity.class);
        ComponentName componentName = mainActivityIntent.getComponent();
        mainActivityIntent = Intent.makeRestartActivityTask(componentName);
        context.startActivity(mainActivityIntent);
        ((Activity) context).overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
