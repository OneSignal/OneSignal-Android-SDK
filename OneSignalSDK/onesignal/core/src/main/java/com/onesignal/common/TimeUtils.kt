package com.onesignal.common

import android.os.Build
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

object TimeUtils {
    fun getTimeZoneOffset(): Int {
        val timezone = Calendar.getInstance().timeZone
        var offset = timezone.rawOffset
        if (timezone.inDaylightTime(Date())) {
            offset += timezone.dstSavings
        }

        return offset / 1000
    }

    fun getTimeZoneId(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ZoneId.systemDefault().id
        } else {
            TimeZone.getDefault().id
        }
    }
}
