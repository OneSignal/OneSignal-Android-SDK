package com.onesignal.session.internal.session.impl

import com.onesignal.common.IDManager
import com.onesignal.common.events.EventProducer
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.Model
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.runOnSerialIO
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.background.IBackgroundService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.startup.IBootstrapService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.features.FeatureFlag
import com.onesignal.session.internal.session.ISessionLifecycleHandler
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.session.internal.session.SessionModel
import com.onesignal.session.internal.session.SessionModelStore
import com.onesignal.session.internal.session.operations.CreateSessionOperation
import com.onesignal.session.internal.session.operations.UpdateSessionOperation
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.jwt.JwtRequirement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KMutableProperty1

/**
 * The implementation for [ISessionService] will continue a session as long as the app remains
 * "active" (in the foreground).  If the app becomes "inactive" (moved to the background, or killed)
 * and does not become active within a certain time period, the session is ended.
 *
 * This implementation subscribes itself as a [IApplicationLifecycleHandler] to know when the app
 * has moved into/out of focus, and implements [IBackgroundService] to gain control in the background
 * some amount of time after losing focus.
 *
 * The time threshold for a session to expire is a configuration option: [ConfigModel.sessionFocusTimeout].
 *
 * While focused, a session that [SessionModel.usesSessionsApi] reports its duration every
 * [ConfigModel.sessionHeartbeatInterval] of active time, so the server keeps it open and a crash loses little duration.
 * It also reports its duration on each unfocus, so a session killed in the background isn't short.
 *
 * An anonymous user's session holds its create until the first unfocus, a heartbeat, or the session
 * end, so a login before then can move the session to that user. Under identity verification it
 * stays held while anonymous, since an anonymous user is never created.
 *
 * On a user switch, a held create moves to the new user. Otherwise a switch from a logged-in user
 * splits the session, and an anonymous user's login keeps it so it isn't counted twice.
 *
 * Time without privacy consent is not counted: losing consent while focused counts as an unfocus,
 * and regaining it while in the foreground counts as a focus. A session that started without
 * consent was never reported, so it starts over once consent is given.
 */
