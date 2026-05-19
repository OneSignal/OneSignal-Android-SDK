package com.onesignal.sdktest.util

import com.onesignal.sdktest.BuildConfig

/**
 * Masks sensitive display values (App ID, Push ID, etc.) when running under
 * E2E automation so recordings and screenshots do not leak per-tenant data.
 * Mirrors the Capacitor demo's `maskValue` helper gated on `VITE_E2E_MODE`.
 */
fun maskValue(value: String?): String =
    if (BuildConfig.E2E_MODE && !value.isNullOrEmpty()) "***" else value.orEmpty()
