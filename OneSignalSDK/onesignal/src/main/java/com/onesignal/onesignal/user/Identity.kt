package com.onesignal.onesignal.user

import java.util.*

/**
 * An identity of a user within OneSignal. An identity can either by [Anonymous], meaning
 * there is no desire or mechanism to consistently and uniquely identify a user, or by
 * [Known], meaning there is a desire and a mechanism to consistently and uniquely identify
 * a user.
 */
interface Identity {

    /**
     * An anonymous user.
     */
    class Anonymous : Identity {
        // All Anonymous identities are equal to each other
        // TODO: From discussion,,,this may not be true?  Anonymous->Known->Anonymous, the two aren't the same users?
        override fun equals(other: Any?) = other is Anonymous
        override fun hashCode() = javaClass.hashCode()
        override fun toString(): String {
            return "Anonymous"
        }
    }

    /**
     * A known user, uniquely identified by the [externalId] provided.
     *
     * @param externalId The unique identifier for this known user.
     * @param authHash The optional auth hash for the external id. If not using identity
     * verification, this can be omitted or set to `null`. See [Identity Verification | OneSignal](https://documentation.onesignal.com/docs/identity-verification)
     */
    class Known @JvmOverloads constructor (
        val externalId: String,
        val authHash: String? = null
    ) : Identity {
        override fun equals(other: Any?) = other is Known && externalId == other.externalId && authHash == other.authHash
        override fun hashCode() = Objects.hash(externalId, authHash)
        override fun toString(): String {
            return "Known: $externalId"
        }
    }
}
