package com.onesignal.location.internal.common

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationListener
import com.huawei.hms.location.LocationCallback
import com.onesignal.common.AndroidUtils

internal object LocationUtils {
    fun hasGMSLocationLibrary(): Boolean {
        return try {
            AndroidUtils.opaqueHasClass(LocationListener::class.java)
        } catch (e: NoClassDefFoundError) {
            false
        }
    }

    fun hasHMSLocationLibrary(): Boolean {
        return try {
            AndroidUtils.opaqueHasClass(LocationCallback::class.java)
        } catch (e: NoClassDefFoundError) {
            false
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        return (
            ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_FINE_LOCATION") === PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_COARSE_LOCATION") === PackageManager.PERMISSION_GRANTED
            )
    }
}
