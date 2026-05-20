package com.onesignal.example.util

import com.onesignal.example.BuildConfig

private const val MASK_CHAR = '•'

/**
 * Masks sensitive display values (App ID, Push ID, etc.) when running under
 * E2E automation so recordings and screenshots do not leak per-tenant data.
 * Mirrors the Capacitor demo's `maskValue` helper gated on `VITE_E2E_MODE`:
 * preserve the original length and use `•` so the masked layout matches the
 * unmasked one (avoids width-shifts in screenshots/specs).
 */
fun maskValue(value: String?): String {
    if (value.isNullOrEmpty()) return value.orEmpty()
    if (BuildConfig.E2E_MODE && value != "—") {
        return MASK_CHAR.toString().repeat(value.length)
    }
    return value
}
