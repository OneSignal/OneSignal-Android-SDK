package com.onesignal.common

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import com.onesignal.common.ViewUtils.pxToDp

/** Safe-area inset math. Every array is ordered top, bottom, right, left. */
internal object SystemBarInsets {
    private const val TOP = 0
    private const val BOTTOM = 1
    private const val RIGHT = 2
    private const val LEFT = 3

    // FLAG_FULLSCREEN hides the status bar without setting SYSTEM_UI_FLAG_FULLSCREEN.
    @Suppress("DEPRECATION")
    fun systemUiVisibilityForInsets(
        systemUiVisibility: Int,
        windowFlags: Int,
    ): Int {
        if (windowFlags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0) {
            return systemUiVisibility or View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        return systemUiVisibility
    }

    fun cutoutAndStatusBarInsetsDp(
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
        val px =
            pxInsetsHidingSystemBars(
                intArrayOf(
                    windowInsets.stableInsetTop,
                    windowInsets.stableInsetBottom,
                    windowInsets.stableInsetRight,
                    windowInsets.stableInsetLeft,
                ),
                statusHidden = systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0,
                navigationHidden = systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0,
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            windowInsets.displayCutout?.let { cutout ->
                px[TOP] = maxOf(px[TOP], cutout.safeInsetTop)
                px[BOTTOM] = maxOf(px[BOTTOM], cutout.safeInsetBottom)
                px[RIGHT] = maxOf(px[RIGHT], cutout.safeInsetRight)
                px[LEFT] = maxOf(px[LEFT], cutout.safeInsetLeft)
            }
        }
        return IntArray(px.size) { pxToDp(px[it]) }
    }

    // A hidden bar is not on screen, so it must not pad the message.
    fun pxInsetsHidingSystemBars(
        pxInsets: IntArray,
        statusHidden: Boolean,
        navigationHidden: Boolean,
    ): IntArray {
        val visible = pxInsets.copyOf()
        if (statusHidden) {
            visible[TOP] = 0
        }
        if (navigationHidden) {
            val navOnLeft = visible[LEFT] > visible[RIGHT]
            val navOnRight = visible[RIGHT] > visible[LEFT]
            visible[BOTTOM] = 0
            if (navOnLeft) {
                visible[LEFT] = 0
            }
            if (navOnRight) {
                visible[RIGHT] = 0
            }
        }
        return visible
    }

    fun legacyCutoutAndStatusBarInsets(activity: Activity): IntArray {
        val frame = Rect()
        activity.window.decorView.getWindowVisibleDisplayFrame(frame)
        val contentView = activity.window.findViewById<View>(Window.ID_ANDROID_CONTENT)
        var rightInset = 0
        var leftInset = 0
        val topInset = pxToDp(frame.top - contentView.top)
        val bottomInset = pxToDp(contentView.bottom - frame.bottom)
        // This fallback only reads side cutouts on API 29.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            val cutout = activity.windowManager.defaultDisplay.cutout
            if (cutout != null) {
                rightInset = pxToDp(cutout.safeInsetRight)
                leftInset = pxToDp(cutout.safeInsetLeft)
            }
        }
        return intArrayOf(
            topInset,
            bottomInset,
            rightInset,
            leftInset,
        )
    }
}
