package com.onesignal.debug.internal.logging.logger.android

import com.onesignal.logger.ILogFileStore
import com.onesignal.logger.StoredLogFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Android [ILogFileStore] backed by the local filesystem. Replaces OpenTelemetry's
 * `disk-buffering` contrib library with a trivial one-file-per-record format we own.
 *
 * Each crash record is written to its own file under [rootPath]. The file's last
 * modified time is used as the record age for [listReadable], mirroring the old
 * `minFileAgeForReadMillis` behavior (never read a file the crashing process may
 * still have been writing).
 */
internal class FileLogStore(
    private val rootPath: String,
) : ILogFileStore {
    private val rootDir: File get() = File(rootPath)

    private companion object {
        const val FILE_SUFFIX = ".otlp"
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun save(bytes: ByteArray): Boolean {
        return try {
            val dir = rootDir
            if (!dir.exists()) dir.mkdirs()
            // Write to a temp file then rename so a half-written file is never readable.
            val target = File(dir, "${System.currentTimeMillis()}-${UUID.randomUUID()}$FILE_SUFFIX")
            val temp = File(dir, target.name + ".tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                // Fallback: write directly if rename is unsupported on this fs.
                target.writeBytes(bytes)
                temp.delete()
            }
            true
        } catch (t: Throwable) {
            // Crash-path safety: never throw from persistence; signal failure to caller.
            false
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun listReadable(minAgeMillis: Long): List<StoredLogFile> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                rootDir.listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
                    ?.filter { now - it.lastModified() >= minAgeMillis }
                    ?.mapNotNull { file ->
                        try {
                            StoredLogFile(id = file.name, bytes = file.readBytes())
                        } catch (t: Throwable) {
                            null
                        }
                    }
                    ?: emptyList()
            } catch (t: Throwable) {
                emptyList()
            }
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            try {
                File(rootDir, id).delete()
            } catch (t: Throwable) {
                // best-effort
            }
        }
    }
}
