package com.onesignal.user

interface Identity {
    class Anonymous : Identity {
        // All Anonymous identities are equal to each other
        override fun equals(other: Any?) = other is Anonymous
        override fun hashCode() = javaClass.hashCode()
    }

    interface Known : Identity

    data class ExternalIdWithAuthHash(
        val externalId: String,
        val authHash: String,
    ) : Known
    data class ExternalId(
        val externalId: String,
    ) : Known
}
