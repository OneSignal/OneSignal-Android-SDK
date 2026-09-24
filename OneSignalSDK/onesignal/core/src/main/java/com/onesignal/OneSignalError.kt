package com.onesignal

import com.onesignal.common.toList
import com.onesignal.common.toMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.LinkedHashMap

/** Whether the SDK produced a failure locally or OneSignal's backend returned it. */
enum class ErrorSource {
    CLIENT,
    BACKEND,
}

/**
 * The catalog of failure codes shared by every OneSignal SDK.
 *
 * An enum rather than a sealed hierarchy so that Java callers get a native `switch` and the
 * wrapper bridges get a trivial name-to-string marshal. The backend half of the catalog is
 * deliberately *not* modelled here — see [BACKEND_ERROR].
 */
enum class ErrorCode(val source: ErrorSource) {
    /** [IOneSignal.initWithContextSuspend] has not been called. */
    NOT_INITIALIZED(ErrorSource.CLIENT),

    /**
     * Device storage was locked, so the SDK could not read or write its own preferences.
     * Transient: the same call generally succeeds once the device is unlocked.
     */
    STORAGE_LOCKED(ErrorSource.CLIENT),

    /** A caller-supplied argument failed validation before any request was made. */
    INVALID_ARGUMENT(ErrorSource.CLIENT),

    /** OneSignal rejected the request. The catalog code is on [OneSignalError.Detail.backendCode]. */
    BACKEND_ERROR(ErrorSource.BACKEND),

    /** No more specific code applies. Callers should surface [OneSignalError.Detail.message]. */
    UNKNOWN(ErrorSource.CLIENT),
}

/**
 * Describes why a OneSignal call failed.
 *
 * One request can fail for several reasons at once, so [error] is a list of [Detail]. Everything
 * the SDK raises locally has exactly one reason, which [first] reads without the indexing
 * ceremony.
 *
 * On the wire this is the list itself, sitting under the envelope's `error` key:
 *
 * ```json
 * { "success": false, "data": null,
 *   "error": [ { "code": "STORAGE_LOCKED", "source": "CLIENT", "backendCode": null, "message": "..." } ] }
 * ```
 *
 * The constructor is private on purpose. An `internal` constructor still emits as JVM-public, so
 * Java outside this module could build an error with no reasons and leave [first] throwing; private
 * closes that hole. Callers construct through [of] or [fromWire].
 */
