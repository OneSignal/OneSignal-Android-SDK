package com.onesignal.debug.internal.logging.logger.android

import android.util.Log
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.logger.ILogFileStore
import com.onesignal.logger.StoredLogFile
import com.onesignal.logger.crash.CrashDirEntry
import com.onesignal.logger.crash.CrashRetention
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android [ILogFileStore] backed by the local filesystem: one file per crash record under
 * [rootPath], aged via [CrashRetention.effectiveWriteTimeMs] for [listReadable] so a file the
 * crashing process may still have been writing is never read.
 *
 * The directory is shared with pre-upgrade sessions, so ownership is decided purely by the
 * policy's owned suffix: everything this store writes ends in `.otlp`; anything else
 * (bare-millis names, stray `.tmp`s) is foreign and reclaimable via [deleteUnrecognizedEntries].
 *
 * All retention decisions come from the shared [CrashRetention] so Android and iOS bound the
 * directory identically; this class only turns a listing into [CrashDirEntry]s and applies the
 * result with `File` I/O. `maxTotalBytes` bounds the *budget claim* rather than raw disk bytes —
 * the two differ only for oversized records inherited from a build predating the write-time limit
 * in [save]. Both bounds are enforced on every path that touches the directory ([save],
 * [listReadable], [deleteUnrecognizedEntries]), and over-limit records are deleted rather than
 * merely hidden from [listReadable], so a record that never uploads cannot grow the cache forever.
 */
