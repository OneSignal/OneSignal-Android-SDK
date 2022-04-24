package com.onesignal.user

interface Identity {
    class Anonymous : Identity {
        // All Anonymous identities are equal to each other
        override fun equals(other: Any?) = other is Anonymous
        override fun hashCode() = javaClass.hashCode()
    }

    interface Known : Identity {
        val externalId: String
    }

    data class ExternalId(
        override val externalId: String,
    ) : Known
    data class ExternalIdWithAuthHash(
        override val externalId: String,
        val authHash: String,
    ) : Known
}
