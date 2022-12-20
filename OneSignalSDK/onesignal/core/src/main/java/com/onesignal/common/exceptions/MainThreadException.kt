package com.onesignal.common.exceptions

/**
 * Raised when processing is done on the main thread, when it should *not* be on the main thread.
 */
class MainThreadException(message: String?) : RuntimeException(message)
