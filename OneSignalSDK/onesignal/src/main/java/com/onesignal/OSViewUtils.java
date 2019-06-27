package com.onesignal;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;

import java.lang.ref.WeakReference;

class OSViewUtils {

    private static final int MARGIN_ERROR_PX_SIZE = dpToPx(24);

    /**
     * Check if the keyboard is currently being shown.
     * Does not work for cases when keyboard is full screen.
     */
    static boolean isKeyboardUp(WeakReference<Activity> activityWeakReference) {
        DisplayMetrics metrics = new DisplayMetrics();
        Rect visibleBounds = new Rect();
        View view = null;
        boolean isOpen = false;

        if (activityWeakReference.get() != null) {
            Window window = activityWeakReference.get().getWindow();
            view = window.getDecorView();
            view.getWindowVisibleDisplayFrame(visibleBounds);
            window.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        }

        if (view != null) {
            int heightDiff = metrics.heightPixels - visibleBounds.bottom;
            isOpen = heightDiff > MARGIN_ERROR_PX_SIZE;
        }

        return isOpen;
    }

    static boolean isScreenRotated(@NonNull Activity activity, int previousOrientation) {
        int currentOrientation = activity.getResources().getConfiguration().orientation;
        return previousOrientation == Configuration.ORIENTATION_LANDSCAPE && currentOrientation == Configuration.ORIENTATION_PORTRAIT ||
                previousOrientation == Configuration.ORIENTATION_PORTRAIT && currentOrientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    static @NonNull Rect getUsableWindowRect(@NonNull Activity activity) {
       Rect rect = new Rect();
       activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
       return rect;
    }

    static int dpToPx(int dp) {
       return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }
}