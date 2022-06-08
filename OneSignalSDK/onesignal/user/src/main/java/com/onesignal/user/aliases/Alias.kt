package com.onesignal.user.aliases

/**
 * A user alias is comprised of two parts. The label represents a key
 * used by all user's within the user repository.  The id represents a
 * unique value for the alias label that can uniquely identify a
 * specific user.
 */
class Alias(
    val label: String,
    val id: String
)