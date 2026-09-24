package com.onesignal.common

import com.onesignal.debug.internal.logging.Logging

fun rejectNullOrEmpty(
    value: String?,
    api: String,
): Boolean {
    if (!value.isNullOrEmpty()) return false
    Logging.error("OneSignal: $api is required")
    return true
}

fun rejectNullOrEmptyAny(
    values: Collection<String>,
    api: String,
): Boolean {
    for (value in values) {
        if (rejectNullOrEmpty(value, api)) return true
    }
    return false
}

fun rejectNullOrEmptyEntries(
    values: Map<String, String>,
    api: String,
    allowEmptyValue: Boolean,
): Boolean {
    for ((key, item) in values) {
        if (rejectNullOrEmpty(key, "$api: key")) return true
        val raw = item as String?
        if (allowEmptyValue) {
            if (raw != null) continue
            Logging.error("OneSignal: $api: value is required")
            return true
        }
        if (rejectNullOrEmpty(raw, "$api: value")) return true
    }
    return false
}
