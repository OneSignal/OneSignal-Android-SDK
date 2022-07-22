package com.onesignal.onesignal.core.internal.common

import android.content.Context
import android.net.ConnectivityManager
import android.telephony.TelephonyManager

object DeviceUtils {

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