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
 * Android [ILogFileStore]: one crash record per file under [rootPath], bounded entirely by the
 * shared [CrashRetention] policy. Ownership is by suffix — this store writes `.otlp`.
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
                Log.w(
                    TAG,
                    "FileLogStore: refusing record of ${bytes.size} bytes, " +
                        "over the ${policy.maxRecordBytes}-byte limit",
                )
                return false
            }
            val dir = rootDir
            if (!dir.exists()) dir.mkdirs()
            // Temp-then-rename so a half-written file is never readable. Both names lead with millis
            // under the policy's suffixes, which is what keeps an interrupted write reclaimable.
            val base = "${System.currentTimeMillis()}-${UUID.randomUUID()}"
            val target = File(dir, base + policy.ownedSuffix)
            val temp = File(dir, base + policy.ownedTempSuffix)
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                // Fallback: write directly if rename is unsupported on this fs.
                target.writeBytes(bytes)
                temp.delete()
            }
            // Crash path: raw Logcat only — a Logging call invokes app listeners synchronously,
            // and a throwing listener would flip a successful write to false.
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
     * Evicts oldest-first until owned records fit the caps, always keeping [keepNames]. Runs inline
     * on the crashing thread, so it must not throw and reports through raw Logcat only.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun enforceAccumulationCaps(dir: File, keepNames: Set<String>) {
        try {
            val entries = listEntries(dir)
            // Cheap exit: the selector sorts the whole directory, so only reach it when over cap.
            // keepNames must match the selector's — it excuses protected records their byte claim,
            // so a check that charged them would sort and then trim nothing.
            if (CrashRetention.isWithinCaps(entries, keepNames, policy)) return
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
     * Deletes owned records over the caps — deleted, not merely withheld from [listReadable].
     * @return names of the evicted records, so callers can exclude them from the same pass
     */
    private fun reclaimOverLimitRecords(entries: List<CrashDirEntry>, nowMs: Long): Set<String> {
        // Android has no in-flight write registry yet (SDK-5129), so nothing here can be protected. The
        // only exposure is save()'s renameTo fallback; the normal path renames, so .otlp is never partial.
        val overflow = CrashRetention.selectOverflowOwned(entries, nowMs, keepNames = emptySet(), policy = policy)
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
     * `File.lastModified()` reports an I/O error as `0`, indistinguishable from a real epoch mtime.
     * Report non-positive as unknown so the policy dates from the name; never pass it as an age.
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
     * Deletes owned records past the policy's read-age ceiling.
     * @return every expired name, deleted or not — one that could not be removed must still not be read
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

    /**
     * Ages records via [CrashRetention.effectiveWriteTimeMs], so a file the crashing process may
     * still be writing is never read. An undatable record is withheld, never deleted: it may be mid-write.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun listReadable(minAgeMillis: Long): List<StoredLogFile> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val entries = listEntries(rootDir)
                // Reclaim before reading, so an over-cap backlog is never fully materialized.
                val expired = reclaimExpiredOwnedRecords(entries, now)
                val evicted = reclaimOverLimitRecords(entries.filterNot { expired.contains(it.name) }, now)
                val dropped = expired + evicted
                val suffixMatches =
                    entries.filter { CrashRetention.isOwned(it.name, policy) && !dropped.contains(it.name) }
                // Same helper the reclaim passes above use, or a record could be withheld here by
                // one clock while being deleted by another.
                val writeTimes = suffixMatches.map { it to CrashRetention.effectiveWriteTimeMs(it, policy) }
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
     * Also applies the owned bounds: with remote logging off this is the only pass that runs.
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
