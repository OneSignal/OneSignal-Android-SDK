package com.onesignal.common

import android.annotation.TargetApi
import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import kotlin.math.roundToInt

object ViewUtils {
    fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().displayMetrics.density).toInt()
    }

    // Due to differences in accounting for keyboard, navigation bar, and status bar between
    //   Android versions have different implementation here
    fun getWindowHeight(activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindowHeightAPI23Plus(activity)
        } else {
            getWindowHeightLollipop(activity)
        }
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
        return if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            getWindowVisibleDisplayFrame(
                activity,
            ).height()
        } else {
            getDisplaySizeY(activity)
        }
        //  getWindowVisibleDisplayFrame - Doesn't work for portrait as it subtracts the keyboard height.
    }

    private fun getWindowVisibleDisplayFrame(activity: Activity): Rect {
        val rect = Rect()
        activity.window.decorView.getWindowVisibleDisplayFrame(rect)
        return rect
    }

    fun getCutoutAndStatusBarInsets(activity: Activity): IntArray {
        val decorView = activity.window.decorView
        val windowInsets = decorView.rootWindowInsets
        if (windowInsets != null) {
            @Suppress("DEPRECATION")
            return cutoutAndStatusBarInsetsDp(windowInsets, decorView.systemUiVisibility)
        }
        return legacyCutoutAndStatusBarInsets(activity)
    }

    internal fun cutoutAndStatusBarInsetsDp(
        windowInsets: WindowInsets,
        systemUiVisibility: Int = 0,
    ): IntArray {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets =
                windowInsets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
            return intArrayOf(
                pxToDp(insets.top),
                pxToDp(insets.bottom),
                pxToDp(insets.right),
                pxToDp(insets.left),
            )
        }
        return stableInsetsToDp(windowInsets, systemUiVisibility)
    }

    // stableInset stops at the nav bar. systemWindowInsetBottom also includes the keyboard.
    @Suppress("DEPRECATION")
    private fun stableInsetsToDp(
        windowInsets: WindowInsets,
        systemUiVisibility: Int,
    ): IntArray {
        val visibleBars =
            pxInsetsHidingSystemBars(
                windowInsets.stableInsetTop,
                windowInsets.stableInsetBottom,
                windowInsets.stableInsetRight,
                windowInsets.stableInsetLeft,
                systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0,
                systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0,
            )
        var top = visibleBars[0]
        var bottom = visibleBars[1]
        var right = visibleBars[2]
        var left = visibleBars[3]
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            windowInsets.displayCutout?.let { cutout ->
                top = maxOf(top, cutout.safeInsetTop)
                bottom = maxOf(bottom, cutout.safeInsetBottom)
                right = maxOf(right, cutout.safeInsetRight)
                left = maxOf(left, cutout.safeInsetLeft)
            }
        }
        return intArrayOf(pxToDp(top), pxToDp(bottom), pxToDp(right), pxToDp(left))
    }

    // A hidden bar is not on screen, so it must not pad the message.
    internal fun pxInsetsHidingSystemBars(
        top: Int,
        bottom: Int,
        right: Int,
        left: Int,
        statusHidden: Boolean,
        navigationHidden: Boolean,
    ): IntArray {
        var topPx = top
        var bottomPx = bottom
        var rightPx = right
        var leftPx = left
        if (statusHidden) {
            topPx = 0
        }
        if (navigationHidden) {
            val navOnLeft = leftPx > rightPx
            val navOnRight = rightPx > leftPx
            bottomPx = 0
            if (navOnLeft) {
                leftPx = 0
            }
            if (navOnRight) {
                rightPx = 0
            }
        }
        return intArrayOf(topPx, bottomPx, rightPx, leftPx)
    }

    private fun pxToDp(px: Int): Int {
        return (px / Resources.getSystem().displayMetrics.density).roundToInt()
    }

    private fun legacyCutoutAndStatusBarInsets(activity: Activity): IntArray {
        val frame = getWindowVisibleDisplayFrame(activity)
        val contentView = activity.window.findViewById<View>(Window.ID_ANDROID_CONTENT)
        var rightInset = 0f
        var leftInset = 0f
        val topInset = (frame.top - contentView.top) / Resources.getSystem().displayMetrics.density
        val bottomInset =
            (contentView.bottom - frame.bottom) / Resources.getSystem().displayMetrics.density
        // This fallback only reads side cutouts on API 29.
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
            leftInset.roundToInt(),
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
