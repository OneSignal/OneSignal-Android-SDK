package com.onesignal.logger.internal

/**
 * Current wall-clock time in nanoseconds since the Unix epoch.
 *
 * Resolution is platform-dependent (typically millisecond precision scaled to
 * nanos); only used for OTLP `timeUnixNano` stamping.
 */
internal expect fun epochNanosNow(): Long

/** A random UUID string (lowercase, hyphenated). Used for log record idempotency. */
internal expect fun randomUuidString(): String