@Suppress("TooManyFunctions")
internal class SessionService(
    private val applicationService: IApplicationService,
    private val configModelStore: ConfigModelStore,
    private val sessionModelStore: SessionModelStore,
    private val time: ITime,
    private val featureManager: IFeatureManager,
    private val identityModelStore: IdentityModelStore,
    private val operationRepo: IOperationRepo,
) : ISessionService, IBootstrapService, IStartableService, IBackgroundService, IApplicationLifecycleHandler {
    // Not a constructor parameter, since the service container can only inject registered services.
    // Tests replace it with a test dispatcher so the end send stays on the virtual clock.
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    // Runs on the serial IO lane so it doesn't race the focus handlers. Not a constructor parameter
    // for the same reason as ioDispatcher; tests replace it to run the heartbeat on a virtual clock.
    internal var launchHeartbeat: (suspend () -> Unit) -> Job = OneSignalDispatchers::launchOnSerialIO
    private val heartbeat = Heartbeat()

    override val startTime: Long
        // Pre-bootstrap default returns "now" so call sites computing `_time.currentTimeMillis - startTime`
        // (e.g. IAM session-duration / SESSION_TIME triggers) see ~0ms elapsed instead of ~58 years
        // (which is what `0L` / Jan 1970 would produce).
        get() = session?.startTime ?: time.currentTimeMillis

    /**
     * Run in the background when the session would time out, only if a session is currently active.
     *
     * Returns null if [bootstrap] has not yet run -- callers that invoke us before bootstrap
     * (possible when init is still in flight) should treat this
     * as "no schedule needed" rather than crashing on a null-deref.
     */
    override val scheduleBackgroundRunIn: Long?
        get() {
            val session = this.session ?: return null
            val config = this.config ?: return null
            return if (session.isValid) config.sessionFocusTimeout else null
        }

    private val sessionLifeCycleNotifier: EventProducer<ISessionLifecycleHandler> = EventProducer()
    private var session: SessionModel? = null
    private var config: ConfigModel? = null
    private var shouldFireOnSubscribe = false

    // True if app has been foregrounded at least once since the app started
    private var hasFocused = false

    // Serial IO lane only.
    private var isFocused = false

    // Set while focused without privacy consent, so the next unfocus adds no time. Serial IO lane only.
    private var pausedForConsent = false

    // Serial IO lane only.
    private var startedWithoutConsent = false

    private val pendingDirectAttributionId = AtomicReference<String?>()

    override fun bootstrap() {
        session = sessionModelStore.model
        config = configModelStore.model
    }

    override fun start() {
        applicationService.addApplicationLifecycleHandler(this)
        configModelStore.subscribe(ConsentChangeHandler())
        configModelStore.subscribe(HeartbeatIntervalChangeHandler())
        identityModelStore.subscribe(PinnedLocalIdTranslator(IdentityConstants.ONESIGNAL_ID, SessionModel::onesignalId))
        identityModelStore.subscribe(UserSwitchHandler())
        configModelStore.subscribe(PinnedLocalIdTranslator(ConfigModel::pushSubscriptionId.name, SessionModel::subscriptionId))
    }

    private inner class UserSwitchHandler : ISingletonModelStoreChangeHandler<IdentityModel> {
        override fun onModelReplaced(
            model: IdentityModel,
            tag: String,
        ) {
            // A user refresh replaces the model for the same user.
            if (tag == ModelChangeTags.HYDRATE) return
            val onesignalId = model.takeIf { it.hasProperty(IdentityConstants.ONESIGNAL_ID) }?.onesignalId
            val externalId = model.externalId
            val subscriptionId = config?.pushSubscriptionId
            val nowMs = time.currentTimeMillis
            val elapsedMs = time.elapsedRealtimeMillis
            runOnSerialIO {
                val session = session?.takeIf { it.isValid && it.usesSessionsApi && !it.isSplitPending } ?: return@runOnSerialIO
                when {
                    session.isCreateHeld -> {
                        Logging.debug("SessionService: moving session ${session.sessionId} with its held create to the new user")
                        session.onesignalId = onesignalId
                        session.subscriptionId = subscriptionId
                        session.externalId = externalId
                        if (externalId != null) releaseHeldCreate(session)
                    }
                    session.externalId == null -> session.externalId = externalId
                    else -> handleSplit(nowMs, elapsedMs)
                }
            }
        }

        override fun onModelUpdated(
            args: ModelChangedArgs,
            tag: String,
        ) = Unit
    }

    /**
     * A session that starts before the user is created pins local IDs. When the backend replaces
     * a local ID in place, carry it over to the pinned copy. Changes from a non-local ID, such as a
     * login or user switch, leave the pinned IDs alone.
     */
    private inner class PinnedLocalIdTranslator<TModel : Model>(
        private val sourceProperty: String,
        private val pinnedId: KMutableProperty1<SessionModel, String?>,
    ) : ISingletonModelStoreChangeHandler<TModel> {
        override fun onModelReplaced(
            model: TModel,
            tag: String,
        ) = Unit

        override fun onModelUpdated(
            args: ModelChangedArgs,
            tag: String,
        ) {
            val localId =
                (args.oldValue as? String)?.takeIf { args.property == sourceProperty && IDManager.isLocalId(it) }
            val backendId = (args.newValue as? String)?.takeUnless { IDManager.isLocalId(it) }
            if (localId == null || backendId == null) return

            runOnSerialIO {
                val session = this@SessionService.session ?: return@runOnSerialIO
                if (pinnedId.get(session) == localId) pinnedId.set(session, backendId)
            }
        }
    }

    /**
     * A heartbeat armed before the params fetch, such as on first launch, would otherwise wait out
     * the stale interval before picking up the fetched one.
     */
    private inner class HeartbeatIntervalChangeHandler : ISingletonModelStoreChangeHandler<ConfigModel> {
        override fun onModelReplaced(
            model: ConfigModel,
            tag: String,
        ) = runOnSerialIO { heartbeat.restartIfRunning() }

        override fun onModelUpdated(
            args: ModelChangedArgs,
            tag: String,
        ) = Unit
    }

    private inner class ConsentChangeHandler : ISingletonModelStoreChangeHandler<ConfigModel> {
        override fun onModelReplaced(
            model: ConfigModel,
            tag: String,
        ) = onConsentChanged()

        override fun onModelUpdated(
            args: ModelChangedArgs,
            tag: String,
        ) {
            if (args.property == ConfigModel::consentRequired.name || args.property == ConfigModel::consentGiven.name) {
                onConsentChanged()
            }
        }
    }

    private fun onConsentChanged() {
        val nowMs = time.currentTimeMillis
        val elapsedMs = time.elapsedRealtimeMillis
        runOnSerialIO {
            // A focus that has not arrived yet checks consent itself.
            if (session == null || !hasFocused || !applicationService.isInForeground) return@runOnSerialIO
            val awaitingConsent = config?.isAwaitingConsent == true
            if (awaitingConsent && !pausedForConsent) {
                Logging.debug("SessionService: privacy consent revoked, pausing the session")
                handleOnUnfocused(nowMs, elapsedMs)
                pausedForConsent = true
            } else if (!awaitingConsent && pausedForConsent) {
                Logging.debug("SessionService: privacy consent given, resuming the session")
                if (pausedPastTimeout(nowMs)) endSession()?.let { operationRepo.enqueue(it) }
                handleOnFocus(false, nowMs, elapsedMs, null)
            }
        }
    }

    /** Like a return from the background, a pause this long starts the next session. */
    private fun pausedPastTimeout(nowMs: Long): Boolean {
        val unfocusTime = session?.takeIf { it.isValid && !startedWithoutConsent }?.unfocusTime
        val timeout = config?.sessionFocusTimeout
        return unfocusTime != null && timeout != null && nowMs - unfocusTime >= timeout
    }

    override fun attributeNextSessionTo(notificationId: String) {
        pendingDirectAttributionId.set(notificationId)
    }

    /** NOTE: This triggers more often than scheduleBackgroundRunIn defined above,
     * as it runs on the lowest IBackgroundService.scheduleBackgroundRunIn across
     * the SDK.
     */
    override suspend fun backgroundRun() {
        val end = endSession() ?: return
        // Without consent the end can't be sent, so it waits in the queue until consent is given.
        if (config?.isAwaitingConsent == true) {
            operationRepo.enqueue(end)
            return
        }
        // Android 15+ blocks network requests started after this job finishes, so send the end now
        // rather than after the queue's batching delay. Flushing is bounded to once per session.
        // On timeout the end stays queued and is sent on the next app open.
        // BackgroundManager runs this on Dispatchers.Unconfined, so without the switch the rest of its
        // loop would resume on the operation repo thread that wakes the wait.
        val sent =
            withTimeoutOrNull(END_SESSION_SEND_TIMEOUT_MS) {
                withContext(ioDispatcher) { operationRepo.enqueueAndWait(end, flush = true) }
            }
        Logging.debug("SessionService.backgroundRun: session end sent: $sent")
    }

    /**
     * @return the operation that ends the session through the sessions API, for the caller to enqueue.
     */
    private fun endSession(): UpdateSessionOperation? {
        // Defensive: if bootstrap() has not run yet, there is no session state to end.
        // This can happen when SyncJobService races with
        // an in-flight initWithContext that has not yet reached bootstrapServices().
        val session = this.session?.takeIf { it.isValid } ?: return null
        val activeDuration = session.activeDuration
        Logging.debug("SessionService.backgroundRun: Session ended. activeDuration: $activeDuration")

        // Read and reset before clearing isValid: after that, a focus on the serial IO lane can start
        // the next session and write these fields while this runs on the background thread.
        val usesSessionsApi = session.usesSessionsApi
        val end =
            if (usesSessionsApi && !session.isSplitPending && releaseHeldCreate(session)) endSessionOperation(session) else null
        session.isSplitPending = false
        if (session.isCreateHeld) {
            Logging.debug("SessionService: session ${session.sessionId} ended with its create held, not reporting it")
            session.isCreateHeld = false
        }
        session.activeDuration = 0L
        session.lastHeartbeatDuration = 0L
        session.lastHeartbeatTime = null
        session.isValid = false
        sessionLifeCycleNotifier.fire { it.onSessionEnded(activeDuration, usesSessionsApi) }
        return end
    }

    /**
     * NOTE: When `firedOnSubscribe = true`
     *
     * Typically, the app foregrounding will trigger this callback via the IApplicationService.
     * However, it is possible for OneSignal to initialize too late to capture the Android lifecycle callbacks.
     * In this case, the app is already foregrounded, so this method is fired immediately on subscribing
     * to the IApplicationService. Listeners of this service will not subscribe in time to capture
     * the `onSessionStarted()` callback here, so fire it when they themselves subscribe.
     */
    override fun onFocus(firedOnSubscribe: Boolean) {
        // Capture focus time on the caller's thread so session timestamps reflect lifecycle
        // arrival, not dispatcher latency (SDK-4506).
        val focusTimeMs = time.currentTimeMillis
        val focusElapsedMs = time.elapsedRealtimeMillis
        // Any focus consumes the open, so one that didn't bring the app forward (e.g. it opened a
        // browser) isn't credited to a later organic open. Entry state is read here because it
        // resets on unfocus, which can run before the serial IO lane gets to this focus.
        val directAttributionId =
            pendingDirectAttributionId.getAndSet(null)
                ?.takeIf { applicationService.entryState.isNotificationClick }
        runOnSerialIO {
            handleOnFocus(firedOnSubscribe, focusTimeMs, focusElapsedMs, directAttributionId)
        }
    }

    private fun handleOnFocus(
        firedOnSubscribe: Boolean,
        focusTimeMs: Long,
        focusElapsedMs: Long,
        directAttributionId: String?,
    ) {
        Logging.log(LogLevel.DEBUG, "SessionService.onFocus() - fired from start: $firedOnSubscribe")

        val session = this.session
        if (session == null) {
            Logging.warn("SessionService.onFocus called before bootstrap; ignoring.")
            return
        }

        // Treat app cold starts as a new session, we attempt to end any previous session to do this.
        if (!hasFocused) {
            hasFocused = true
            endSession()?.let { operationRepo.enqueue(it) }
        }

        if (!session.isValid || (startedWithoutConsent && config?.isAwaitingConsent != true)) {
            // As the old session was made inactive, we need to create a new session
            shouldFireOnSubscribe = firedOnSubscribe
            session.sessionId = UUID.randomUUID().toString()
            session.startTime = focusTimeMs
            startNextSession(session, focusTimeMs, focusElapsedMs, directAttributionId)
            session.isValid = true
            sessionLifeCycleNotifier.fire { it.onSessionStarted() }
        } else if (session.isSplitPending) {
            startNextSession(session, focusTimeMs, focusElapsedMs, directAttributionId)
            sessionLifeCycleNotifier.fire { it.onSessionActive() }
        } else {
            // existing session: just remember the focus time so we can calculate the active time
            // when onUnfocused is called.
            session.focusTime = focusTimeMs
            session.focusElapsedRealtime = focusElapsedMs
            sessionLifeCycleNotifier.fire { it.onSessionActive() }
        }
        isFocused = true
        pausedForConsent = config?.isAwaitingConsent == true
        if (session.usesSessionsApi && !pausedForConsent) heartbeat.start(session) else heartbeat.stop()
    }

    private fun startNextSession(
        session: SessionModel,
        startTimeMs: Long,
        startElapsedMs: Long,
        directAttributionId: String?,
    ) {
        session.sessionsApiStartTime = startTimeMs
        session.focusTime = startTimeMs
        session.focusElapsedRealtime = startElapsedMs
        session.unfocusTime = null
        session.isSplitPending = false
        session.onesignalId =
            identityModelStore.model
                .takeIf { it.hasProperty(IdentityConstants.ONESIGNAL_ID) }
                ?.onesignalId
        session.subscriptionId = config?.pushSubscriptionId
        session.appId = config?.appId
        session.localSessionId = IDManager.createLocalId()
        session.externalId = identityModelStore.model.externalId
        val create = startSessionsApi(session, directAttributionId)
        startedWithoutConsent = config?.isAwaitingConsent == true
        Logging.debug("SessionService: New session started at $startTimeMs")
        create?.let { operationRepo.enqueue(it) }
    }

    /**
     * Ends the current sessions API session at a user switch and starts the next one for the new
     * user. The app session goes on, so onSessionStarted and onSessionEnded don't fire. In the background, the
     * next one starts on the next focus.
     */
    private fun handleSplit(
        nowMs: Long,
        elapsedMs: Long,
    ) {
        val session = this.session ?: return
        val counting = isFocused && !pausedForConsent
        val end =
            when {
                !releaseHeldCreate(session) -> null
                counting -> {
                    val duration = session.activeDuration + session.focusDurationAt(elapsedMs)
                    updateSessionOperation(session, maxOf(duration, session.lastHeartbeatDuration), endTime = nowMs)
                }
                else -> endSessionOperation(session)
            }
        Logging.debug("SessionService: splitting session ${session.sessionId} at a user switch")
        end?.let { operationRepo.enqueue(it) }
        session.activeDuration = 0L
        session.lastHeartbeatDuration = 0L
        session.lastHeartbeatTime = null
        if (counting) {
            startNextSession(session, nowMs, elapsedMs, null)
            if (session.usesSessionsApi) heartbeat.start(session) else heartbeat.stop()
        } else {
            session.isSplitPending = true
        }
    }

    /**
     * Decides whether a new session reports through the sessions API. The flag must be on, and a
     * session the sessions API can't create falls back to legacy so it is still counted.
     *
     * @return the create to enqueue now, or null if the session uses legacy or its create is held.
     */
    private fun startSessionsApi(
        session: SessionModel,
        directAttributionId: String?,
    ): CreateSessionOperation? {
        val create =
            if (featureManager.isEnabled(FeatureFlag.SDK_SESSIONS_V2_API_CUTOVER)) {
                createSessionOperation(session, identityModelStore.model.externalId, directAttributionId)
            } else {
                null
            }
        session.usesSessionsApi = create != null
        session.isCreateHeld = create != null && create.externalId == null
        session.directAttributionId = directAttributionId.takeIf { session.isCreateHeld }
        return create?.takeUnless { session.isCreateHeld }
    }

    /**
     * Enqueues a held create. It must be enqueued before any of the session's updates, or the queue
     * rejects them.
     *
     * @return false if the create is still held, so the caller sends no update.
     */
    @Synchronized
    private fun releaseHeldCreate(session: SessionModel): Boolean {
        if (!session.isCreateHeld) return true
        val create = sendableHeldCreate(session)
        if (create != null) {
            session.isCreateHeld = false
            session.directAttributionId = null
            operationRepo.enqueue(create)
        }
        return create != null
    }

    /** @return the held create, or null while it must stay held. */
    private fun sendableHeldCreate(session: SessionModel): CreateSessionOperation? {
        val identity = identityModelStore.model
        val ownsSession = identity.hasProperty(IdentityConstants.ONESIGNAL_ID) && identity.onesignalId == session.onesignalId
        val externalId = identity.externalId.takeIf { ownsSession }
        val config = config
        // Under identity verification the queue drops an anonymous create whose user or subscription
        // is local, so it waits for a login.
        val waitsForLogin =
            externalId == null &&
                config?.useIdentityVerification == JwtRequirement.REQUIRED &&
                listOf(session.onesignalId, session.subscriptionId).any { it != null && IDManager.isLocalId(it) }
        val canSend =
            !waitsForLogin &&
                // The queue drops a create sent without consent.
                config?.isAwaitingConsent != true
        return if (canSend) createSessionOperation(session, externalId, session.directAttributionId) else null
    }

    /** Sends the session's cumulative duration every [ConfigModel.sessionHeartbeatInterval] of active time while focused. */
    private inner class Heartbeat {
        private var job: Job? = null
        private var session: SessionModel? = null

        @Suppress("TooGenericExceptionCaught")
        fun start(session: SessionModel) {
            stop()
            this.session = session
            job =
                launchHeartbeat {
                    // The serial IO scope has no exception handler, so an escaping failure would crash
                    // the app. Stop until the next focus instead, which also can't spin on a failed tick.
                    try {
                        tickForever(session)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logging.error("SessionService: heartbeat stopped", e)
                    }
                }
        }

        private suspend fun tickForever(session: SessionModel) {
            while (true) {
                // A non-positive interval would loop without suspending.
                val heartbeatIntervalMs =
                    config?.sessionHeartbeatInterval?.takeIf { it > 0 } ?: ConfigModel.DEFAULT_SESSION_HEARTBEAT_INTERVAL_MS
                val focusDuration = session.focusDurationAt(time.elapsedRealtimeMillis)
                val duration = session.activeDuration + focusDuration
                val remaining = session.lastHeartbeatDuration + heartbeatIntervalMs - duration
                if (remaining > 0) {
                    // Rechecked after waking since delay and elapsedRealtime can disagree.
                    delay(remaining)
                    continue
                }
                Logging.debug("SessionService: heartbeat with activeDuration: $duration")
                session.lastHeartbeatDuration = duration
                // Measured from the focus, so a wall-clock jump mid-focus can't move a killed session's end.
                session.lastHeartbeatTime = session.focusTime + focusDuration
                if (releaseHeldCreate(session)) {
                    updateSessionOperation(session, duration)?.let { operationRepo.enqueue(it) }
                }
            }
        }

        fun stop() {
            job?.cancel()
            job = null
            session = null
        }

        /** Re-arms the current wait so a new interval applies now, without starting a stopped heartbeat. */
        fun restartIfRunning() {
            val session = session ?: return
            if (job?.isActive == true) start(session)
        }
    }

    override fun onUnfocused() {
        // Capture on the caller's thread so activeDuration is unaffected by dispatcher latency.
        val unfocusTimeMs = time.currentTimeMillis
        val unfocusElapsedMs = time.elapsedRealtimeMillis
        runOnSerialIO {
            handleOnUnfocused(unfocusTimeMs, unfocusElapsedMs)
            sendCheckpoint()
        }
    }

    private fun sendCheckpoint() {
        val session =
            this.session
                ?.takeIf { it.isValid && it.usesSessionsApi && config?.isAwaitingConsent != true }
                ?.takeIf { releaseHeldCreate(it) }
                // The backend gets whole seconds, so a smaller change would repeat the last report.
                ?.takeIf { MILLISECONDS.toSeconds(it.activeDuration) > MILLISECONDS.toSeconds(it.lastHeartbeatDuration) }
                ?: return
        val duration = session.activeDuration
        session.lastHeartbeatDuration = duration
        Logging.debug("SessionService: checkpoint with activeDuration: $duration")
        // Android 15+ blocks network requests once a backgrounded app is cached, which can happen within
        // the queue's batching delay, so send now. Each flush needs a new focus and a longer duration.
        updateSessionOperation(session, duration)?.let { operationRepo.enqueue(it, flush = true) }
    }

    private fun handleOnUnfocused(
        unfocusTimeMs: Long,
        unfocusElapsedMs: Long,
    ) {
        val session = this.session
        if (session == null) {
            Logging.warn("SessionService.onUnfocused called before bootstrap; ignoring.")
            return
        }
        heartbeat.stop()
        isFocused = false
        if (pausedForConsent) {
            pausedForConsent = false
            return
        }
        // capture the amount of time the app was focused
        val dt =
            if (session.usesSessionsApi) {
                session.focusDurationAt(unfocusElapsedMs)
            } else {
                unfocusTimeMs - session.focusTime
            }
        session.activeDuration += dt
        session.unfocusTime = unfocusTimeMs
        session.lastHeartbeatTime = null
        Logging.log(LogLevel.DEBUG, "SessionService.onUnfocused adding time $dt for total: ${session.activeDuration}")
    }

    override fun subscribe(handler: ISessionLifecycleHandler) {
        sessionLifeCycleNotifier.subscribe(handler)
        // If a handler subscribes too late to capture the initial onSessionStarted.
        if (shouldFireOnSubscribe) handler.onSessionStarted()
    }

    override fun unsubscribe(handler: ISessionLifecycleHandler) = sessionLifeCycleNotifier.unsubscribe(handler)

    override val hasSubscribers: Boolean
        get() = sessionLifeCycleNotifier.hasSubscribers

    companion object {
        // Covers the post-create delay when the create is still queued, plus the request itself.
        private const val END_SESSION_SEND_TIMEOUT_MS = 30_000L
    }
}