internal class FileLogStore(
    private val rootPath: String,
) : ILogFileStore {
    private val rootDir: File get() = File(rootPath)

    // One instance passed to every selector, so no path can disagree about the bounds.
    private val policy = CrashRetention.defaultPolicy

    private companion object {
        const val TAG = "OneSignal"

        /** Keeps reclaim log lines bounded when a large backlog is trimmed at once. */
        const val MAX_NAMES_LOGGED = 10
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun save(bytes: ByteArray): Boolean {
        return try {
            if (bytes.size > policy.maxRecordBytes) {
                // Refuse rather than store-then-reclaim: a record this large would either claim
                // the whole shared budget or be deleted before it was ever uploaded.
                Log.w(
                    TAG,
                    "FileLogStore: refusing record of ${bytes.size} bytes, " +
                        "over the ${policy.maxRecordBytes}-byte limit",
                )
                return false
            }
            val dir = rootDir
            if (!dir.exists()) dir.mkdirs()
            // Write to a temp file then rename so a half-written file is never readable.
            val target = File(dir, "${System.currentTimeMillis()}-${UUID.randomUUID()}${policy.ownedSuffix}")
            val temp = File(dir, target.name + ".tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                // Fallback: write directly if rename is unsupported on this fs.
                target.writeBytes(bytes)
                temp.delete()
            }
            // Crash path: raw Logcat only — Logging.info can invoke app listeners
            // synchronously, and a listener exception would flip a successful write to false.
            Log.i(TAG, "FileLogStore: saved name=${target.name} bytes=${bytes.size} dir=${dir.path}")
            enforceAccumulationCaps(dir, keepNames = setOf(target.name))
            true
        } catch (t: Throwable) {
            // Crash-path safety: never throw from persistence; signal failure to caller.
            Log.w(TAG, "FileLogStore: save failed: ${t.message}")
            false
        }
    }

    /**
     * Evicts oldest-first on the crash path until the owned records fit the accumulation caps,
     * always retaining [keepNames] (the record [save] just wrote).
     *
     * Runs inline on the crashing thread, and uses raw Logcat for the same reason [save] does.
     * The [CrashRetention.isWithinCaps] check below is what keeps that affordable: the selector
     * sorts the whole directory, so it must only run on the rare pass that is actually over cap.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun enforceAccumulationCaps(dir: File, keepNames: Set<String>) {
        try {
            val entries = listEntries(dir)
            // Uses the selector's own capped accounting, so the check and the trim cannot
            // disagree about whether a trim is needed.
            if (CrashRetention.isWithinCaps(entries, policy)) return
            val overflow =
                CrashRetention.selectOverflowOwned(
                    entries,
                    nowMs = System.currentTimeMillis(),
                    keepNames = keepNames,
                    policy = policy,
                )
            if (overflow.isEmpty()) return
            var evicted = 0
            for (entry in overflow) {
                if (File(dir, entry.name).delete()) evicted++
            }
            Log.i(
                TAG,
                "FileLogStore: evicted $evicted/${overflow.size} over-cap record(s) in ${dir.path}: " +
                    overflow.take(MAX_NAMES_LOGGED).joinToString(", ") { it.name },
            )
        } catch (t: Throwable) {
            // Never let cache trimming turn a successful crash write into a failure.
            Log.w(TAG, "FileLogStore: cap enforcement failed: ${t.message}")
        }
    }

    /**
     * Deletes owned records beyond the accumulation caps. Unlike [enforceAccumulationCaps] this
     * runs on the uploader's IO paths, where walking a large inherited backlog is safe.
     *
     * @return names of the evicted records, so callers can exclude them from the same pass
     */
    private fun reclaimOverLimitRecords(entries: List<CrashDirEntry>, nowMs: Long): Set<String> {
        val overflow = CrashRetention.selectOverflowOwned(entries, nowMs, policy = policy)
        if (overflow.isEmpty()) return emptySet()
        var deleted = 0
        for (entry in overflow) {
            if (File(rootDir, entry.name).delete()) {
                deleted++
            } else {
                Logging.warn("FileLogStore: failed to evict over-cap record ${entry.name}")
            }
        }
        Logging.info(
            "FileLogStore: evicted $deleted/${overflow.size} over-cap record(s) in ${rootDir.path}: " +
                overflow.take(MAX_NAMES_LOGGED).joinToString(", ") { it.name },
        )
        return overflow.mapTo(HashSet()) { it.name }
    }

    /**
     * `File.lastModified()` reports both "epoch" and "I/O error" as `0`, and the policy reads any
     * timestamp it is handed as an age — `0` as the maximum one, which expires a live record. So
     * a non-positive value is reported as unknown and the policy dates the record from its name
     * instead; nothing here is ever legitimately written at the epoch.
     */
    private fun listEntries(dir: File): List<CrashDirEntry> =
        dir.listFiles()?.filter { it.isFile }?.map { file ->
            CrashDirEntry(
                name = file.name,
                lastModifiedMs = file.lastModified().takeIf { it > 0 },
                lengthBytes = file.length(),
            )
        }.orEmpty()

    /**
     * Deletes owned records past the policy's read-age ceiling. Called from both read paths so
     * over-age records are reclaimed even when remote logging is off and the uploader never
     * gets as far as [listReadable].
     *
     * @return every expired name, whether or not its delete succeeded — one that could not be
     *   removed must still not be read.
     */
    private fun reclaimExpiredOwnedRecords(entries: List<CrashDirEntry>, nowMs: Long): Set<String> {
        val expired = CrashRetention.selectExpiredOwned(entries, nowMs, policy)
        if (expired.isEmpty()) return emptySet()
        var deleted = 0
        for (entry in expired) {
            if (File(rootDir, entry.name).delete()) {
                deleted++
            } else {
                Logging.warn("FileLogStore: failed to reclaim expired record ${entry.name}")
            }
        }
        Logging.info(
            "FileLogStore: reclaimed $deleted/${expired.size} expired record(s) in ${rootDir.path}: " +
                expired.take(MAX_NAMES_LOGGED).joinToString(", ") { it.name },
        )
        return expired.mapTo(HashSet()) { it.name }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun listReadable(minAgeMillis: Long): List<StoredLogFile> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val entries = listEntries(rootDir)
                // Reclaim before reading: payloads are only materialized for records that
                // survive both bounds, so an over-cap backlog is never fully loaded.
                val expired = reclaimExpiredOwnedRecords(entries, now)
                val evicted = reclaimOverLimitRecords(entries.filterNot { expired.contains(it.name) }, now)
                val dropped = expired + evicted
                val suffixMatches =
                    entries.filter { CrashRetention.isOwned(it.name, policy) && !dropped.contains(it.name) }
                // Age must come from the same helper the reclaim passes above use, or a record
                // could be withheld here by one clock while being deleted by another. An
                // undatable record is withheld, never deleted: it cannot be shown to have
                // cleared minAgeMillis, so it may still be mid-write, and the point of that
                // gate is to not read a file the crashing process had not finished. It stays
                // on disk for a later pass, and the caps still bound it.
                val writeTimes = suffixMatches.map { it to CrashRetention.effectiveWriteTimeMs(it) }
                val undatable = writeTimes.count { (_, writtenMs) -> writtenMs == null }
                val readable =
                    writeTimes
                        .filter { (_, writtenMs) -> writtenMs != null && now - writtenMs >= minAgeMillis }
                        .mapNotNull { (entry, _) -> readRecord(File(rootDir, entry.name)) }
                Logging.debug(
                    "FileLogStore: listReadable minAgeMs=$minAgeMillis total=${entries.size} " +
                        "suffix=${suffixMatches.size} readable=${readable.size} " +
                        "expired=${expired.size} overCap=${evicted.size} " +
                        "undatable=$undatable " +
                        "legacy=${entries.count { !CrashRetention.isOwned(it.name, policy) }}",
                )
                readable
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Logging.warn("FileLogStore: listReadable failed: ${t.message}")
                emptyList()
            }
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun readRecord(file: File): StoredLogFile? =
        try {
            StoredLogFile(id = file.name, bytes = file.readBytes())
        } catch (t: Throwable) {
            Logging.warn("FileLogStore: failed to read ${file.name}: ${t.message}")
            null
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            try {
                val deleted = File(rootDir, id).delete()
                Logging.debug("FileLogStore: delete id=$id deleted=$deleted")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Logging.warn("FileLogStore: delete failed id=$id: ${t.message}")
            }
        }
    }

    /**
     * Removes on-disk entries this store does not own — bare-millis files and stray `.tmp`s
     * that share this directory — whose age is at least [minAgeMillis]. Owned `*.otlp` records
     * are left untouched so failed / too-young uploads can still retry on the next launch,
     * except for ones past the read-age ceiling or beyond the accumulation caps.
     *
     * Unlike [listReadable] the uploader also calls this when remote logging is disabled, which
     * makes it the only chance to bound records written by a session that never uploads.
     * Idempotent and safe to call repeatedly.
     *
     * @return number of unrecognized entries deleted, excluding reclaimed owned records
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun deleteUnrecognizedEntries(minAgeMillis: Long): Int =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val listed = listEntries(rootDir)
                val expired = reclaimExpiredOwnedRecords(listed, now)
                reclaimOverLimitRecords(listed.filterNot { expired.contains(it.name) }, now)
                val foreign = CrashRetention.selectUnrecognized(listed, now, minAgeMillis, policy)
                if (foreign.isEmpty()) {
                    Logging.debug("FileLogStore: no unrecognized files to purge in ${rootDir.path}")
                    return@withContext 0
                }
                var deleted = 0
                val names = mutableListOf<String>()
                for (entry in foreign) {
                    val file = File(rootDir, entry.name)
                    if (file.delete()) {
                        deleted++
                        names.add(entry.name)
                    } else {
                        Logging.warn("FileLogStore: failed to purge unrecognized file ${entry.name}")
                    }
                }
                Logging.info(
                    "FileLogStore: purged $deleted unrecognized file(s) in ${rootDir.path}: $names",
                )
                deleted
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Logging.warn("FileLogStore: deleteUnrecognizedEntries failed: ${t.message}")
                0
            }
        }
}
