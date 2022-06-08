package com.onesignal.user.aliases

import com.onesignal.user.collections.OSCollection

/**
 * A user alias is comprised of two parts. The label represents a key
 * used by all user's within the user repository.  The id represents a
 * unique value for the alias label that can uniquely identify a
 * specific user.
 */
class AliasCollection(aliases: Collection<Alias>) : OSCollection<Alias>(aliases) {
}