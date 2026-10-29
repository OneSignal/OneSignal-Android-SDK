package com.onesignal.example.util

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * The SDK's pending ask for a JWT. App-scoped so the listener MainApplication registers at
 * launch reaches the UI whether or not MainActivity exists. The SDK doesn't replay the event.
 * Every write goes through the main handler, so they land in call order and an [answered] or
 * [clear] called after a 401 wins over it.
 */
object JwtAskStore {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _externalId = MutableLiveData<String?>()

    /** External id the SDK is waiting on a JWT for, or null. */
    val externalId: LiveData<String?> = _externalId

    /**
     * Called from IUserJwtInvalidatedListener, which the SDK runs on a background thread.
     * Replaces any earlier ask. The stored token stays. An SDK that asks because requests parked
     * without a token fires this event too, so it doesn't prove the stored token is bad. A token
     * that is bad shows up as a 401 in the REST fetch row.
     */
    fun onInvalidated(externalId: String) {
        mainHandler.post {
            _externalId.value = externalId
        }
    }

    /** A token for [externalId] answers the ask if it is for that user. */
    fun answered(externalId: String) {
        mainHandler.post {
            if (_externalId.value == externalId) {
                _externalId.value = null
            }
        }
    }

    fun clear() {
        mainHandler.post {
            _externalId.value = null
        }
    }
}
