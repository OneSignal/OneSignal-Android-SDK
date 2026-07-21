package com.onesignal.logger

/**
 * A tiny, independent protobuf reader used only by tests to verify [
 * com.onesignal.logger.otlp.OtlpLogEncoder] output. It is intentionally written
 * separately from the production `ProtoWriter` so the tests validate the wire format
 * (field numbers, wire types, values) rather than round-tripping through the same code.
 */
internal class ProtoMessage(private val fields: List<ProtoField>) {
    fun all(fieldNumber: Int): List<ProtoField> = fields.filter { it.number == fieldNumber }

    fun first(fieldNumber: Int): ProtoField =
        fields.firstOrNull { it.number == fieldNumber }
            ?: error("field $fieldNumber not present (have ${fields.map { it.number }})")

    fun message(fieldNumber: Int): ProtoMessage = parseProto(first(fieldNumber).bytes())

    fun string(fieldNumber: Int): String = first(fieldNumber).bytes().decodeToString()
}

internal class ProtoField(
    val number: Int,
    val wireType: Int,
    val varint: Long,
    private val raw: ByteArray?,
) {
    fun bytes(): ByteArray = raw ?: error("field $number is not length-delimited")
}

internal fun parseProto(bytes: ByteArray): ProtoMessage {
    val fields = ArrayList<ProtoField>()
    var index = 0

    fun readVarint(): Long {
        var shift = 0
        var result = 0L
        while (true) {
            val byte = bytes[index++].toInt() and 0xFF
            result = result or ((byte.toLong() and 0x7F) shl shift)
            if (byte < 0x80) break
            shift += 7
        }
        return result
    }

    fun readFixed(byteCount: Int): Long {
        var value = 0L
        for (i in 0 until byteCount) {
            value = value or ((bytes[index++].toLong() and 0xFF) shl (8 * i))
        }
        return value
    }

    while (index < bytes.size) {
        val tag = readVarint().toInt()
        val fieldNumber = tag ushr 3
        when (val wireType = tag and 0x7) {
            0 -> fields.add(ProtoField(fieldNumber, wireType, readVarint(), null))
            1 -> fields.add(ProtoField(fieldNumber, wireType, readFixed(Long.SIZE_BYTES), null))
            2 -> {
                val length = readVarint().toInt()
                val slice = bytes.copyOfRange(index, index + length)
                index += length
                fields.add(ProtoField(fieldNumber, wireType, 0, slice))
            }
            5 -> fields.add(ProtoField(fieldNumber, wireType, readFixed(Int.SIZE_BYTES), null))
            else -> error("unsupported wire type $wireType")
        }
    }
    return ProtoMessage(fields)
}
