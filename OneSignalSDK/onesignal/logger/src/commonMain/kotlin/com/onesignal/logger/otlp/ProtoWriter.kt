package com.onesignal.logger.otlp

/**
 * Minimal, dependency-free protobuf wire-format writer.
 *
 * Only the pieces the OTLP logs subset needs are implemented: varint, fixed64, and
 * length-delimited (string/bytes/embedded-message) fields. This lets us produce the
 * exact same wire format the OpenTelemetry Java SDK's OTLP/HTTP exporter emits
 * (`Content-Type: application/x-protobuf`) from pure `commonMain` Kotlin, with no
 * OpenTelemetry or serialization dependency.
 *
 * See https://protobuf.dev/programming-guides/encoding/ for the wire format.
 */
internal class ProtoWriter {
    private var buffer = ByteArray(INITIAL_CAPACITY)
    private var position = 0

    private fun ensureCapacity(extra: Int) {
        if (position + extra <= buffer.size) return
        var newSize = buffer.size * 2
        while (newSize < position + extra) newSize *= 2
        buffer = buffer.copyOf(newSize)
    }

    /** Writes a base-128 varint (used for tags, enums, and lengths). */
    private fun writeVarint(value: Long) {
        ensureCapacity(VARINT_MAX_BYTES)
        var remaining = value
        while (true) {
            val lower7 = (remaining and 0x7F).toInt()
            remaining = remaining ushr 7
            if (remaining != 0L) {
                buffer[position++] = (lower7 or 0x80).toByte()
            } else {
                buffer[position++] = lower7.toByte()
                break
            }
        }
    }

    private fun writeTag(fieldNumber: Int, wireType: Int) {
        writeVarint(((fieldNumber shl 3) or wireType).toLong())
    }

    /** Writes a `fixed64` field (little-endian 8 bytes) — used for OTLP timestamps. */
    fun writeFixed64(fieldNumber: Int, value: Long) {
        writeTag(fieldNumber, WIRE_TYPE_FIXED64)
        ensureCapacity(Long.SIZE_BYTES)
        var remaining = value
        repeat(Long.SIZE_BYTES) {
            buffer[position++] = (remaining and 0xFF).toByte()
            remaining = remaining ushr 8
        }
    }

    /** Writes a varint-encoded field — used for OTLP `severity_number` (an enum). */
    fun writeVarintField(fieldNumber: Int, value: Long) {
        writeTag(fieldNumber, WIRE_TYPE_VARINT)
        writeVarint(value)
    }

    /** Writes a length-delimited field (bytes / embedded message). */
    fun writeLengthDelimited(fieldNumber: Int, value: ByteArray) {
        writeTag(fieldNumber, WIRE_TYPE_LENGTH_DELIMITED)
        writeVarint(value.size.toLong())
        ensureCapacity(value.size)
        value.copyInto(buffer, position)
        position += value.size
    }

    /** Writes a length-delimited UTF-8 string field. */
    fun writeString(fieldNumber: Int, value: String) = writeLengthDelimited(fieldNumber, value.encodeToByteArray())

    fun toByteArray(): ByteArray = buffer.copyOf(position)

    companion object {
        private const val INITIAL_CAPACITY = 64
        private const val VARINT_MAX_BYTES = 10

        const val WIRE_TYPE_VARINT = 0
        const val WIRE_TYPE_FIXED64 = 1
        const val WIRE_TYPE_LENGTH_DELIMITED = 2
    }
}
