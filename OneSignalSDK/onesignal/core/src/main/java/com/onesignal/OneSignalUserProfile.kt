package com.onesignal

import com.onesignal.common.OneSignalUtils
import com.onesignal.common.hasNullByte
import java.util.Collections

/**
 * Profile applied at login. Email/SMS create or transfer that address onto this user; they do not replace other subscriptions.
 * Build one with [builder]. There is no public constructor, so adding a field never breaks compiled callers.
 */
// Takes the Builder so the synthetic accessor's JVM signature stays fixed as fields are added.
class OneSignalUserProfile private constructor(builder: Builder) {
    val email: String? = builder.email?.takeIf { it.isNotBlank() }
    val sms: String? = builder.sms?.takeIf { it.isNotBlank() }

    /** Subscription status for [email]. Null sends no status. See [Builder.setEmailEnabled]. */
    val emailEnabled: Boolean? = builder.emailEnabled

    /** Subscription status for [sms]. Null sends no status. See [Builder.setSmsEnabled]. */
    val smsEnabled: Boolean? = builder.smsEnabled
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
            smsEnabled == other.smsEnabled &&
            tags == other.tags &&
            aliases == other.aliases
    }

    override fun hashCode(): Int {
        var result = email?.hashCode() ?: 0
        result = 31 * result + (sms?.hashCode() ?: 0)
        result = 31 * result + (emailEnabled?.hashCode() ?: 0)
        result = 31 * result + (smsEnabled?.hashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        result = 31 * result + aliases.hashCode()
        return result
    }

    override fun toString(): String =
        "OneSignalUserProfile(email=${redact(email)}, emailEnabled=$emailEnabled, sms=${redact(sms)}, " +
            "smsEnabled=$smsEnabled, tags=${tags.size}, aliases=${aliases.size})"

    /** Builds a [OneSignalUserProfile]. Unset fields stay empty. */
    class Builder {
        internal var email: String? = null
            private set
        internal var sms: String? = null
            private set
        internal var emailEnabled: Boolean? = null
            private set
        internal var smsEnabled: Boolean? = null
            private set
        internal var tags: Map<String, String> = emptyMap()
            private set
        internal var aliases: Map<String, String> = emptyMap()
            private set

        /** Email to create or transfer onto this user at login. */
        fun setEmail(email: String?) = apply { this.email = email }

        /**
         * Subscription status for the email. Subscribes when true, unsubscribes when false.
         * Unset sends no status, so an address that already exists keeps the status it has,
         * unless a pending [com.onesignal.user.IUserManager.addEmail] for it sends one.
         * Ignored without [setEmail]. Cannot be returned to unset once called.
         */
        fun setEmailEnabled(enabled: Boolean) = apply { this.emailEnabled = enabled }

        /** SMS to create or transfer onto this user at login. Must be E.164 (e.g. +14155552671). */
        fun setSms(sms: String?) = apply { this.sms = sms }

        /**
         * Subscription status for the SMS number. Subscribes when true, unsubscribes when false.
         * Unset sends no status, so a number that already exists keeps the status it has,
         * unless a pending [com.onesignal.user.IUserManager.addSms] for it sends one.
         * Ignored without [setSms]. Cannot be returned to unset once called.
         */
        fun setSmsEnabled(enabled: Boolean) = apply { this.smsEnabled = enabled }

        /** Sets tags to apply at login. The map is copied. */
        fun setTags(tags: Map<String, String>) = apply { this.tags = tags.toMap() }

        /** Sets aliases to apply at login. The map is copied. */
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
            smsEnabled: Boolean? = null,
        ) = Builder().setEmail(email).setSms(sms).setTags(tags).setAliases(aliases).apply {
            if (emailEnabled != null) setEmailEnabled(emailEnabled)
            if (smsEnabled != null) setSmsEnabled(smsEnabled)
        }.build()

        private fun redact(value: String?): String = if (value == null) "null" else "<set>"
    }
}
