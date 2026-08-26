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
 * directory small.
 *
 * [CRASH_MAX_TOTAL_BYTES] bounds *claim*, not bytes on disk. Since writes are size-limited,
 * the two coincide for anything this build wrote. They diverge only for records inherited
 * from a build without that limit: each claims at most [CRASH_MAX_RECORD_BYTES], so a handful
 * of oversized leftovers can occupy more than this while still counting as within cap. That
 * is deliberate — they are real crashes and deserve an upload attempt — and it is bounded by
 * the count cap and by [CRASH_MAX_READ_AGE_MILLIS] aging them out.
 */
internal const val CRASH_MAX_RECORD_COUNT = 50

internal const val CRASH_MAX_TOTAL_BYTES = 2L * 1024 * 1024

/**
 * Largest payload [com.onesignal.debug.internal.logging.logger.android.FileLogStore] will
 * write. Rejecting at the source keeps every stored record within the shared budget, so no
 * single payload can push the rest out. It also caps how much budget an oversized record
 * inherited from a build without this limit is allowed to claim.
 */
internal const val CRASH_MAX_RECORD_BYTES = 512L * 1024

/**
 * @property name on-disk file name. Ownership is decided from its suffix, so it must be the real
 *   name and not a display label.
 * @property lastModifiedMs write time in epoch millis.
 * @property lengthBytes size on disk. Required rather than defaulted: budget claim is
 *   `min(lengthBytes, maxRecordBytes)`, so an omitted size would silently claim zero and disable
 *   the byte budget for that record.
 */
