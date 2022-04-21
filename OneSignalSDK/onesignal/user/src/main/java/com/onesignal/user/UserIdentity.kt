package com.onesignal.user

public interface UserIdentity {
    public class Anonymous : UserIdentity {
        // All Anonymous identities are equal to each other
        override fun equals(other: Any?) = other is Anonymous
        override fun hashCode(): Int = javaClass.hashCode()
    }

    public interface Identified : UserIdentity
    public data class ExternalIdWithAuthHash(
        val externalId: String,
        val authHash: String,
    ) : Identified
    public data class ExternalIdWithoutAuth(
        val externalId: String,
    ) : Identified
}
