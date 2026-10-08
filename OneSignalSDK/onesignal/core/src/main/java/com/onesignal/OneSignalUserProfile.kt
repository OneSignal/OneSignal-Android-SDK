package com.onesignal

import com.onesignal.common.OneSignalUtils

/**
 * Profile applied at login. Email/SMS create or transfer that address onto this user; they do not replace other subscriptions.
 */
class OneSignalUserProfile @JvmOverloads constructor(
    email: String? = null,
    sms: String? = null,
    tags: Map<String, String> = emptyMap(),
    aliases: Map<String, String> = emptyMap(),
) {
    val email: String? = email?.takeIf { it.isNotBlank() }
    val sms: String? = sms?.takeIf { it.isNotBlank() }
    val tags: Map<String, String> = tags.toMap()
    val aliases: Map<String, String> = aliases.toMap()

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

    /** Java builder. Use this instead of the constructor when only some fields are set. */
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
        /** Returns a new Java builder. */
        @JvmStatic
        fun builder() = Builder()

        private fun redact(value: String?): String = if (value == null) "null" else "<set>"
    }
}
