package com.onesignal.debug.internal.logging.logger.android

/**
 * Pure, Android-free helpers for the shared logger/otel crash directory.
 *
 * Ownership is suffix-based: logger-owned records end in [CRASH_OWNED_SUFFIX]; everything else
 * (legacy otel bare-millis names, stray `.tmp`s) is foreign. Keeping this logic free of
 * `File` / `Logging` / Robolectric means it is counted by Jacoco on the plain JVM.
 */
internal const val CRASH_OWNED_SUFFIX = ".otlp"

internal data class CrashDirEntry(
    val name: String,
    val lastModifiedMs: Long,
    val lengthBytes: Long = 0L,
)

/** True when [name] is a logger-owned crash record. */
internal fun isOwnedCrashFile(name: String, ownedSuffix: String = CRASH_OWNED_SUFFIX): Boolean =
    name.endsWith(ownedSuffix)

/**
 * Returns foreign/legacy entries old enough to reclaim. Owned `*.otlp` names are never selected,
 * regardless of age.
 */
internal fun selectUnrecognizedEntries(
    entries: List<CrashDirEntry>,
    nowMs: Long,
    minAgeMillis: Long,
    ownedSuffix: String = CRASH_OWNED_SUFFIX,
): List<CrashDirEntry> =
    entries.filter { entry ->
        !isOwnedCrashFile(entry.name, ownedSuffix) &&
            nowMs - entry.lastModifiedMs >= minAgeMillis
    }

/**
 * Builds the human-readable crash-dir inventory line used for rollout verification.
 * Per-file detail is capped at [maxSample] so Logcat is not flooded.
 */
internal fun formatCrashDirInventory(
    label: String,
    path: String,
    entries: List<CrashDirEntry>,
    nowMs: Long,
    maxSample: Int,
    ownedSuffix: String = CRASH_OWNED_SUFFIX,
): String {
    if (entries.isEmpty()) {
        return "OneSignal: Crash storage inventory [$label] ($path): empty"
    }
    val otlp = entries.count { isOwnedCrashFile(it.name, ownedSuffix) }
    val legacy = entries.size - otlp
    val sample = entries.take(maxSample)
    val summary =
        sample.joinToString(separator = "; ") { entry ->
            "name=${entry.name} bytes=${entry.lengthBytes} ageMs=${nowMs - entry.lastModifiedMs}"
        }
    val truncated =
        if (entries.size > maxSample) {
            " …(+${entries.size - maxSample} more)"
        } else {
            ""
        }
    return "OneSignal: Crash storage inventory [$label] ($path): " +
        "total=${entries.size} otlp=$otlp legacy=$legacy [$summary]$truncated"
}
