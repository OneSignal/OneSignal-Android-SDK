package com.onesignal;

import android.app.Activity;
import android.content.res.Configuration;
import android.support.annotation.NonNull;

class OSViewUtils {

    static boolean isScreenRotated(@NonNull Activity activity, int previousOrientation) {
        int currentOrientation = activity.getResources().getConfiguration().orientation;
        return previousOrientation == Configuration.ORIENTATION_LANDSCAPE && currentOrientation == Configuration.ORIENTATION_PORTRAIT ||
                previousOrientation == Configuration.ORIENTATION_PORTRAIT && currentOrientation == Configuration.ORIENTATION_LANDSCAPE;
    }
}