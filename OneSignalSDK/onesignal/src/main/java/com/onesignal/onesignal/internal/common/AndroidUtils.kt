package com.onesignal.onesignal.internal.common

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Rect
import android.os.Bundle
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.View
import com.onesignal.onesignal.logging.Logging
import java.lang.ref.WeakReference
import java.util.*

object AndroidUtils {
    private val MARGIN_ERROR_PX_SIZE = dpToPx(24)

    fun getRandomDelay(minDelay: Int, maxDelay: Int): Int {
        return Random().nextInt(maxDelay + 1 - minDelay) + minDelay
    }

    fun isStringNotEmpty(body: String?): Boolean {
        return !TextUtils.isEmpty(body)
    }

    /**
     * Check if the keyboard is currently being shown.
     * Does not work for cases when keyboard is full screen.
     */
    fun isKeyboardUp(activityWeakReference: WeakReference<Activity?>): Boolean {
        val metrics = DisplayMetrics()
        val visibleBounds = Rect()
        var view: View? = null
        var isOpen = false
        if (activityWeakReference.get() != null) {
            val window = activityWeakReference.get()!!.window
            view = window.decorView
            view.getWindowVisibleDisplayFrame(visibleBounds)
            window.windowManager.defaultDisplay.getMetrics(metrics)
        }
        if (view != null) {
            val heightDiff = metrics.heightPixels - visibleBounds.bottom
            isOpen = heightDiff > MARGIN_ERROR_PX_SIZE
        }
        return isOpen
    }

    fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().displayMetrics.density).toInt()
    }

    fun sleep(ms: Int) {
        try {
            Thread.sleep(ms.toLong())
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun hasConfigChangeFlag(activity: Activity, configChangeFlag: Int): Boolean {
        var hasFlag = false
        try {
            val configChanges =
                activity.packageManager.getActivityInfo(activity.componentName, 0).configChanges
            val flagInt = configChanges and configChangeFlag
            hasFlag = flagInt != 0
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return hasFlag
    }

    fun getManifestMeta(context: Context, metaName: String?): String? {
        val bundle = getManifestMetaBundle(context)
        return bundle?.getString(metaName)
    }

    fun getManifestMetaBundle(context: Context): Bundle? {
        val ai: ApplicationInfo
        try {
            ai = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            return ai.metaData
        } catch (e: PackageManager.NameNotFoundException) {
            Logging.error("Manifest application info not found", e)
        }
        return null
    }
}