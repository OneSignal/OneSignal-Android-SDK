package com.onesignal.debug.internal.logging.logger.android

import android.util.Log
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.logger.ILogFileStore
import com.onesignal.logger.StoredLogFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android [ILogFileStore] backed by the local filesystem. Replaces OpenTelemetry's
 * `disk-buffering` contrib library with a trivial one-file-per-record format we own.
 *
 * Each crash record is written to its own file under [rootPath]. The file's last
 * modified time is used as the record age for [listReadable], mirroring the old
 * `minFileAgeForReadMillis` behavior (never read a file the crashing process may
 * still have been writing).
 *
 * The directory is inherited from the removed otel module, so ownership is distinguished
 * purely by [CRASH_OWNED_SUFFIX]: everything the logger writes ends in `.otlp`; anything
 * else (bare-millis files left by an otel session before upgrade, stray `.tmp`s) is
 * foreign and reclaimable via [deleteUnrecognizedEntries].
 *
 * Owned records are bounded on both axes, replacing the caps disk-buffering used to apply:
 * [CRASH_MAX_READ_AGE_MILLIS] ages records out, and [CRASH_MAX_RECORD_COUNT] /
 * [CRASH_MAX_TOTAL_BYTES] cap accumulation. Over-limit records are deleted, not just hidden
 * from [listReadable], so a record that never uploads cannot grow the cache forever.
 */
internal class FileLogStore(
    private val rootPath: String,
) : ILogFileStore {
    private val rootDir: File get() = File(rootPath)

    private companion object {
        const val TAG = "OneSignal"
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun save(bytes: ByteArray): Boolean {
        return try {
            val dir = rootDir
            if (!dir.exists()) dir.mkdirs()
            // Write to a temp file then rename so a half-written file is never readable.
            val target = File(dir, "${System.currentTimeMillis()}-${UUID.randomUUID()}$CRASH_OWNED_SUFFIX")
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
            enforceAccumulationCaps(dir)
            true
        } catch (t: Throwable) {
            // Crash-path safety: never throw from persistence; signal failure to caller.
            Log.w(TAG, "FileLogStore: save failed: ${t.message}")
            false
        }
    }

    /**
     * Evicts oldest-first until the owned records fit the accumulation caps.
     *
     * Runs inline on the crashing thread — it is a single directory listing plus at most a
     * few deletes, and deferring it would mean the write that breached the cap is the one
     * that never gets trimmed. Uses raw Logcat for the same reason [save] does.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun enforceAccumulationCaps(dir: File) {
        try {
            val overflow = selectOverflowOwnedEntries(listEntries(dir))
            if (overflow.isEmpty()) return
            var evicted = 0
            for (entry in overflow) {
                if (File(dir, entry.name).delete()) evicted++
            }
            Log.i(TAG, "FileLogStore: evicted $evicted over-cap record(s) in ${dir.path}")
        } catch (t: Throwable) {
            // Never let cache trimming turn a successful crash write into a failure.
            Log.w(TAG, "FileLogStore: cap enforcement failed: ${t.message}")
        }
    }

    private fun listEntries(dir: File): List<CrashDirEntry> =
        dir.listFiles()?.filter { it.isFile }?.map { file ->
            CrashDirEntry(
                name = file.name,
                lastModifiedMs = file.lastModified(),
                lengthBytes = file.length(),
            )
        }.orEmpty()

    /**
     * Deletes owned records past [CRASH_MAX_READ_AGE_MILLIS]. Called from both read paths so
     * over-age records are reclaimed even when remote logging is off and the uploader never
     * gets as far as [listReadable].
     *
     * @return names of the expired records, whether or not the delete succeeded — a record
     *   past the ceiling must not be read even if it could not be removed this pass
     */
    private fun reclaimExpiredOwnedRecords(entries: List<CrashDirEntry>, nowMs: Long): Set<String> {
        val expired = selectExpiredOwnedEntries(entries, nowMs)
        if (expired.isEmpty()) return emptySet()
        var deleted = 0
        for (entry in expired) {
            if (File(rootDir, entry.name).delete()) {
                deleted++
            } else {
                Logging.warn("FileLogStore: failed to reclaim expired record ${entry.name}")
            }
        }
        Logging.info("FileLogStore: reclaimed $deleted expired record(s) in ${rootDir.path}")
        return expired.mapTo(HashSet()) { it.name }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun listReadable(minAgeMillis: Long): List<StoredLogFile> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val entries = listEntries(rootDir)
                val expired = reclaimExpiredOwnedRecords(entries, now)
                val suffixMatches =
                    entries.filter { isOwnedCrashFile(it.name) && !expired.contains(it.name) }
                val readable =
                    suffixMatches
                        .filter { now - it.lastModifiedMs >= minAgeMillis }
                        .mapNotNull { entry -> readRecord(File(rootDir, entry.name)) }
                Logging.debug(
                    "FileLogStore: listReadable minAgeMs=$minAgeMillis total=${entries.size} " +
                        "suffix=${suffixMatches.size} readable=${readable.size} expired=${expired.size} " +
                        "legacy=${entries.count { !isOwnedCrashFile(it.name) }}",
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
     * Removes on-disk entries this store does not own — legacy OTEL disk-buffering
     * files (bare-millis names) and stray `.tmp`s that share this directory — whose
     * age is at least [minAgeMillis]. Owned `*.otlp` records are left untouched so failed
     * / too-young uploads can still retry on the next launch, except for ones past
     * [CRASH_MAX_READ_AGE_MILLIS], which are no longer uploadable.
     *
     * Implements the shared [ILogFileStore] contract: the KMP `LogCrashUploader`
     * invokes this after its owned-record upload pass, and — unlike [listReadable] —
     * also when remote logging is disabled, which is the only chance to age out records
     * written by a session that never uploads. Idempotent and safe to call repeatedly.
     *
     * @return number of unrecognized entries deleted, excluding expired owned records
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun deleteUnrecognizedEntries(minAgeMillis: Long): Int =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val listed = listEntries(rootDir)
                reclaimExpiredOwnedRecords(listed, now)
                val foreign = selectUnrecognizedEntries(listed, now, minAgeMillis)
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
