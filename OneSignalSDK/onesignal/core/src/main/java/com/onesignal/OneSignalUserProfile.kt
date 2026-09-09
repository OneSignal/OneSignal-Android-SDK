package com.onesignal

/**
 * Profile fields applied at composite login. Email and SMS are additive subscriptions, not replaced traits.
 */
data class OneSignalUserProfile(
    val email: String? = null,
    /** SMS number in [E.164](https://documentation.onesignal.com/docs/sms-faq#what-is-the-e164-format) format. */
    val phoneNumber: String? = null,
    val tags: Map<String, String> = emptyMap(),
    val aliases: Map<String, String> = emptyMap(),
) {
    internal val hasFields: Boolean
        get() =
            !email.isNullOrBlank() ||
                !phoneNumber.isNullOrBlank() ||
                tags.isNotEmpty() ||
                aliases.isNotEmpty()

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
    }
}
