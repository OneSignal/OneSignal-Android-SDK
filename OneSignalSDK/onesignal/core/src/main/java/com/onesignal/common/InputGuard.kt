package com.onesignal.common

import com.onesignal.debug.internal.logging.Logging
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal fun hasNullByte(value: String?): Boolean = value != null && '\u0000' in value

/**
 * Returns true and logs an error naming [api] when [value] is null, empty, or contains a null byte.
 */
@OptIn(ExperimentalContracts::class)
fun isMissing(
    value: String?,
    api: String,
): Boolean {
    contract { returns(false) implies (value != null) }
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
 * Returns true when [values] is null, or any of it is missing, per [isMissing].
 */
@OptIn(ExperimentalContracts::class)
fun isMissingAny(
    values: Collection<String>?,
    api: String,
): Boolean {
    contract { returns(false) implies (values != null) }
    if (values == null) {
        Logging.error("$api is required")
        return true
    }
    return values.any { isMissing(it, api) }
}

/**
 * Returns true when [values] is null, any key in it is missing, or any value is missing. With
 * [allowEmptyValue], only null values count as missing.
 */
@OptIn(ExperimentalContracts::class)
fun hasMissingEntries(
    values: Map<String, String>?,
    api: String,
    allowEmptyValue: Boolean,
): Boolean {
    contract { returns(false) implies (values != null) }
    if (values == null) {
        Logging.error("$api is required")
        return true
    }
    return values.any { (key, item) ->
        isMissing(key, "$api: key") || isMissingValue(item as String?, "$api: value", allowEmptyValue)
    }
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
