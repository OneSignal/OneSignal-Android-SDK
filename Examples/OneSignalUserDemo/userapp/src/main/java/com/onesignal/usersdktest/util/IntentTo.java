package com.onesignal.usersdktest.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.onesignal.usersdktest.R;
import com.onesignal.usersdktest.activity.MainActivity;

public class IntentTo {

    private Context context;

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
