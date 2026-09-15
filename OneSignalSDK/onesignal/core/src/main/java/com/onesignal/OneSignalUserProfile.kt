package com.onesignal

/**
 * Profile fields applied at composite login. Email and SMS are additive subscriptions, not replaced traits.
 */
class OneSignalUserProfile @JvmOverloads constructor(
    email: String? = null,
    phoneNumber: String? = null,
    tags: Map<String, String> = emptyMap(),
    aliases: Map<String, String> = emptyMap(),
) {
    val email: String? = email?.takeIf { it.isNotBlank() }
    val phoneNumber: String? = phoneNumber?.takeIf { it.isNotBlank() }
    val tags: Map<String, String> = tags.toMap()
    val aliases: Map<String, String> = aliases.toMap()

    internal val hasFields: Boolean
        get() =
            !email.isNullOrBlank() ||
                !phoneNumber.isNullOrBlank() ||
                tags.isNotEmpty() ||
                aliases.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OneSignalUserProfile) return false
        return email == other.email &&
            phoneNumber == other.phoneNumber &&
            tags == other.tags &&
            aliases == other.aliases
    }

    override fun hashCode(): Int {
        var result = email?.hashCode() ?: 0
        result = 31 * result + (phoneNumber?.hashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        result = 31 * result + aliases.hashCode()
        return result
    }

    override fun toString(): String =
        "OneSignalUserProfile(email=${redact(email)}, phoneNumber=${redact(phoneNumber)}, tags=${tags.size}, aliases=${aliases.size})"

    /** Java builder. Use this instead of the constructor when only some fields are set. */
    class Builder {
        private var email: String? = null
        private var phoneNumber: String? = null
        private var tags: Map<String, String> = emptyMap()
        private var aliases: Map<String, String> = emptyMap()

        /** Sets the email subscription to create at login. */
        fun setEmail(email: String?) = apply { this.email = email }

        /** Sets the SMS subscription to create at login. Must be E.164 (e.g. +14155552671). */
        fun setPhoneNumber(phoneNumber: String?) = apply { this.phoneNumber = phoneNumber }

        /** Sets tags to apply at login. The map is copied. */
        fun setTags(tags: Map<String, String>) = apply { this.tags = tags.toMap() }

        /** Sets aliases to apply at login. The map is copied. */
        fun setAliases(aliases: Map<String, String>) = apply { this.aliases = aliases.toMap() }

        /** Builds the profile. */
        fun build() = OneSignalUserProfile(email, phoneNumber, tags, aliases)
    }

    companion object {
        /** Returns a new Java builder. */
        @JvmStatic
        fun builder() = Builder()

        private fun redact(value: String?): String = if (value == null) "null" else "<set>"
    }
}
