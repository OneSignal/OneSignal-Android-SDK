package com.onesignal.common

import java.security.MessageDigest

/**
 * Deterministic SHA-256 hashing for PII fields (email, phone number) so that
 * sensitive data stored in SharedPreferences is not readable in plain text on
 * rooted devices or via ADB backup.
 *
 * The hash is hex-encoded and always 64 characters long.
 */
object PIIHasher {
    private const val SHA256_HEX_LENGTH = 64
    private val SHA256_HEX_REGEX = Regex("^[a-f0-9]{$SHA256_HEX_LENGTH}$")

    /** Returns the lowercase hex-encoded SHA-256 hash of [value]. */
    fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Returns `true` if [value] looks like a 64-char lowercase hex SHA-256 digest. */
    fun isHashed(value: String): Boolean = SHA256_HEX_REGEX.matches(value)
}