class OneSignalError private constructor(
    error: List<Detail>,
    /**
     * The throwable behind the failure, when there was one.
     *
     * Deliberately absent from [toList]: a stack trace cannot cross the wrapper bridges, and the
     * wire schema has to stay identical across every SDK. This exists so that native Kotlin and
     * Java callers do not lose the stack when the suspend APIs report a failure instead of
     * throwing it.
     */
    val cause: Throwable?,
) {
    /**
     * Why the call failed. Never empty.
     *
     * Copied so a caller holding the original list cannot empty it afterwards, and unmodifiable so
     * the copy itself cannot be emptied either. Java sees a plain `List` and `clear()` is one
     * keystroke away from `get()`; both would leave [first] throwing.
     */
    val error: List<Detail> = Collections.unmodifiableList(error.toList())

    init {
        // [first] is documented as always safe to read, and the wire projection of an empty error
        // would claim failure while explaining nothing. Both factories guard this; the check is
        // here so a future caller of the constructor cannot quietly break the invariant.
        require(this.error.isNotEmpty()) { "OneSignalError requires at least one Detail." }
    }

    /**
     * A single reason a call failed.
     *
     * Nested rather than top-level so the name cannot collide with `kotlin.Error`, which is
     * auto-imported everywhere, or shadow `java.lang.Error` in a Java file that imports it.
     */
    class Detail private constructor(
        /** A stable code, safe to branch on. Never localized. */
        val code: ErrorCode,
        /** Backend catalog code (e.g. `user-1`). Present only for BACKEND_ERROR. */
        val backendCode: String?,
        /** HTTP status of the backend response, when there was one. */
        val httpStatus: Int?,
        /** A human-readable description intended for logs and diagnostics, not for end users. */
        val message: String?,
        /** Who the failure came from. Kept on UNKNOWN so a backend failure is not re-attributed as client. */
        val source: ErrorSource,
        /** Backend `meta` object, when present (e.g. `conflicting_aliases`). */
        val meta: Map<String, Any?>?,
    ) {
        /** Projects this reason onto the cross-SDK wire shape consumed by the wrapper bridges. */
        fun toMap(): Map<String, Any?> =
            mapOf(
                KEY_CODE to code.name,
                KEY_SOURCE to source.name,
                KEY_BACKEND_CODE to backendCode,
                KEY_HTTP_STATUS to httpStatus,
                KEY_MESSAGE to message,
                KEY_META to meta,
            )

        override fun toString(): String =
            "Detail(code=$code, source=$source, backendCode=$backendCode, httpStatus=$httpStatus, message=$message, meta=$meta)"

        internal companion object {
            // Private because `const val` in an internal companion still compiles to a public
            // static field, which would leak the wire keys into the customer-facing API surface.
            private const val KEY_CODE = "code"
            private const val KEY_SOURCE = "source"
            private const val KEY_BACKEND_CODE = "backendCode"
            private const val KEY_HTTP_STATUS = "httpStatus"
            private const val KEY_MESSAGE = "message"
            private const val KEY_META = "meta"

            /** Rebuilds a reason from its wire shape. Unrecognized codes become [ErrorCode.UNKNOWN]. */
            fun fromMap(map: Map<*, *>): Detail {
                val code = codeOf(map[KEY_CODE] as? String)
                return Detail(
                    code = code,
                    backendCode = optionalString(map[KEY_BACKEND_CODE]),
                    httpStatus = (map[KEY_HTTP_STATUS] as? Number)?.toInt(),
                    message = map[KEY_MESSAGE] as? String,
                    source = sourceOf(map[KEY_SOURCE] as? String) ?: code.source,
                    meta = optionalMeta(map[KEY_META]),
                )
            }

            fun of(
                code: ErrorCode,
                backendCode: String? = null,
                httpStatus: Int? = null,
                message: String? = null,
                source: ErrorSource = code.source,
                meta: Map<String, Any?>? = null,
            ): Detail =
                Detail(
                    code,
                    backendCode,
                    httpStatus,
                    message,
                    source,
                    meta?.let { Collections.unmodifiableMap(LinkedHashMap(it)) },
                )

            private fun codeOf(name: String?): ErrorCode = ErrorCode.entries.firstOrNull { it.name == name } ?: ErrorCode.UNKNOWN

            private fun sourceOf(name: String?): ErrorSource? = ErrorSource.entries.firstOrNull { it.name == name }

            private fun optionalString(value: Any?): String? = (value as? String)?.takeIf { it.isNotEmpty() }

            private fun optionalMeta(value: Any?): Map<String, Any?>? {
                val map =
                    when (value) {
                        is Map<*, *> ->
                            value.entries.mapNotNull { (key, entry) ->
                                (key as? String)?.let { it to entry }
                            }.toMap()
                        is JSONObject -> value.toMap()
                        else -> return null
                    }
                return map.takeIf { it.isNotEmpty() }?.let { Collections.unmodifiableMap(LinkedHashMap(it)) }
            }
        }
    }

    /** The first reason, which is the only one for every failure the SDK raises locally. */
    val first: Detail
        get() = error.first()

    /** Projects this error onto the cross-SDK wire shape consumed by the wrapper bridges. */
    fun toList(): List<Map<String, Any?>> = error.map { it.toMap() }

    override fun toString(): String = "OneSignalError(error=$error)"

    internal companion object {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = false
            }

        /** Builds a single-reason error, which is the shape of everything the SDK raises locally. */
        fun of(
            code: ErrorCode,
            message: String? = null,
            backendCode: String? = null,
            httpStatus: Int? = null,
            meta: Map<String, Any?>? = null,
            cause: Throwable? = null,
        ): OneSignalError = OneSignalError(listOf(Detail.of(code, backendCode, httpStatus, message, meta = meta)), cause)

        /** Builds a multi-reason error. [reasons] must not be empty. */
        fun of(
            reasons: List<Detail>,
            cause: Throwable? = null,
        ): OneSignalError = OneSignalError(reasons, cause)

        /** Parses a backend `errors[]` body. Unreadable bodies become one reason with [httpStatus]. */
        fun fromBackendResponse(
            httpStatus: Int?,
            body: String?,
            fallbackMessage: String? = null,
        ): OneSignalError {
            parseBackendErrors(httpStatus, body)?.let { return OneSignalError(it, cause = null) }
            val message = body?.takeIf { it.isNotBlank() } ?: fallbackMessage
            return of(ErrorCode.BACKEND_ERROR, message = message, httpStatus = httpStatus)
        }

        /** Rebuilds an error from its wire shape. Unreadable entries become UNKNOWN rather than dropping the failure. */
        fun fromWire(raw: Any?): OneSignalError {
            val reasons =
                when (raw) {
                    is List<*> -> raw.map { reasonOf(it) }
                    // org.json is what a bridge naturally parses with, and JSONArray is not a
                    // java.util.List. Convert rather than treating the whole array as one reason.
                    is JSONArray -> raw.toList().orEmpty().map { reasonOf(it) }
                    else -> listOf(reasonOf(raw))
                }
            return OneSignalError(reasons.ifEmpty { listOf(Detail.of(ErrorCode.UNKNOWN)) }, cause = null)
        }

        private fun reasonOf(raw: Any?): Detail =
            when (raw) {
                is Map<*, *> -> Detail.fromMap(raw)
                is JSONObject -> Detail.fromMap(raw.toMap())
                else -> Detail.of(ErrorCode.UNKNOWN, message = raw?.toString())
            }

        private fun parseBackendErrors(
            httpStatus: Int?,
            body: String?,
        ): List<Detail>? {
            if (body.isNullOrBlank()) return null
            return try {
                val root = json.parseToJsonElement(body) as? JsonObject ?: return null
                val errors = root["errors"] as? JsonArray ?: return null
                val details =
                    errors.mapNotNull { element ->
                        val item = element as? JsonObject ?: return@mapNotNull null
                        Detail.of(
                            code = ErrorCode.BACKEND_ERROR,
                            backendCode = item.optionalString("code"),
                            httpStatus = httpStatus,
                            message = item.optionalString("title"),
                            meta = (item["meta"] as? JsonObject)?.toPlainMap()?.takeIf { it.isNotEmpty() },
                        )
                    }
                details.takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
        }

        private fun JsonObject.optionalString(key: String): String? {
            val value = this[key] ?: return null
            if (value is JsonNull) return null
            val primitive = value as? JsonPrimitive ?: return null
            return primitive.content.takeIf { it.isNotEmpty() }
        }

        private fun JsonObject.toPlainMap(): Map<String, Any?> = entries.associate { it.key to it.value.toPlain() }

        private fun JsonElement.toPlain(): Any? =
            when (this) {
                is JsonNull -> null
                is JsonPrimitive ->
                    when {
                        isString -> content
                        content.equals("true", ignoreCase = true) -> true
                        content.equals("false", ignoreCase = true) -> false
                        else -> content.toLongOrNull() ?: content.toDoubleOrNull() ?: content
                    }
                is JsonObject -> toPlainMap()
                is JsonArray -> map { it.toPlain() }
            }
    }
}
