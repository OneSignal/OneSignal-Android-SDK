package com.onesignal;

import android.content.Context;
import android.widget.Toast;

public class Toaster {

    static public void makeToast(Context context, String message, int time) {
        Toast.makeText(context, message, time).show();
    }

}
