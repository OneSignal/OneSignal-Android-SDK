package com.onesignal.session.internal.session.requests.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.onesignal.common.NetworkUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.runOnSerialIO
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.SessionModelStore
import com.onesignal.session.internal.session.backend.CreateSessionRequest
import com.onesignal.session.internal.session.backend.ISessionsBackendService
import com.onesignal.session.internal.session.backend.UpdateSessionRequest
import com.onesignal.session.internal.session.requests.ISessionRequestQueue
import com.onesignal.session.internal.session.requests.SessionRequest
import com.onesignal.session.internal.session.requests.SessionRequestStore
import com.onesignal.user.internal.operations.impl.states.NewRecordsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.math.pow
import kotlin.random.Random

/**
 * Sends one request at a time, oldest first among those ready: a create waits for backend user and
 * subscription IDs, and an update waits for its session's create. A request that fails is retried
 * with backoff without holding up the others.
 */
@Suppress("TooManyFunctions")
internal class SessionRequestQueue(
    private val store: SessionRequestStore,
    private val sessionsBackend: ISessionsBackendService,
    private val deviceService: IDeviceService,
    private val newRecordsState: NewRecordsState,
    private val applicationService: IApplicationService,
    private val sessionModelStore: SessionModelStore,
    private val time: ITime,
) : ISessionRequestQueue, IStartableService, IApplicationLifecycleHandler {
    private val lock = Any()
    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)
    private var inFlightId: String? = null

    // Backoff is per run; a restart retries saved requests right away.
    private val retryAt = mutableMapOf<String, Long>()
    private val attempts = mutableMapOf<String, Int>()
    private val waiters = mutableMapOf<String, MutableList<CompletableDeferred<Boolean>>>()

    // A create can succeed after the session's next update was built without its server ID.
    private val createdSessionIds = mutableMapOf<String, String>()

    override fun start() {
        applicationService.addApplicationLifecycleHandler(this)
        registerNetworkCallback()
        OneSignalDispatchers.launchOnIO { processForever() }
    }

    override fun enqueue(request: SessionRequest) {
        admit(request, waiter = null)
    }

    override suspend fun enqueueAndWait(request: SessionRequest): Boolean {
        val waiter = CompletableDeferred<Boolean>()
        admit(request, waiter)
        return waiter.await()
    }

    override fun translateOnesignalId(
        localId: String,
        backendId: String,
    ) = translate { if (it.onesignalId == localId) it.onesignalId = backendId }

    override fun translateSubscriptionId(
        localId: String,
        backendId: String,
        onesignalId: String?,
    ) = translate { if (it.subscriptionId == localId && it.onesignalId == onesignalId) it.subscriptionId = backendId }

    private fun translate(apply: (SessionRequest) -> Unit) {
        synchronized(lock) { store.list().forEach(apply) }
        wake()
    }

    override fun onFocus(firedOnSubscribe: Boolean) = retryNow()

    override fun onUnfocused() = Unit

    private fun retryNow() {
        synchronized(lock) { retryAt.clear() }
        wake()
    }

    private fun wake() {
        wakeSignal.trySend(Unit)
    }

    private fun admit(
        request: SessionRequest,
        waiter: CompletableDeferred<Boolean>?,
    ) {
        synchronized(lock) {
            waiter?.let { waiters.getOrPut(request.id) { mutableListOf() }.add(it) }
            if (!request.isCreate && !admitUpdate(request)) {
                complete(request.id, sent = false)
                return
            }
            store.add(request)
            enforceCap()
        }
        wake()
    }

    /**
     * Folds unsent updates for the same session into [incoming], which then carries the highest
     * duration. An end replaces them the same way.
     *
     * @return false if [incoming] should be discarded: its session's end is already queued or
     * sending, or the session has no server ID and no queued create, so it could never be sent.
     */
    private fun admitUpdate(incoming: SessionRequest): Boolean {
        val requests = store.list()
        val updates = requests.filter { !it.isCreate && it.isSameSession(incoming) }
        if (incoming.serverSessionId == null) incoming.serverSessionId = createdSessionIds[incoming.localSessionId]
        val hasEnd = updates.any { it.isEnd }
        val isOrphaned = incoming.serverSessionId == null && requests.none { it.isCreate && it.isSameSession(incoming) }
        if (hasEnd || isOrphaned) {
            Logging.debug("SessionRequestQueue: discarding $incoming, session ended: $hasEnd, never created: $isOrphaned")
            return false
        }
        coalesce(incoming, updates.filter { it.id != inFlightId })
        return true
    }

    private fun coalesce(
        incoming: SessionRequest,
        unsent: List<SessionRequest>,
    ) {
        if (unsent.isEmpty()) return
        incoming.activeDuration = maxOf(incoming.activeDuration, unsent.maxOf { it.activeDuration })
        // Otherwise a session that keeps sending updates would never reach the attempt limit.
        incoming.failedAttempts = maxOf(incoming.failedAttempts, unsent.maxOf { it.failedAttempts })
        unsent.forEach { replaced ->
            remove(replaced)
            waiters.remove(replaced.id)?.let { waiters.getOrPut(incoming.id) { mutableListOf() }.addAll(it) }
        }
        Logging.debug("SessionRequestQueue: coalesced ${unsent.size} unsent update(s) for session ${incoming.localSessionId}")
    }

    private fun enforceCap() {
        while (true) {
            val unsent = store.list().filter { it.id != inFlightId }
            if (unsent.size <= MAX_QUEUED_REQUESTS) return

            val oldest = unsent.first()
            Logging.warn("SessionRequestQueue: more than $MAX_QUEUED_REQUESTS requests queued, dropping oldest: $oldest")
            drop(oldest)
        }
    }

    internal suspend fun processForever() {
        while (true) {
            val next = synchronized(lock) { nextReady()?.also { inFlightId = it.id } }
            if (next == null) {
                val waitMs = synchronized(lock) { nextWakeDelay() }
                if (waitMs == null) wakeSignal.receive() else withTimeoutOrNull(waitMs) { wakeSignal.receive() }
                continue
            }
            try {
                send(next)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                Logging.error("SessionRequestQueue: error sending $next, dropping", e)
                synchronized(lock) { drop(next) }
            } finally {
                synchronized(lock) { inFlightId = null }
            }
        }
    }

    private fun nextReady(): SessionRequest? {
        val now = time.currentTimeMillis
        return store.list().firstOrNull { request ->
            (retryAt[request.id] ?: 0L) <= now && hasAccessibleIds(request)
        }
    }

    private fun hasAccessibleIds(request: SessionRequest): Boolean {
        val serverSessionId = request.serverSessionId
        if (!request.isCreate && (serverSessionId == null || !newRecordsState.canAccess(serverSessionId))) return false
        return request.hasBackendIds &&
            newRecordsState.canAccess(request.onesignalId) &&
            newRecordsState.canAccess(request.subscriptionId)
    }

    /**
     * @return how long until a waiting request may become ready, or null to wait for a wake.
     */
    private fun nextWakeDelay(): Long? {
        val now = time.currentTimeMillis
        val pending = store.list()
        val untilRetry = pending.mapNotNull { retryAt[it.id] }.minOrNull()?.let { (it - now).coerceAtLeast(0L) }
        // NewRecordsState doesn't expose when a record becomes accessible, so poll for it.
        val awaitingNewRecord = pending.any { (retryAt[it.id] ?: 0L) <= now && it.hasBackendIds && !hasAccessibleIds(it) }
        val untilNewRecord = if (awaitingNewRecord) NEW_RECORD_POLL_MS else null
        return listOfNotNull(untilRetry, untilNewRecord).minOrNull()
    }

    private suspend fun send(request: SessionRequest) {
        try {
            if (request.isCreate) sendCreate(request) else sendUpdate(request)
        } catch (ex: BackendException) {
            synchronized(lock) { onFailure(request, ex) }
        }
    }

    private suspend fun sendCreate(request: SessionRequest) {
        val serverSessionId =
            sessionsBackend.createSession(
                request.appId,
                CreateSessionRequest(
                    onesignalId = request.onesignalId,
                    subscriptionId = request.subscriptionId,
                    deviceType = deviceService.deviceType.value,
                    startTime = MILLISECONDS.toSeconds(request.startTime),
                    idempotencyKey = request.idempotencyKey,
                ),
            )
        synchronized(lock) {
            if (serverSessionId == null) {
                // Retrying with the same idempotency key lets the backend return the session it already created.
                Logging.warn("SessionRequestQueue: create session response is missing data.session_id")
                retryOrDrop(request, backendResponded = true, retryAfterSeconds = null)
            } else {
                onCreated(request, serverSessionId)
            }
        }
    }

    private suspend fun sendUpdate(request: SessionRequest) {
        sessionsBackend.updateSession(
            request.appId,
            checkNotNull(request.serverSessionId),
            UpdateSessionRequest(
                onesignalId = request.onesignalId,
                subscriptionId = request.subscriptionId,
                durationSeconds = MILLISECONDS.toSeconds(request.activeDuration),
                idempotencyKey = request.idempotencyKey,
                endTime = request.endTime?.let { MILLISECONDS.toSeconds(it) },
            ),
        )
        synchronized(lock) {
            remove(request)
            complete(request.id, sent = true)
        }
    }

    private fun onCreated(
        create: SessionRequest,
        serverSessionId: String,
    ) {
        remove(create)
        complete(create.id, sent = true)
        newRecordsState.add(serverSessionId)
        createdSessionIds[create.localSessionId] = serverSessionId
        store.list().filter { !it.isCreate && it.isSameSession(create) }.forEach { it.serverSessionId = serverSessionId }

        // Serialized with SessionService, which clears the server ID when the next session starts.
        runOnSerialIO {
            val session = sessionModelStore.model
            if (session.sessionId == create.localSessionId) session.serverSessionId = serverSessionId
        }
    }

    private fun onFailure(
        request: SessionRequest,
        ex: BackendException,
    ) {
        val code = ex.statusCode
        val missingId = if (request.isCreate) request.onesignalId else request.serverSessionId
        val retryable =
            when (NetworkUtils.getResponseStatusType(code)) {
                // A record the backend just created may not be readable yet.
                NetworkUtils.ResponseStatusType.MISSING -> missingId != null && newRecordsState.isInMissingRetryWindow(missingId)
                // RETRYABLE also covers 4xx codes NetworkUtils doesn't list, like 413 and 422. Those won't succeed on retry.
                NetworkUtils.ResponseStatusType.RETRYABLE -> code !in CLIENT_ERROR_CODES || code in RETRYABLE_CLIENT_ERROR_CODES
                else -> false
            }
        if (!retryable) {
            Logging.warn("SessionRequestQueue: $request failed with $code, dropping")
            drop(request)
            return
        }
        // A status of 0 or less means no response, e.g. offline, which keeps retrying until reconnect.
        retryOrDrop(request, backendResponded = code > 0, retryAfterSeconds = ex.retryAfterSeconds)
    }

    /**
     * Attempts the backend answered are capped; attempts that never reached it are not.
     */
    private fun retryOrDrop(
        request: SessionRequest,
        backendResponded: Boolean,
        retryAfterSeconds: Int?,
    ) {
        if (backendResponded && ++request.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            Logging.warn("SessionRequestQueue: $request failed ${request.failedAttempts} times, dropping")
            drop(request)
            return
        }
        val attempt = (attempts[request.id] ?: 0) + 1
        attempts[request.id] = attempt
        val delayMs = retryDelayMs(attempt, retryAfterSeconds, Random)
        retryAt[request.id] = time.currentTimeMillis + delayMs
        Logging.info("SessionRequestQueue: $request failed, retrying in $delayMs ms")
    }

    /**
     * Drops [request] unsent, along with any updates that could then never get a server session ID.
     */
    private fun drop(request: SessionRequest) {
        remove(request)
        complete(request.id, sent = false)
        if (!request.isCreate) return

        store.list()
            .filter { !it.isCreate && it.isSameSession(request) && it.serverSessionId == null }
            .forEach { orphan ->
                Logging.debug("SessionRequestQueue: dropping $orphan, its session was never created")
                remove(orphan)
                complete(orphan.id, sent = false)
            }
    }

    private fun remove(request: SessionRequest) {
        store.remove(request.id)
        retryAt.remove(request.id)
        attempts.remove(request.id)
    }

    private fun complete(
        requestId: String,
        sent: Boolean,
    ) {
        waiters.remove(requestId)?.forEach { it.complete(sent) }
    }

    private fun registerNetworkCallback() {
        val connectivityManager =
            applicationService.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        try {
            connectivityManager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        // Android 15+ blocks requests from cached background apps; a blocked attempt only lengthens the backoff.
                        if (applicationService.isInForeground) retryNow()
                    }
                },
            )
        } catch (e: RuntimeException) {
            // Throws SecurityException on some OEM builds and when the per-app callback limit is reached.
            Logging.warn("SessionRequestQueue: unable to register a network callback", e)
        }
    }

    companion object {
        internal const val MAX_QUEUED_REQUESTS = 100
        internal const val MAX_FAILED_ATTEMPTS = 5

        private const val BASE_RETRY_DELAY_MS = 5_000L
        private const val MAX_RETRY_DELAY_MS = 300_000L
        private const val NEW_RECORD_POLL_MS = 1_000L
        private const val MILLIS_PER_SECOND = 1_000L

        private val CLIENT_ERROR_CODES = 400..499

        // Request Timeout and Too Many Requests
        private val RETRYABLE_CLIENT_ERROR_CODES = setOf(408, 429)

        /**
         * Exponential backoff, jittered over its upper half, or the server's Retry-After when longer.
         */
        internal fun retryDelayMs(
            attempt: Int,
            retryAfterSeconds: Int?,
            random: Random,
        ): Long {
            val ceiling = (BASE_RETRY_DELAY_MS * 2.0.pow(attempt - 1)).toLong().coerceAtMost(MAX_RETRY_DELAY_MS)
            val backoff = ceiling / 2 + random.nextLong(ceiling / 2 + 1)
            val retryAfterMs = (retryAfterSeconds ?: 0) * MILLIS_PER_SECOND
            return maxOf(backoff, retryAfterMs)
        }
    }
}
