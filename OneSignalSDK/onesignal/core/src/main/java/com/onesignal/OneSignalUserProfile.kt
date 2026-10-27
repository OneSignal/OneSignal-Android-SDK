package com.onesignal

import com.onesignal.common.OneSignalUtils
import com.onesignal.common.hasNullByte
import java.util.Collections

/**
 * Email, SMS, tags, and aliases for the internal profile login.
 * Email and SMS create or transfer that address. They do not replace other subscriptions.
 */
// Takes the Builder so the synthetic accessor's JVM signature stays fixed as fields are added.
internal class OneSignalUserProfile private constructor(builder: Builder) {
    val email: String? = builder.email?.takeIf { it.isNotBlank() }
    val sms: String? = builder.sms?.takeIf { it.isNotBlank() }

    /** False stores the email unsubscribed. True is not sent. Null omits status. */
    val emailEnabled: Boolean? = builder.emailEnabled
    val tags: Map<String, String> = Collections.unmodifiableMap(builder.tags.toMap())
    val aliases: Map<String, String> = Collections.unmodifiableMap(builder.aliases.toMap())

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
            // Java callers can put nulls in these maps despite the non-null types.
            tags.any { (key, value) -> isBadKey(key) || (value as String?) == null } -> "Tag keys must be non-empty and values non-null"
            aliases.any { (label, id) -> isBadKey(label) || isBadKey(id) } -> "Alias labels and IDs must be non-empty"
            else -> null
        }

    private fun isBadKey(value: String?) = value.isNullOrEmpty() || hasNullByte(value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OneSignalUserProfile) return false
        return email == other.email &&
            sms == other.sms &&
            emailEnabled == other.emailEnabled &&
            tags == other.tags &&
            aliases == other.aliases
    }

    override fun hashCode(): Int {
        var result = email?.hashCode() ?: 0
        result = 31 * result + (sms?.hashCode() ?: 0)
        result = 31 * result + (emailEnabled?.hashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        result = 31 * result + aliases.hashCode()
        return result
    }

    override fun toString(): String =
        "OneSignalUserProfile(email=${redact(email)}, sms=${redact(sms)}, tags=${tags.size}, aliases=${aliases.size})"

    /** Builds a [OneSignalUserProfile]. Unset fields stay empty. */
    class Builder {
        internal var email: String? = null
            private set
        internal var sms: String? = null
            private set
        internal var emailEnabled: Boolean? = null
            private set
        internal var tags: Map<String, String> = emptyMap()
            private set
        internal var aliases: Map<String, String> = emptyMap()
            private set

        /** Email to create or transfer onto this user. */
        fun setEmail(email: String?) = apply { this.email = email }

        /** Store the email unsubscribed when false. True is not sent on Create User. */
        fun setEmailEnabled(enabled: Boolean) = apply { this.emailEnabled = enabled }

        /** SMS to create or transfer onto this user at login. Must be E.164 (e.g. +14155552671). */
        fun setSms(sms: String?) = apply { this.sms = sms }

        /** Tags to include. The map is copied. */
        fun setTags(tags: Map<String, String>) = apply { this.tags = tags.toMap() }

        /** Aliases to include. The map is copied. */
        fun setAliases(aliases: Map<String, String>) = apply { this.aliases = aliases.toMap() }

        /** Builds the profile. */
        fun build() = OneSignalUserProfile(this)
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
            emailEnabled: Boolean? = null,
        ) = Builder().setEmail(email).setSms(sms).setTags(tags).setAliases(aliases).apply {
            if (emailEnabled != null) setEmailEnabled(emailEnabled)
        }.build()

        private fun redact(value: String?): String = if (value == null) "null" else "<set>"
    }
}
