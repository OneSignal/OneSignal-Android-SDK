package com.onesignal.logger

/**
 * A stored crash record on disk.
 *
 * @property id opaque identifier (the platform decides what this is — e.g. a file
 *   name). Used to [ILogFileStore.read] and [ILogFileStore.delete] the entry.
 * @property bytes the encoded payload that was written.
 */
data class StoredLogFile(
    val id: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredLogFile) return false
        return id == other.id && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * id.hashCode() + bytes.contentHashCode()
}

/**
 * Platform-agnostic durable store for crash records.
 *
 * Replaces OpenTelemetry's `disk-buffering` contrib library. The on-disk format is
 * entirely owned by this module (each record is one encoded OTLP/JSON payload), so
 * the same logic works on any platform that can provide simple file primitives.
 *
 * Implementations must be safe to call from a crash path (i.e. cheap, no heavy
 * initialization).
 */
interface ILogFileStore {
    /** Persists [bytes] under a newly generated entry. */
    fun save(bytes: ByteArray)

    /**
     * Returns all readable entries whose age is at least [minAgeMillis]. The age
     * gate mirrors `minFileAgeForReadMillis` from the old pipeline: it guarantees
     * we never read a file that may still be mid-write from the crashing process.
     */
    fun listReadable(minAgeMillis: Long): List<StoredLogFile>

    /** Deletes the entry with the given [id]. Safe to call if already gone. */
    fun delete(id: String)
}
