package com.onesignal.user

public interface UserIdentity {
    public class Anonymous : UserIdentity

    public interface Identified : UserIdentity
    public data class ExternalIdWithAuthHash(
        val externalId: String,
        val authHash: String,
    ) : Identified
    public data class ExternalIdWithoutAuth(
        val externalId: String,
    ) : Identified
}
