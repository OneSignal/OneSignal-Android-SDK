package com.onesignal.session.internal.session.queue.impl

import com.onesignal.common.IDManager
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.Model
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.backend.ISessionsBackendService
import com.onesignal.session.internal.session.queue.ISessionsApiQueue
import com.onesignal.session.internal.session.queue.SessionRecord
import com.onesignal.session.internal.session.queue.SessionRecordStore
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.identity.IdentityModelStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.reflect.KMutableProperty1

internal class SessionsApiQueue(
    private val store: SessionRecordStore,
    backend: ISessionsBackendService,
    deviceService: IDeviceService,
    private val identityModelStore: IdentityModelStore,
    private val configModelStore: ConfigModelStore,
    applicationService: IApplicationService,
    time: ITime,
) : ISessionsApiQueue, IStartableService {
    private val lock = Any()
    private val sendMutex = Mutex()
    private val wakeChannel = Channel<Unit>(Channel.CONFLATED)
    internal val backoff = SessionsApiBackoff(time)
    private val sender = SessionsApiSender(store, backend, deviceService, backoff, lock)
    private val retryTrigger = SessionsApiRetryTrigger(applicationService, ::retryNow)

    override fun start() {
        identityModelStore.subscribe(LocalIdTranslator(IdentityConstants.ONESIGNAL_ID, SessionRecord::onesignalId))
        // Login before the anonymous user is created carries the local push subscription over to
        // the new user, so its backend ID belongs to that user, not the one pinned on the record.
        configModelStore.subscribe(
            LocalIdTranslator(ConfigModel::pushSubscriptionId.name, SessionRecord::subscriptionId) {
                it.onesignalId == identityModelStore.currentOnesignalId()
            },
        )
        retryTrigger.start()
        OneSignalDispatchers.launchOnIO {
            while (true) {
                val wait = backoff.waitMillis
                if (wait > 0) {
                    withTimeoutOrNull(wait) { wakeChannel.receive() }
                    continue
                }
                sendPending()
                if (backoff.waitMillis <= 0) wakeChannel.receive()
            }
        }
    }

    override fun enqueueCreate(
        sessionId: String,
        appId: String,
        onesignalId: String,
        subscriptionId: String,
        startTimeMillis: Long,
    ) {
        val record =
            SessionRecord().also {
                it.id = sessionId
                it.appId = appId
                it.onesignalId = onesignalId
                it.subscriptionId = subscriptionId
                it.startTime = startTimeMillis / MILLIS_PER_SECOND
                it.createKey = UUID.randomUUID().toString()
            }
        synchronized(lock) {
            val records = store.list()
            if (records.size >= MAX_RECORDS) {
                Logging.warn("SessionsApiQueue: queue full, dropping session ${records.first().id}")
                store.remove(records.first().id)
            }
            store.add(record)
        }
        wakeChannel.trySend(Unit)
    }

    override fun enqueueUpdate(
        sessionId: String,
        durationMillis: Long,
        endTimeMillis: Long?,
    ) {
        synchronized(lock) {
            val record = store.get(sessionId)
            if (record == null || record.hasEnded) {
                Logging.debug("SessionsApiQueue: ignoring update for session $sessionId, not queued or already ended")
                return
            }
            record.durationSeconds = durationMillis / MILLIS_PER_SECOND
            record.endTime = endTimeMillis?.let { it / MILLIS_PER_SECOND }
            record.updateKey = UUID.randomUUID().toString()
        }
        wakeChannel.trySend(Unit)
    }

    override suspend fun flush() {
        backoff.skipFailureDelay()
        if (!backoff.isWaitingOnRetryAfter) sendPending()
    }

    /** Ends the failure backoff early. Still waits out a backend `Retry-After`. */
    fun retryNow() {
        if (backoff.skipFailureDelay()) wakeChannel.trySend(Unit)
    }

    private suspend fun sendPending() =
        sendMutex.withLock {
            for (record in store.list()) {
                if (hasBackendIds(record) && !sender.send(record)) return@withLock
            }
        }

    /**
     * A local ID is replaced once the user or subscription is created. One that is no longer
     * current never will be, so the record can never be sent.
     */
    private fun hasBackendIds(record: SessionRecord): Boolean {
        val localIds = listOf(record.onesignalId, record.subscriptionId).filter(IDManager::isLocalId)
        if (localIds.isEmpty()) return true
        val currentIds = setOf(identityModelStore.currentOnesignalId(), configModelStore.model.pushSubscriptionId)
        if (!currentIds.containsAll(localIds)) {
            Logging.warn("SessionsApiQueue: dropping session ${record.id}, its local IDs will never be created")
            synchronized(lock) { store.remove(record.id) }
        }
        return false
    }

    /** Carries a backend ID that replaces a local ID in place over to the queued records. */
    private inner class LocalIdTranslator<TModel : Model>(
        private val sourceProperty: String,
        private val recordId: KMutableProperty1<SessionRecord, String>,
        private val appliesTo: (SessionRecord) -> Boolean = { true },
    ) : ISingletonModelStoreChangeHandler<TModel> {
        override fun onModelReplaced(
            model: TModel,
            tag: String,
        ) = Unit

        override fun onModelUpdated(
            args: ModelChangedArgs,
            tag: String,
        ) {
            val localId = (args.oldValue as? String)?.takeIf { args.property == sourceProperty && IDManager.isLocalId(it) }
            val backendId = (args.newValue as? String)?.takeUnless { IDManager.isLocalId(it) }
            if (localId == null || backendId == null) return

            synchronized(lock) {
                store.list().filter { recordId.get(it) == localId && appliesTo(it) }.forEach { recordId.set(it, backendId) }
            }
            wakeChannel.trySend(Unit)
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
        const val MAX_RECORDS = 100
    }
}

private fun IdentityModelStore.currentOnesignalId(): String? =
    model.takeIf { it.hasProperty(IdentityConstants.ONESIGNAL_ID) }?.onesignalId
