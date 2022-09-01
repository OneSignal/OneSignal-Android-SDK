package com.onesignal.core.internal.common

import android.annotation.TargetApi
import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.Window
import kotlin.math.roundToInt

object ViewUtils {
    fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().displayMetrics.density).toInt()
    }

    // Due to differences in accounting for keyboard, navigation bar, and status bar between
    //   Android versions have different implementation here
    fun getWindowHeight(activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getWindowHeightAPI23Plus(activity) else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) getWindowHeightLollipop(
            activity
        ) else getDisplaySizeY(activity)
    }

    private fun getDisplaySizeY(activity: Activity): Int {
        val point = Point()
        activity.windowManager.defaultDisplay.getSize(point)
        return point.y
    }

    // Requirement: Ensure DecorView is ready by using OSViewUtils.decorViewReady
    @TargetApi(Build.VERSION_CODES.M)
    private fun getWindowHeightAPI23Plus(activity: Activity): Int {
        val decorView = activity.window.decorView
        // Use use stable heights as SystemWindowInset subtracts the keyboard
        val windowInsets = decorView.rootWindowInsets ?: return decorView.height
        return decorView.height -
            windowInsets.stableInsetBottom -
            windowInsets.stableInsetTop
    }

    private fun getWindowHeightLollipop(activity: Activity): Int {
        // getDisplaySizeY - works correctly expect for landscape due to a bug.
        return if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) getWindowVisibleDisplayFrame(
            activity
        ).height() else getDisplaySizeY(activity)
        //  getWindowVisibleDisplayFrame - Doesn't work for portrait as it subtracts the keyboard height.
    }

    private fun getWindowVisibleDisplayFrame(activity: Activity): Rect {
        val rect = Rect()
        activity.window.decorView.getWindowVisibleDisplayFrame(rect)
        return rect
    }

    fun getCutoutAndStatusBarInsets(activity: Activity): IntArray {
        val frame = getWindowVisibleDisplayFrame(activity)
        val contentView = activity.window.findViewById<View>(Window.ID_ANDROID_CONTENT)
        var rightInset = 0f
        var leftInset = 0f
        val topInset = (frame.top - contentView.top) / Resources.getSystem().displayMetrics.density
        val bottomInset =
            (contentView.bottom - frame.bottom) / Resources.getSystem().displayMetrics.density
        // API 29 is the only version where the IAM bleeds under cutouts in immersize mode
        // All other versions will not need left and right insets.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            val cutout = activity.windowManager.defaultDisplay.cutout
            if (cutout != null) {
                rightInset = cutout.safeInsetRight / Resources.getSystem().displayMetrics.density
                leftInset = cutout.safeInsetLeft / Resources.getSystem().displayMetrics.density
            }
        }
        return intArrayOf(
            topInset.roundToInt(),
            bottomInset.roundToInt(),
            rightInset.roundToInt(),
            leftInset.roundToInt()
        )
    }

    fun getFullbleedWindowWidth(activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val decorView = activity.window.decorView
            decorView.width
        } else {
            getWindowWidth(activity)
        }
    }

    fun getWindowWidth(activity: Activity): Int {
        return getWindowVisibleDisplayFrame(activity).width()
    }
}
