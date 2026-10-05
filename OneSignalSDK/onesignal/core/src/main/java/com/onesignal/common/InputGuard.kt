package com.onesignal.common

import com.onesignal.debug.internal.logging.Logging

internal fun hasNullByte(value: String?): Boolean = value != null && '\u0000' in value

fun isMissing(
    value: String?,
    api: String,
): Boolean {
    // NUL cannot be stored in a text column, so it never counts as a usable value.
    if (hasNullByte(value)) {
        Logging.error("[OneSignal] $api contains a null byte")
        return true
    }
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
