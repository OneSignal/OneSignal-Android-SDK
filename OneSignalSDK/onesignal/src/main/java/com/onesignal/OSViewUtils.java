package com.onesignal;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import androidx.annotation.NonNull;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

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

    // Ensures the root decor view is ready by checking the following;
    //   1. Is fully attach to the root window and insets are available
    //   2. Ensure if any Activities are changed while waiting we use the updated one
    static void decorViewReady(@NonNull Activity activity, final @NonNull Runnable runnable) {
        final String listenerKey = "decorViewReady:" + runnable;
        activity.getWindow().getDecorView().post(new Runnable() {
            @Override
            public void run() {
                final ActivityLifecycleHandler activityLifecycleHandler = ActivityLifecycleListener.getActivityLifecycleHandler();
                if (activityLifecycleHandler!= null) {
                    activityLifecycleHandler.addActivityAvailableListener(listenerKey, new ActivityLifecycleHandler.ActivityAvailableListener() {
                        @Override
                        void available(@NonNull Activity currentActivity) {
                            activityLifecycleHandler.removeActivityAvailableListener(listenerKey);
                            if (isActivityFullyReady(currentActivity))
                                runnable.run();
                            else
                                decorViewReady(currentActivity, runnable);
                        }
                    });
                }
            }
        });
    }

    private static @NonNull Rect getWindowVisibleDisplayFrame(@NonNull Activity activity) {
       Rect rect = new Rect();
       activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
       return rect;
    }

    static int[] getWindowInsets(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

        }
        Rect frame = getWindowVisibleDisplayFrame(activity);
        View contentView = activity.getWindow().findViewById(Window.ID_ANDROID_CONTENT);
        float topInset = (frame.top - contentView.getTop()) / Resources.getSystem().getDisplayMetrics().density;
        float bottomInset = (contentView.getBottom() - frame.bottom) / Resources.getSystem().getDisplayMetrics().density;;
        float rightInset = (frame.right - contentView.getRight()) / Resources.getSystem().getDisplayMetrics().density;;
        float leftInset = (contentView.getLeft() - frame.left) / Resources.getSystem().getDisplayMetrics().density;;
        return new int[]{Math.round(topInset), Math.round(bottomInset), Math.round(rightInset), Math.round(leftInset)};
    }
    /*
    Not using this method for now because the bottom value is not correct (we don't cover the bottom buttons)
     */
    // Requirement: Ensure DecorView is ready by using OSViewUtils.decorViewReady
    @TargetApi(Build.VERSION_CODES.P)
    private static int[] getWindowInsetsAPI28Plus(@NonNull Activity activity) {
        View decorView = activity.getWindow().getDecorView();
        // Use use stable heights as SystemWindowInset subtracts the keyboard
        WindowInsets windowInsets = decorView.getRootWindowInsets();
        //if windowInsets.getDisplayCutout().
        return new int[] {windowInsets.getStableInsetTop(), windowInsets.getStableInsetBottom(),
                windowInsets.getStableInsetRight(), windowInsets.getStableInsetLeft()};
    }

    static int getWindowWidth(@NonNull Activity activity) {
        return getWindowVisibleDisplayFrame(activity).width();
    }

    // Due to differences in accounting for keyboard, navigation bar, and status bar between
    //   Android versions have different implementation here
    static int getWindowHeight(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            return getWindowHeightAPI23Plus(activity);
        else  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            return getWindowHeightLollipop(activity);
        else
            return getDisplaySizeY(activity);
    }

    // Requirement: Ensure DecorView is ready by using OSViewUtils.decorViewReady
    @TargetApi(Build.VERSION_CODES.M)
    private static int getWindowHeightAPI23Plus(@NonNull Activity activity) {
        View decorView = activity.getWindow().getDecorView();
        // Use use stable heights as SystemWindowInset subtracts the keyboard
        WindowInsets windowInsets = decorView.getRootWindowInsets();
        if (windowInsets == null)
            return decorView.getHeight();

        return decorView.getHeight() -
               windowInsets.getStableInsetBottom();
//               windowInsets.getStableInsetTop();
    }

    private static int getWindowHeightLollipop(@NonNull Activity activity) {
        // getDisplaySizeY - works correctly expect for landscape due to a bug.
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            return getWindowVisibleDisplayFrame(activity).height();
        //  getWindowVisibleDisplayFrame - Doesn't work for portrait as it subtracts the keyboard height.

        return getDisplaySizeY(activity);
    }

    private static int getDisplaySizeY(@NonNull Activity activity) {
        Point point = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(point);
        return point.y;
    }

    static int dpToPx(int dp) {
       return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    // Ensures the Activity is fully ready by;
    //   1. Ensure it is attached to a top-level Window by checking if it has an IBinder
    //   2. If Android M or higher ensure WindowInsets exists on the root window also
    static boolean isActivityFullyReady(@NonNull Activity activity) {
        boolean hasToken = activity.getWindow().getDecorView().getApplicationWindowToken() != null;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return hasToken;

        View decorView = activity.getWindow().getDecorView();
        boolean insetsAttached = decorView.getRootWindowInsets() != null;

        return hasToken && insetsAttached;
    }
}