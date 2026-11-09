package com.onesignal.internal

import android.content.Context
import com.onesignal.IOneSignal
import com.onesignal.debug.IDebugManager
import com.onesignal.debug.internal.DebugManager
import com.onesignal.debug.internal.logging.Logging

/** Process-wide SDK choice. Delete this with [NeverCrashKillSwitch]. */
internal object OneSignalChoice {
    val isInitialized: Boolean
        get() = chosen?.isInitialized ?: false

    val debug: IDebugManager
        get() = debugManager

    var consentRequired: Boolean
        get() = readBeforeChoice(pendingConsentRequired) { it.consentRequired }
        set(value) = setBeforeChoice(value, { pendingConsentRequired = it }) { it.consentRequired = value }

    var consentGiven: Boolean
        get() = readBeforeChoice(pendingConsentGiven) { it.consentGiven }
        set(value) = setBeforeChoice(value, { pendingConsentGiven = it }) { it.consentGiven = value }

    var disableGMSMissingPrompt: Boolean
        get() = readBeforeChoice(pendingDisableGmsMissingPrompt) { it.disableGMSMissingPrompt }
        set(value) =
            setBeforeChoice(value, { pendingDisableGmsMissingPrompt = it }) { it.disableGMSMissingPrompt = value }

    suspend fun getConsentRequired(): Boolean = readSuspend({ pendingConsentRequired }) { it.getConsentRequired() }

    suspend fun setConsentRequired(required: Boolean) {
        writeSuspend(required, { pendingConsentRequired = it }) { it.setConsentRequired(required) }
    }

    suspend fun getConsentGiven(): Boolean = readSuspend({ pendingConsentGiven }) { it.getConsentGiven() }

    suspend fun setConsentGiven(value: Boolean) {
        writeSuspend(value, { pendingConsentGiven = it }) { it.setConsentGiven(value) }
    }

    suspend fun getDisableGMSMissingPrompt(): Boolean =
        readSuspend({ pendingDisableGmsMissingPrompt }) { it.getDisableGMSMissingPrompt() }

    suspend fun setDisableGMSMissingPrompt(value: Boolean) {
        writeSuspend(value, { pendingDisableGmsMissingPrompt = it }) { it.setDisableGMSMissingPrompt(value) }
    }

    // The startup provider usually evaluated already. Otherwise only a Context passed to init can.
    fun resolve(context: Context? = null): IOneSignal {
        chosen?.let { return it }
        synchronized(choiceLock) {
            chosen?.let { return it }
            if (context != null) NeverCrashKillSwitch.evaluate(context)
            // Construction resets log levels. Put back whatever was set before the choice.
            val keptLogLevel = Logging.logLevel
            val keptVisualLogLevel = Logging.visualLogLevel
            val impl = NeverCrashKillSwitch.create()
            Logging.logLevel = keptLogLevel
            Logging.visualLogLevel = keptVisualLogLevel
            pendingConsentRequired?.let { impl.consentRequired = it }
            pendingConsentGiven?.let { impl.consentGiven = it }
            pendingDisableGmsMissingPrompt?.let { impl.disableGMSMissingPrompt = it }
            chosen = impl
            return impl
        }
    }

    @Volatile
    private var chosen: IOneSignal? = null

    private val choiceLock = Any()

    @Volatile
    private var pendingConsentRequired: Boolean? = null

    @Volatile
    private var pendingConsentGiven: Boolean? = null

    @Volatile
    private var pendingDisableGmsMissingPrompt: Boolean? = null

    private val debugManager: IDebugManager by lazy { DebugManager() }

    private fun readBeforeChoice(pending: Boolean?, read: (IOneSignal) -> Boolean): Boolean {
        chosen?.let { return read(it) }
        val impl = synchronized(choiceLock) { chosen ?: return pending == true }
        return read(impl)
    }

    private fun setBeforeChoice(value: Boolean, stash: (Boolean) -> Unit, write: (IOneSignal) -> Unit) {
        val impl = synchronized(choiceLock) {
            val current = chosen
            if (current == null) stash(value)
            current
        }
        impl?.let(write)
    }

    private suspend fun readSuspend(pending: () -> Boolean?, read: suspend (IOneSignal) -> Boolean): Boolean {
        val impl = synchronized(choiceLock) { chosen } ?: return pending() == true
        return read(impl)
    }

    private suspend fun writeSuspend(value: Boolean, stash: (Boolean) -> Unit, write: suspend (IOneSignal) -> Unit) {
        val impl = synchronized(choiceLock) {
            val current = chosen
            if (current == null) stash(value)
            current
        }
        if (impl != null) write(impl)
    }
}
