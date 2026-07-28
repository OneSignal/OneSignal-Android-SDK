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
 * The logger and the legacy otel module share this one crash directory, so ownership
 * is distinguished purely by [CRASH_OWNED_SUFFIX]: everything the logger writes ends in
 * `.otlp`; anything else (legacy otel bare-millis files, stray `.tmp`s) is foreign and
 * reclaimable via [deleteUnrecognizedEntries] once the logger is the active module.
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
            true
        } catch (t: Throwable) {
            // Crash-path safety: never throw from persistence; signal failure to caller.
            Log.w(TAG, "FileLogStore: save failed: ${t.message}")
            false
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun listReadable(minAgeMillis: Long): List<StoredLogFile> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val allFiles = rootDir.listFiles()?.filter { it.isFile }.orEmpty()
                val suffixMatches = allFiles.filter { isOwnedCrashFile(it.name) }
                val readable =
                    suffixMatches
                        .filter { now - it.lastModified() >= minAgeMillis }
                        .mapNotNull { file -> readRecord(file) }
                Logging.debug(
                    "FileLogStore: listReadable minAgeMs=$minAgeMillis total=${allFiles.size} " +
                        "suffix=${suffixMatches.size} readable=${readable.size} " +
                        "legacy=${allFiles.size - suffixMatches.size}",
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
     * age is at least [minAgeMillis]. All `*.otlp` owned records are left untouched
     * so failed / too-young uploads can still retry on the next launch.
     *
     * Implements the shared [ILogFileStore] contract: the KMP `LogCrashUploader`
     * invokes this after its owned-record upload pass. Idempotent and safe to call
     * repeatedly.
     *
     * @return number of unrecognized entries deleted
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun deleteUnrecognizedEntries(minAgeMillis: Long): Int =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val listed =
                    rootDir.listFiles()?.filter { it.isFile }?.map { file ->
                        CrashDirEntry(
                            name = file.name,
                            lastModifiedMs = file.lastModified(),
                            lengthBytes = file.length(),
                        )
                    }.orEmpty()
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
