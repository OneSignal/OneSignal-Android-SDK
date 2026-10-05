package com.onesignal.common

import com.onesignal.debug.internal.logging.Logging

internal fun hasNullByte(value: String?): Boolean = value != null && '\u0000' in value

/**
 * Returns true and logs an error naming [api] when [value] is null, empty, or contains a null byte.
 */
fun isMissing(
    value: String?,
    api: String,
): Boolean {
    // NUL cannot be stored in a text column, so it never counts as a usable value.
    val error =
        when {
            hasNullByte(value) -> "$api contains a null byte"
            value.isNullOrEmpty() -> "$api is required"
            else -> null
        }
    error?.let { Logging.error(it) }
    return error != null
}

/**
 * Returns true when any of [values] is missing, per [isMissing].
 */
fun isMissingAny(
    values: Collection<String>,
    api: String,
): Boolean = values.any { isMissing(it, api) }

/**
 * Returns true when any key in [values] is missing, or any value is missing. With [allowEmptyValue],
 * only null values count as missing.
 */
fun hasMissingEntries(
    values: Map<String, String>,
    api: String,
    allowEmptyValue: Boolean,
): Boolean =
    values.any { (key, item) ->
        isMissing(key, "$api: key") || isMissingValue(item as String?, "$api: value", allowEmptyValue)
    }

private fun isMissingValue(
    value: String?,
    api: String,
    allowEmpty: Boolean,
): Boolean {
    if (!allowEmpty) return isMissing(value, api)
    if (value == null) Logging.error("$api is required")
    return value == null
}
