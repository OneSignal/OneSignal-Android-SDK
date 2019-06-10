package com.onesignal;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;

import java.lang.ref.WeakReference;

class OSViewUtils {

    private static final int MARGIN_ERROR_PX_SIZE = OSUtils.dpToPx(24);
    private static final double HDPI_DENSITY = 1.5;

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

    /**
     * Get the percentage scale for a view
     *
     * @param picWidth the width of the view to Scale
     * @return 0 for default scale, % if the view does not scale for the current density
     */
    static int getScale(@Nullable Activity activity, int picWidth) {
        if (activity != null) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            return displayMetrics.density > HDPI_DENSITY ? 0 : (int) (picWidth * displayMetrics.density * 2) / 10;
        }
        return 0;
    }
}