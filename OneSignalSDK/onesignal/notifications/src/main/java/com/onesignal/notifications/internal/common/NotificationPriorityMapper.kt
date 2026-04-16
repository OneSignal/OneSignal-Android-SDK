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
    private const val THRESHOLD_MAX = 9
    private const val THRESHOLD_HIGH = 7
    private const val THRESHOLD_DEFAULT = 5
    private const val THRESHOLD_LOW = 3
    private const val THRESHOLD_MIN = 1

    fun isHighPriority(osPriority: Int): Boolean = osPriority >= THRESHOLD_MAX

    fun toAndroidPriority(osPriority: Int): Int =
        when {
            osPriority >= THRESHOLD_MAX -> NotificationCompat.PRIORITY_MAX
            osPriority >= THRESHOLD_HIGH -> NotificationCompat.PRIORITY_HIGH
            osPriority >= THRESHOLD_DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
            osPriority >= THRESHOLD_LOW -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_MIN
        }

    fun toAndroidImportance(osPriority: Int): Int =
        when {
            osPriority >= THRESHOLD_MAX -> NotificationManagerCompat.IMPORTANCE_MAX
            osPriority >= THRESHOLD_HIGH -> NotificationManagerCompat.IMPORTANCE_HIGH
            osPriority >= THRESHOLD_DEFAULT -> NotificationManagerCompat.IMPORTANCE_DEFAULT
            osPriority >= THRESHOLD_LOW -> NotificationManagerCompat.IMPORTANCE_LOW
            osPriority >= THRESHOLD_MIN -> NotificationManagerCompat.IMPORTANCE_MIN
            else -> NotificationManagerCompat.IMPORTANCE_NONE
        }
}
