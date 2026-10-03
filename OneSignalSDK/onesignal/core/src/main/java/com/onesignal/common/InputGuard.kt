package com.onesignal.common

import com.onesignal.debug.internal.logging.Logging

fun isMissing(
    value: String?,
    api: String,
): Boolean {
    if (!value.isNullOrEmpty()) return false
    Logging.error("[OneSignal] $api is required")
    return true
}

fun isMissingAny(
    values: Collection<String>,
    api: String,
): Boolean {
    for (value in values) {
        if (isMissing(value, api)) return true
    }
    return false
}

fun hasMissingEntries(
    values: Map<String, String>,
    api: String,
    allowEmptyValue: Boolean,
): Boolean {
    for ((key, item) in values) {
        if (isMissing(key, "$api: key")) return true
        val raw = item as String?
        if (allowEmptyValue) {
            if (raw != null) continue
            Logging.error("[OneSignal] $api: value is required")
            return true
        }
        if (isMissing(raw, "$api: value")) return true
    }
    return false
}
