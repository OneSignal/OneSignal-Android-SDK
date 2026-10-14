package com.onesignal

import com.onesignal.common.OneSignalUtils
import java.util.Collections

/**
 * Profile applied at login. Email/SMS create or transfer that address onto this user; they do not replace other subscriptions.
 * Build one with [builder]. There is no public constructor, so adding a field never breaks compiled callers.
 */
class OneSignalUserProfile private constructor(
    email: String?,
    sms: String?,
    tags: Map<String, String>,
    aliases: Map<String, String>,
) {
    val email: String? = email?.takeIf { it.isNotBlank() }
    val sms: String? = sms?.takeIf { it.isNotBlank() }
    val tags: Map<String, String> = Collections.unmodifiableMap(tags.toMap())
    val aliases: Map<String, String> = Collections.unmodifiableMap(aliases.toMap())

    internal val hasFields: Boolean
        get() =
            !email.isNullOrBlank() ||
                !sms.isNullOrBlank() ||
                tags.isNotEmpty() ||
                aliases.isNotEmpty()

    internal fun validationError(): String? =
        when {
            email != null && !OneSignalUtils.isValidEmail(email) -> "Invalid email address"
            sms != null && !OneSignalUtils.isValidPhoneNumber(sms) -> "Invalid SMS number"
            else -> null
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OneSignalUserProfile) return false
        return email == other.email &&
            sms == other.sms &&
            tags == other.tags &&
            aliases == other.aliases
    }

    override fun hashCode(): Int {
        var result = email?.hashCode() ?: 0
        result = 31 * result + (sms?.hashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        result = 31 * result + aliases.hashCode()
        return result
    }

    override fun toString(): String =
        "OneSignalUserProfile(email=${redact(email)}, sms=${redact(sms)}, tags=${tags.size}, aliases=${aliases.size})"

    /** Builds a [OneSignalUserProfile]. Unset fields stay empty. */
    class Builder {
        private var email: String? = null
        private var sms: String? = null
        private var tags: Map<String, String> = emptyMap()
        private var aliases: Map<String, String> = emptyMap()

        /** Email to create or transfer onto this user at login. */
        fun setEmail(email: String?) = apply { this.email = email }

        /** SMS to create or transfer onto this user at login. Must be E.164 (e.g. +14155552671). */
        fun setSms(sms: String?) = apply { this.sms = sms }

        /** Sets tags to apply at login. The map is copied. */
        fun setTags(tags: Map<String, String>) = apply { this.tags = tags.toMap() }

        /** Sets aliases to apply at login. The map is copied. */
        fun setAliases(aliases: Map<String, String>) = apply { this.aliases = aliases.toMap() }

        /** Builds the profile. */
        fun build() = OneSignalUserProfile(email, sms, tags, aliases)
    }

    companion object {
        /** Returns a new builder. */
        @JvmStatic
        fun builder() = Builder()

        /** SDK-internal Kotlin shorthand; the mangled JVM name keeps it out of the public API. */
        internal operator fun invoke(
            email: String? = null,
            sms: String? = null,
            tags: Map<String, String> = emptyMap(),
            aliases: Map<String, String> = emptyMap(),
        ) = OneSignalUserProfile(email, sms, tags, aliases)

        private fun redact(value: String?): String = if (value == null) "null" else "<set>"
    }
}
