package com.onesignal.core.internal.backend.impl

/**
 * Builds the Turbine-relative path for SDK feature flags and validates the SDK version label.
 *
 * Pure Kotlin only (no `java.*` / Android APIs) so this file can be reused from Kotlin Multiplatform `commonMain`.
 */
internal object TurbineSdkFeatureFlagsPath {
    private val FEATURES_SDK_VERSION_LABEL_REGEX = Regex("""^\d{6}(-[^/\s]+)?$""")

    /**
     * Labels expected in the `:sdk_version` path segment: six digits, optionally `-` and a non-empty suffix
     * (no slashes or whitespace).
     */
    fun isValidFeaturesSdkVersionLabel(label: String): Boolean = FEATURES_SDK_VERSION_LABEL_REGEX.matches(label)

    /**
     * Path only (relative to API base), matching `apps/{app_id}/sdk/features/{platform}/{sdk_version}`.
     * [platform] and [sdkVersion] are UTF-8 percent-encoded per RFC 3986 (unreserved bytes left as-is).
     */
    fun buildGetPath(
        appId: String,
        platform: String,
        sdkVersion: String,
    ): String {
        val p = percentEncodePathSegmentUtf8(platform)
        val v = percentEncodePathSegmentUtf8(sdkVersion)
        return "apps/$appId/sdk/features/$p/$v"
    }

    /**
     * Percent-encodes a UTF-8 string as a single path segment: unreserved bytes stay literal; everything else
     * becomes `%HH` (uppercase hex). Space becomes `%20` (not `+`), matching typical REST path encoding.
     */
    internal fun percentEncodePathSegmentUtf8(segment: String): String {
        val bytes = segment.encodeToByteArray()
        return buildString(bytes.size * 3) {
            for (b in bytes) {
                val u = b.toInt() and 0xff
                if (isUnreservedByte(u)) {
                    append(u.toChar())
                } else {
                    append('%')
                    append(HEX_DIGITS[u shr 4])
                    append(HEX_DIGITS[u and 15])
                }
            }
        }
    }

    private fun isUnreservedByte(u: Int): Boolean =
        u in 0x41..0x5A ||
            u in 0x61..0x7A ||
            u in 0x30..0x39 ||
            u == 0x2D ||
            u == 0x2E ||
            u == 0x5F ||
            u == 0x7E

    private const val HEX_DIGITS = "0123456789ABCDEF"
}
