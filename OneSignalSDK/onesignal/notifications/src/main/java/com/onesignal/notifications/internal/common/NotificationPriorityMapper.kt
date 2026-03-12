package com.onesignal.notifications.internal.common

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Single source of truth for mapping OneSignal payload priority (1–10) to
 * Android notification priority and channel importance levels.
 *
 * Threshold table (OneSignal pri → Android level):
 *   9–10 → MAX
 *   7–8  → HIGH
 *   5–6  → DEFAULT
 *   3–4  → LOW
 *   1–2  → MIN
 *   0    → NONE (importance only)
 */
internal object NotificationPriorityMapper {
    private const val HIGH_PRIORITY_THRESHOLD = 9

    fun isHighPriority(osPriority: Int): Boolean = osPriority >= HIGH_PRIORITY_THRESHOLD

    fun toAndroidPriority(osPriority: Int): Int {
        if (osPriority >= HIGH_PRIORITY_THRESHOLD) return NotificationCompat.PRIORITY_MAX
        if (osPriority >= 7) return NotificationCompat.PRIORITY_HIGH
        if (osPriority >= 5) return NotificationCompat.PRIORITY_DEFAULT
        return if (osPriority >= 3) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN
    }

    fun toAndroidImportance(osPriority: Int): Int {
        if (osPriority >= HIGH_PRIORITY_THRESHOLD) return NotificationManagerCompat.IMPORTANCE_MAX
        if (osPriority >= 7) return NotificationManagerCompat.IMPORTANCE_HIGH
        if (osPriority >= 5) return NotificationManagerCompat.IMPORTANCE_DEFAULT
        if (osPriority >= 3) return NotificationManagerCompat.IMPORTANCE_LOW
        return if (osPriority >= 1) NotificationManagerCompat.IMPORTANCE_MIN else NotificationManagerCompat.IMPORTANCE_NONE
    }
}
