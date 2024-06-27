package com.onesignal.core.internal.minification

/**
 * Purpose: Use on stub classes that are expected to always exists when
 * aggressive minification is enabled on the app.
 *     - Such as android.enableR8.fullMode.
 */
annotation class KeepStub
