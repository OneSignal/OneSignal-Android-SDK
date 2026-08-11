package com.onesignal

/**
 * The outcome of an asynchronous OneSignal call: either a [data] payload or an [error], never both
 * and never neither.
 *
 * Every SDK returns the same envelope, so a wrapper can handle results uniformly regardless of the
 * platform underneath it:
 *
 * ```json
 * { "success": true,  "data": { }, "error": null }
 * { "success": false, "data": null, "error": [ { "code": "STORAGE_LOCKED", ... } ] }
 * ```
 *
 * The presence of [error] is what defines the outcome; [isSuccess] and the wire-level `success`
 * flag are both derived from it, so the two can never disagree.
 *
 * From Kotlin:
 * ```kotlin
 * val result = OneSignal.login("user-123")
 * if (result.isSuccess) println(result.data?.onesignalId) else println(result.error?.first?.code)
 * ```
 *
 * From Java the generated accessors read naturally:
 * ```java
 * if (result.isSuccess()) { result.getData(); } else { result.getError(); }
 * ```
 */
class OneSignalResult<T : OneSignalResultData> internal constructor(
    /** The payload on success, `null` on failure. */
    val data: T?,
    /** The failure detail on failure, `null` on success. */
    val error: OneSignalError?,
) {
    init {
        // [isSuccess] reads error while [getOrThrow] reads data, so an envelope carrying neither or
        // both leaves the two disagreeing with nothing to arbitrate. Mirrors the same guard in
        // OneSignalError, and means no caller of this constructor can build a result that lies.
        require((data == null) != (error == null)) { "OneSignalResult carries exactly one of data or error." }
    }

    /** `true` when the call completed successfully. Equivalent to `error == null`. */
    val isSuccess: Boolean
        get() = error == null

    /** Kotlin-idiomatic alias for [data]. */
    fun getOrNull(): T? = data

    /**
     * Returns the payload, or throws [OneSignalException] when the call failed. Use this only where
     * a failure genuinely cannot be handled locally.
     */
    fun getOrThrow(): T = data ?: throw OneSignalException(checkNotNull(error))

    /** Projects the envelope onto the cross-SDK wire shape consumed by the wrapper bridges. */
    fun toMap(): Map<String, Any?> =
        mapOf(
            KEY_SUCCESS to isSuccess,
            KEY_DATA to data?.toMap(),
            KEY_ERROR to error?.toList(),
        )

    override fun toString(): String = if (isSuccess) "OneSignalResult(success, data=$data)" else "OneSignalResult(failure, error=$error)"

    internal companion object {
        // Private because `const val` in an internal companion still compiles to a public static
        // field, which would leak the wire keys into the customer-facing API surface.
        private const val KEY_SUCCESS = "success"
        private const val KEY_DATA = "data"
        private const val KEY_ERROR = "error"

        fun <T : OneSignalResultData> success(data: T): OneSignalResult<T> = OneSignalResult(data, null)

        fun <T : OneSignalResultData> failure(error: OneSignalError): OneSignalResult<T> = OneSignalResult(null, error)

        fun <T : OneSignalResultData> failure(
            code: ErrorCode,
            message: String? = null,
            backendCode: Int? = null,
            cause: Throwable? = null,
        ): OneSignalResult<T> = failure(OneSignalError.of(code, message, backendCode, cause))

        /**
         * Rebuilds an envelope from its wire shape, delegating payload parsing to [dataParser].
         *
         * The incoming `success` flag is deliberately ignored: [error] is the single source of
         * truth, which keeps a malformed producer from yielding a result that claims success while
         * carrying an error. Unrecognized keys are ignored so a newer producer can add fields
         * without breaking an older consumer.
         *
         * `error` is tested for presence, never for shape. A cast doing double duty as the
         * predicate would read an error the parser could not type as no error at all, and report
         * the failure as an empty success.
         */
        @Suppress("UNCHECKED_CAST")
        fun <T : OneSignalResultData> fromMap(
            map: Map<String, Any?>,
            dataParser: (Map<String, Any?>) -> T,
        ): OneSignalResult<T> {
            val reasons = map[KEY_ERROR]
            if (reasons != null) {
                return failure(OneSignalError.fromWire(reasons))
            }

            val dataMap = map[KEY_DATA] as? Map<String, Any?> ?: emptyMap()
            return success(dataParser(dataMap))
        }
    }
}

/** Thrown by [OneSignalResult.getOrThrow] when the underlying call failed. */
class OneSignalException internal constructor(
    /** The failure detail that caused this exception. */
    val error: OneSignalError,
) : Exception(describe(error), error.cause)

// A Detail carries no message when the code says everything, so appending a bare "null" to the
// exception text would only add noise to the stack trace.
private fun describe(error: OneSignalError): String =
    error.error.joinToString("; ") { detail ->
        if (detail.message == null) detail.code.name else "${detail.code}: ${detail.message}"
    }
