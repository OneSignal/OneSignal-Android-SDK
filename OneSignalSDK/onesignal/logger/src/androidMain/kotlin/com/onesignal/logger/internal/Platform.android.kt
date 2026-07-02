package com.onesignal.logger.internal

import java.util.UUID

internal actual fun epochNanosNow(): Long = System.currentTimeMillis() * NANOS_PER_MILLI

internal actual fun randomUuidString(): String = UUID.randomUUID().toString()

private const val NANOS_PER_MILLI = 1_000_000L
