package com.onesignal.logger

/**
 * Outbound HTTP request for log export. Body is already-encoded bytes (OTLP
 * protobuf) with the matching [contentType].
 */
data class LogHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val contentType: String,
    val body: ByteArray,
) {
    // Generated equals/hashCode for data classes with ByteArray are reference-based,
    // which is fine here (requests are not used as map keys), but override for sanity.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LogHttpRequest) return false
        return url == other.url &&
            headers == other.headers &&
            contentType == other.contentType &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }
}

/** Result of a [ILogHttpSender] call. */
data class LogHttpResponse(
    val success: Boolean,
    val statusCode: Int,
    val message: String? = null,
)

/**
 * Platform-agnostic HTTP transport for shipping logs. Implemented by the platform
 * (Android reuses the SDK's existing HTTP stack), keeping this module free of any
 * networking dependency.
 */
interface ILogHttpSender {
    suspend fun send(request: LogHttpRequest): LogHttpResponse
}
