package com.onesignal

import com.onesignal.common.toList
import com.onesignal.common.toMap
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

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
        /**
         * The backend's catalog code, present only when [code] is [ErrorCode.BACKEND_ERROR].
         *
         * Left as a raw number on purpose: the backend adds codes on its own schedule, and an SDK
         * release must not be the thing that unblocks recognizing one.
         */
        val backendCode: Int?,
        /** A human-readable description intended for logs and diagnostics, not for end users. */
        val message: String?,
        /**
         * Who the failure came from.
         *
         * Carried rather than derived from [code] on demand, because a code this SDK does not
         * recognize degrades to [ErrorCode.UNKNOWN] and re-deriving from that would report a
         * backend failure as a client one. Defaults to the source [code] implies, which is right
         * for everything the SDK raises locally.
         */
        val source: ErrorSource,
    ) {
        /** Projects this reason onto the cross-SDK wire shape consumed by the wrapper bridges. */
        fun toMap(): Map<String, Any?> =
            mapOf(
                KEY_CODE to code.name,
                KEY_SOURCE to source.name,
                KEY_BACKEND_CODE to backendCode,
                KEY_MESSAGE to message,
            )

        override fun toString(): String = "Detail(code=$code, source=$source, backendCode=$backendCode, message=$message)"

        internal companion object {
            // Private because `const val` in an internal companion still compiles to a public
            // static field, which would leak the wire keys into the customer-facing API surface.
            private const val KEY_CODE = "code"
            private const val KEY_SOURCE = "source"
            private const val KEY_BACKEND_CODE = "backendCode"
            private const val KEY_MESSAGE = "message"

            /**
             * Rebuilds a reason from its wire shape.
             *
             * Reads a raw map because the bridges do not all hand over `Map<String, Any?>`
             * specifically, and because an unchecked cast that failed would be indistinguishable
             * from a reason that was never there.
             *
             * An unrecognized code degrades to [ErrorCode.UNKNOWN] rather than throwing, so a
             * wrapper built against an older SDK survives a newer producer emitting a code it has
             * never heard of. [message], [backendCode] and [source] are preserved either way, which
             * is what keeps a degraded reason diagnosable.
             */
            fun fromMap(map: Map<*, *>): Detail {
                val code = codeOf(map[KEY_CODE] as? String)
                return Detail(
                    code = code,
                    backendCode = (map[KEY_BACKEND_CODE] as? Number)?.toInt(),
                    message = map[KEY_MESSAGE] as? String,
                    source = sourceOf(map[KEY_SOURCE] as? String) ?: code.source,
                )
            }

            fun of(
                code: ErrorCode,
                backendCode: Int? = null,
                message: String? = null,
                source: ErrorSource = code.source,
            ): Detail = Detail(code, backendCode, message, source)

            private fun codeOf(name: String?): ErrorCode = ErrorCode.entries.firstOrNull { it.name == name } ?: ErrorCode.UNKNOWN

            private fun sourceOf(name: String?): ErrorSource? = ErrorSource.entries.firstOrNull { it.name == name }
        }
    }

    /** The first reason, which is the only one for every failure the SDK raises locally. */
    val first: Detail
        get() = error.first()

    /** Projects this error onto the cross-SDK wire shape consumed by the wrapper bridges. */
    fun toList(): List<Map<String, Any?>> = error.map { it.toMap() }

    override fun toString(): String = "OneSignalError(error=$error)"

    internal companion object {
        /** Builds a single-reason error, which is the shape of everything the SDK raises locally. */
        fun of(
            code: ErrorCode,
            message: String? = null,
            backendCode: Int? = null,
            cause: Throwable? = null,
        ): OneSignalError = OneSignalError(listOf(Detail.of(code, backendCode, message)), cause)

        /** Builds a multi-reason error. [reasons] must not be empty. */
        fun of(
            reasons: List<Detail>,
            cause: Throwable? = null,
        ): OneSignalError = OneSignalError(reasons, cause)

        /**
         * Rebuilds an error from its wire shape.
         *
         * Takes the raw value rather than a typed list because the bridges do not all hand over a
         * [List] — org.json's array is not one. Anything a producer put under `error` is a failure
         * being reported, so an unreadable shape becomes a reason carrying its own text rather than
         * being dropped, which would silently turn the failure into a success.
         *
         * A payload carrying no recognizable reason still yields a usable error rather than an
         * empty list, so [first] is always safe.
         */
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
    }
}
