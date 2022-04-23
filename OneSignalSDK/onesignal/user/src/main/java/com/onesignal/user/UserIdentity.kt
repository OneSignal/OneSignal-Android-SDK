package com.onesignal.user

// TODO: Naming of UserIdentity
//   Con: UserIdentity.Anonymous - Longer than necessary
//   Pro: We really are sure it's about a User
//          (but we know already know this with method names and namespace)
//   Options:
//     * Identity (+++)
//       PRO: Can clearly see this itself is NOT a User instance
//       PRO: Much shorter, things below have to start with this anyway
//             - Identity.Anonymous()
//             - Identity.ExternalId()
//             - Identity.ExternalIdWithoutAuth()
//          - (It's possible to drop the prefix, however the IDE never suggests
//             the import that is required to do that)

interface UserIdentity {
    class Anonymous : UserIdentity {
        // All Anonymous identities are equal to each other
        override fun equals(other: Any?) = other is Anonymous
        override fun hashCode() = javaClass.hashCode()
    }

    // TODO: Naming of UserIdentity.Identified
    //   Con: Identified and Identity sound and look similar
    //   Pro: Identified would be consistent with our docs on "Identified User"
    //   Options:
    //   * UserIdentity.Known (++++)
    //       PRO: "Known" more concise than "Identified"
    interface Identified : UserIdentity

    // TODO: Naming of ExternalIdWithAuthHash and ExternalIdWithoutAuth (--)
    //   Con: Long name.
    //   Con: Hard to spot the difference
    //   Options:
    //     * ExternalIdNoAuth and ExternalIdWithAuthHash (++)
    //        PRO: Shorter, "NoAuth" vs "WithAuthHash" sticks out more
    //     * ExternalId and ExternalIdWithAuthHash (+++)
    //        PRO: Sticks out more visually
    //        CON: The easier to read one is the less safe one.
    //     * ExternalId and ExternalIdWithoutAuth (+)
    //        PRO: Sticks out more visually
    //        PRO: The shorter one is the recommended one.
    data class ExternalIdWithAuthHash(
        val externalId: String,
        val authHash: String,
    ) : Identified
    data class ExternalIdWithoutAuth(
        val externalId: String,
    ) : Identified
}