internal data class CrashDirEntry(
    val name: String,
    val lastModifiedMs: Long,
    val lengthBytes: Long,
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
 * Returns owned entries no longer worth uploading, so they are reclaimed rather than skipped.
 * Foreign entries are left to [selectUnrecognizedEntries].
 *
 * Two ways a record qualifies. The ordinary one is age past [maxAgeMillis]. The other is an
 * mtime so far in the future that it can no longer be a clock artifact: the read path gates on
 * `nowMs - lastModifiedMs >= minAgeMillis`, which a future timestamp never satisfies, so such a
 * record is unreadable for its entire life while still consuming a count slot and budget.
 * Reclaiming it is the only way it ever leaves the directory.
 *
 * The threshold is the retention window itself, which keeps the deliberate backwards-clock
 * protection intact: a record dated modestly ahead of now — the clock stepped back since it was
 * written — is left alone to wait until the clock agrees it is old. Only one that would still be
 * in the future after the entire window has elapsed is written off.
 */
internal fun selectExpiredOwnedEntries(
    entries: List<CrashDirEntry>,
    nowMs: Long,
    maxAgeMillis: Long = CRASH_MAX_READ_AGE_MILLIS,
    ownedSuffix: String = CRASH_OWNED_SUFFIX,
): List<CrashDirEntry> =
    entries.filter { entry ->
        isOwnedCrashFile(entry.name, ownedSuffix) &&
            (
                nowMs - entry.lastModifiedMs > maxAgeMillis ||
                    isUnrecoverablyFutureDated(entry, nowMs, maxAgeMillis)
                )
    }

/**
 * True when [entry] is dated so far ahead of [nowMs] that it can no longer be explained by a
 * clock step, and so can never become readable.
 */
private fun isUnrecoverablyFutureDated(
    entry: CrashDirEntry,
    nowMs: Long,
    maxAgeMillis: Long,
): Boolean = entry.lastModifiedMs - nowMs > maxAgeMillis

/** Leading millis of a `{millis}-{uuid}.otlp` name, or null for anything else. */
private fun leadingMillis(name: String): Long? = name.substringBefore('-').toLongOrNull()

/**
 * Returns the owned entries to evict so the directory fits within [maxCount] and
 * [maxTotalBytes], newest kept and the excess returned oldest-first.
 *
 * Size is never on its own a reason to evict. A record too large to upload should be refused
 * at write time; deleting one that is already on disk would destroy a captured crash without
 * ever attempting to send it. What size does control is *budget claim*: each record is charged
 * at most [maxRecordBytes], so one outsized payload — necessarily inherited from a build
 * without the write-time limit — cannot displace the rest of the backlog.
 *
 * A record that does not fit the remaining budget is skipped rather than treated as a cutoff,
 * so everything older still gets its chance to fit.
 *
 * [keepName] is the record the caller just wrote. It is retained regardless of sort position,
 * so a backwards clock step cannot make a fresh record look oldest and delete it.
 *
 * [nowMs] bounds how new a record is allowed to sort. A future mtime would otherwise sort ahead
 * of every genuine record and hold a keep slot against the whole backlog. Ordinary future dates
 * are clamped to [nowMs]; one far enough ahead to be unrecoverable — the same judgement
 * [selectExpiredOwnedEntries] makes — sorts last instead, so it is evicted before any record
 * that could still be uploaded. Ordering does not assume an expiry pass has run, because
 * [FileLogStore]'s write path enforces caps on its own.
 */
@Suppress("LongParameterList")
internal fun selectOverflowOwnedEntries(
    entries: List<CrashDirEntry>,
    nowMs: Long,
    maxCount: Int = CRASH_MAX_RECORD_COUNT,
    maxTotalBytes: Long = CRASH_MAX_TOTAL_BYTES,
    maxRecordBytes: Long = CRASH_MAX_RECORD_BYTES,
    maxAgeMillis: Long = CRASH_MAX_READ_AGE_MILLIS,
    keepName: String? = null,
    ownedSuffix: String = CRASH_OWNED_SUFFIX,
): List<CrashDirEntry> {
    fun sortKey(entry: CrashDirEntry): Long =
        if (isUnrecoverablyFutureDated(entry, nowMs, maxAgeMillis)) {
            Long.MIN_VALUE
        } else {
            minOf(entry.lastModifiedMs, nowMs)
        }

    // Ties break on the millis embedded in the name, which is the write time the filesystem
    // may have rounded away. Names that do not parse sort last among their timestamp group.
    val newestFirst =
        entries
            .filter { isOwnedCrashFile(it.name, ownedSuffix) }
            .sortedWith(
                compareByDescending<CrashDirEntry> { sortKey(it) }
                    .thenByDescending { leadingMillis(it.name)?.coerceAtMost(nowMs) ?: Long.MIN_VALUE },
            )

    fun budgetClaim(entry: CrashDirEntry): Long = minOf(entry.lengthBytes, maxRecordBytes)

    val kept = HashSet<String>()
    var keptBytes = 0L
    keepName?.let { name ->
        newestFirst.firstOrNull { it.name == name }?.let {
            kept.add(it.name)
            keptBytes += budgetClaim(it)
        }
    }
    for (entry in newestFirst) {
        if (kept.contains(entry.name)) continue
        if (kept.size >= maxCount) break
        if (keptBytes + budgetClaim(entry) > maxTotalBytes) continue
        kept.add(entry.name)
        keptBytes += budgetClaim(entry)
    }
    return newestFirst.filterNot { kept.contains(it.name) }.reversed()
}

/**
 * Builds the human-readable crash-dir inventory line used for rollout verification.
 * Per-file detail is capped at [maxSample] so Logcat is not flooded. A negative [maxSample] is
 * treated as zero rather than throwing — this runs on a crash-adjacent path where a logging
 * helper must not be the thing that fails.
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
    val sampleSize = maxSample.coerceAtLeast(0)
    val otlp = entries.count { isOwnedCrashFile(it.name, ownedSuffix) }
    val legacy = entries.size - otlp
    val sample = entries.take(sampleSize)
    val summary =
        sample.joinToString(separator = "; ") { entry ->
            "name=${entry.name} bytes=${entry.lengthBytes} ageMs=${nowMs - entry.lastModifiedMs}"
        }
    val truncated =
        if (entries.size > sampleSize) {
            " …(+${entries.size - sampleSize} more)"
        } else {
            ""
        }
    return "OneSignal: Crash storage inventory [$label] ($path): " +
        "total=${entries.size} otlp=$otlp legacy=$legacy [$summary]$truncated"
}