private fun createSessionOperation(
    session: SessionModel,
    externalId: String?,
    directAttributionId: String?,
): CreateSessionOperation? =
    withSessionIds(session, "creating") { resolvedAppId, onesignalId, subscriptionId ->
        CreateSessionOperation(
            appId = resolvedAppId,
            localSessionId = session.localSessionId,
            onesignalId = onesignalId,
            subscriptionId = subscriptionId,
            startTime = session.sessionsApiStartTime,
            externalId = externalId,
            directAttributionId = directAttributionId,
        )
    }

/** Time in the current focus, which [SessionModel.activeDuration] doesn't include until unfocus. */
private fun SessionModel.focusDurationAt(elapsedRealtimeMs: Long): Long =
    // elapsedRealtime resets on reboot; drop the interval rather than count a negative one.
    (elapsedRealtimeMs - focusElapsedRealtime).coerceAtLeast(0L)

/**
 * An app killed while focused never adds its last focus to [SessionModel.activeDuration] or sets
 * [SessionModel.unfocusTime], so fall back to the last focus or heartbeat, whichever is later.
 */
private fun endSessionOperation(session: SessionModel): UpdateSessionOperation? =
    updateSessionOperation(
        session,
        activeDuration = maxOf(session.activeDuration, session.lastHeartbeatDuration),
        endTime = maxOf(session.unfocusTime ?: 0L, session.focusTime, session.lastHeartbeatTime ?: 0L),
    )

private fun updateSessionOperation(
    session: SessionModel,
    activeDuration: Long,
    endTime: Long? = null,
): UpdateSessionOperation? =
    withSessionIds(session, if (endTime != null) "ending" else "updating") { resolvedAppId, onesignalId, subscriptionId ->
        UpdateSessionOperation(
            appId = resolvedAppId,
            sessionId = session.localSessionId,
            onesignalId = onesignalId,
            subscriptionId = subscriptionId,
            activeDuration = activeDuration,
            endTime = endTime,
        )
    }

private fun <T> withSessionIds(
    session: SessionModel,
    action: String,
    block: (appId: String, onesignalId: String, subscriptionId: String) -> T,
): T? {
    val appId = session.appId
    val onesignalId = session.onesignalId
    val subscriptionId = session.subscriptionId
    if (appId == null || onesignalId == null || subscriptionId == null) {
        Logging.warn(
            "SessionService: not $action session ${session.sessionId}, missing appId: ${appId == null}, " +
                "onesignalId: ${onesignalId == null}, subscriptionId: ${subscriptionId == null}",
        )
        return null
    }
    return block(appId, onesignalId, subscriptionId)
}
