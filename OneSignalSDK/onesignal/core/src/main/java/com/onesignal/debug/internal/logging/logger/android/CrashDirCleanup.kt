package com.onesignal.debug.internal.logging.logger.android

/**
 * Pure, Android-free helpers for the crash directory.
 *
 * Ownership is suffix-based: logger-owned records end in [CRASH_OWNED_SUFFIX]; everything else
 * (bare-millis names left by a pre-upgrade otel session, stray `.tmp`s) is foreign. Keeping this logic free of
 * `File` / `Logging` / Robolectric means it is counted by Jacoco on the plain JVM.
 */
internal const val CRASH_OWNED_SUFFIX = ".otlp"

/**
 * Upper bound on how long an owned record stays eligible for upload, carried over from the
 * disk-buffering config the otel module used. Past this the payload is too stale to be worth
 * shipping, and without a ceiling a record that never uploads successfully — including one
 * written while remote logging is off, which is never even read — would be retried on every
 * launch forever.
 */
internal const val CRASH_MAX_READ_AGE_MILLIS = 72L * 60 * 60 * 1000

/**
 * Accumulation caps, applied oldest-first. The count bound is what normally binds: crash
 * records are single-event OTLP payloads of a few KB, so 50 covers far more unsent crashes
 * than a healthy install will ever hold. The byte bound is the backstop for pathological
 * payloads (deep stacktraces, huge exception messages) where count alone would not keep the
 * directory small. The newest record is always retained even if it alone exceeds the byte cap.
 */
internal const val CRASH_MAX_RECORD_COUNT = 50

internal const val CRASH_MAX_TOTAL_BYTES = 2L * 1024 * 1024

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
 * Returns owned entries past [maxAgeMillis] — no longer uploadable, so they are reclaimed
 * rather than skipped. Foreign entries are left to [selectUnrecognizedEntries].
 */
internal fun selectExpiredOwnedEntries(
    entries: List<CrashDirEntry>,
    nowMs: Long,
    maxAgeMillis: Long = CRASH_MAX_READ_AGE_MILLIS,
    ownedSuffix: String = CRASH_OWNED_SUFFIX,
): List<CrashDirEntry> =
    entries.filter { entry ->
        isOwnedCrashFile(entry.name, ownedSuffix) &&
            nowMs - entry.lastModifiedMs > maxAgeMillis
    }

/**
 * Returns the owned entries to evict so the directory fits within [maxCount] and
 * [maxTotalBytes]. Newest records are kept; the excess is returned oldest-first. The single
 * newest record is never evicted, so an oversized payload cannot starve the cache.
 */
internal fun selectOverflowOwnedEntries(
    entries: List<CrashDirEntry>,
    maxCount: Int = CRASH_MAX_RECORD_COUNT,
    maxTotalBytes: Long = CRASH_MAX_TOTAL_BYTES,
    ownedSuffix: String = CRASH_OWNED_SUFFIX,
): List<CrashDirEntry> {
    // Name breaks ties: owned names are millis-prefixed, so it orders consistently with mtime
    // when a filesystem reports coarse timestamps.
    val newestFirst =
        entries
            .filter { isOwnedCrashFile(it.name, ownedSuffix) }
            .sortedWith(compareByDescending<CrashDirEntry> { it.lastModifiedMs }.thenByDescending { it.name })

    val kept = HashSet<String>()
    var keptBytes = 0L
    for (entry in newestFirst) {
        if (kept.size >= maxCount) break
        if (kept.isNotEmpty() && keptBytes + entry.lengthBytes > maxTotalBytes) break
        kept.add(entry.name)
        keptBytes += entry.lengthBytes
    }
    return newestFirst.filterNot { kept.contains(it.name) }.reversed()
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
