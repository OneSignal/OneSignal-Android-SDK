package com.onesignal.common

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.net.ConnectivityManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowInsets
import androidx.core.view.WindowInsetsCompat
import java.lang.ref.WeakReference

object DeviceUtils {
    private val MARGIN_ERROR_PX_SIZE = ViewUtils.dpToPx(24)

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
            isOpen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val imeInsets = view.rootWindowInsets.getInsets(WindowInsetsCompat.Type.ime())
                // ensure input method size is 0
                !(imeInsets.left == 0 && imeInsets.top == 0 && imeInsets.right == 0 && imeInsets.bottom == 0)
            } else {
                val heightDiff = metrics.heightPixels - visibleBounds.bottom
                heightDiff > MARGIN_ERROR_PX_SIZE
            }
        }
        return isOpen
    }

    fun getNetType(appContext: Context): Int? {
        val cm =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netInfo = cm.activeNetworkInfo
        if (netInfo != null) {
            val networkType = netInfo.type
            return if (networkType == ConnectivityManager.TYPE_WIFI || networkType == ConnectivityManager.TYPE_ETHERNET) 0 else 1
        }
        return null
    }

    fun getCarrierName(appContext: Context): String? {
        return try {
            val manager = appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            // May throw even though it's not in noted in the Android docs.
            // Issue #427
            val carrierName = manager.networkOperatorName
            if ("" == carrierName) null else carrierName
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }
}
