package com.onesignal

/**
 * Implemented by every payload that can travel inside a [OneSignalResult].
 *
 * Sealed so a new member is never a silent source-compatible break for external implementors — this
 * contract exists so the envelope can project onto the wire shape without knowing which payload it
 * is carrying, and is not intended to be implemented outside the SDK.
 */
sealed interface OneSignalResultData {
    /** Projects this payload onto the cross-SDK wire shape consumed by the wrapper bridges. */
    fun toMap(): Map<String, Any?>
}

/*
 * The payloads themselves.
 *
 * Several of these start with no fields. That is intentional, and it is why none of them is a
 * `data class` or an `object`: a regular class with an internal constructor can gain nullable or
 * defaulted fields later without changing any signature, without a singleton blocking per-call
 * state, and without silently altering generated equals/hashCode/toString/copy behavior that a
 * customer may have come to depend on.
 *
 * Field names here are also the wire keys, so they are additive-only: never renamed, never removed.
 */

/** The payload returned by a successful login. */
class LoginData internal constructor(
    /** The OneSignal ID the external ID is now associated with. */
    val onesignalId: String,
    /** The external ID that was logged in. */
    val externalId: String,
) : OneSignalResultData {
    override fun toMap(): Map<String, Any?> =
        mapOf(
            KEY_ONESIGNAL_ID to onesignalId,
            KEY_EXTERNAL_ID to externalId,
        )

    override fun toString(): String = "LoginData(onesignalId=$onesignalId, externalId=$externalId)"

    internal companion object {
        // Private because `const val` in an internal companion still compiles to a public static
        // field, which would leak the wire keys into the customer-facing API surface.
        private const val KEY_ONESIGNAL_ID = "onesignalId"
        private const val KEY_EXTERNAL_ID = "externalId"

        /** Null when [onesignalId] or [externalId] is missing, empty, or not a string. */
        fun fromMap(map: Map<*, *>): LoginData? {
            val onesignalId = map[KEY_ONESIGNAL_ID] as? String
            val externalId = map[KEY_EXTERNAL_ID] as? String
            if (onesignalId.isNullOrEmpty() || externalId.isNullOrEmpty()) return null
            return LoginData(onesignalId, externalId)
        }
    }
}

/** The payload returned by a successful logout. Carries no fields yet. */
class LogoutData internal constructor() : OneSignalResultData {
    override fun toMap(): Map<String, Any?> = emptyMap()

    override fun toString(): String = "LogoutData()"

    internal companion object {
        fun fromMap(
            @Suppress("UNUSED_PARAMETER") map: Map<*, *>,
        ): LogoutData = LogoutData()
    }
}

/** The payload returned by a successful user JWT update. Carries no fields yet. */
class UpdateUserJwtData internal constructor() : OneSignalResultData {
    override fun toMap(): Map<String, Any?> = emptyMap()

    override fun toString(): String = "UpdateUserJwtData()"

    internal companion object {
        fun fromMap(
            @Suppress("UNUSED_PARAMETER") map: Map<*, *>,
        ): UpdateUserJwtData = UpdateUserJwtData()
    }
}

/** The payload returned by a successful initialization. Carries no fields yet. */
class InitData internal constructor() : OneSignalResultData {
    override fun toMap(): Map<String, Any?> = emptyMap()

    override fun toString(): String = "InitData()"

    internal companion object {
        fun fromMap(
            @Suppress("UNUSED_PARAMETER") map: Map<*, *>,
        ): InitData = InitData()
    }
}
