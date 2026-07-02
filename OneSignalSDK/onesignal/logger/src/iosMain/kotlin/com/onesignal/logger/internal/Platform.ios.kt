package com.onesignal.logger.internal

import platform.Foundation.NSDate
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

internal actual fun epochNanosNow(): Long = (NSDate().timeIntervalSince1970 * NANOS_PER_SECOND).toLong()

internal actual fun randomUuidString(): String = NSUUID().UUIDString().lowercase()

private const val NANOS_PER_SECOND = 1_000_000_000.0
