package com.onesignal.session.internal.session.impl

import com.onesignal.common.events.EventProducer
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
import com.onesignal.user.internal.identity.IdentityModelStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

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
 * [HEARTBEAT_INTERVAL_MS] of active time, so the server keeps it open and a crash loses little duration.
 */
internal class SessionService(
    private val applicationService: IApplicationService,
    private val configModelStore: ConfigModelStore,
    private val sessionModelStore: SessionModelStore,
    private val time: ITime,
    private val featureManager: IFeatureManager,
    private val identityModelStore: IdentityModelStore,
    private val operationRepo: IOperationRepo,
) : ISessionService, IBootstrapService, IStartableService, IBackgroundService, IApplicationLifecycleHandler {
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

    /**
     * Runs the heartbeat on the serial IO lane so it doesn't race the focus handlers. A var rather
     * than a constructor parameter so tests can replace it without tripping the IoC's reflection.
     */
    internal var launchHeartbeat: (suspend () -> Unit) -> Job = OneSignalDispatchers::launchOnSerialIO
    private var heartbeatJob: Job? = null
    private var lastHeartbeatDuration = 0L

    override fun bootstrap() {
        session = sessionModelStore.model
        config = configModelStore.model
    }

    override fun start() {
        applicationService.addApplicationLifecycleHandler(this)
    }

    /** NOTE: This triggers more often than scheduleBackgroundRunIn defined above,
     * as it runs on the lowest IBackgroundService.scheduleBackgroundRunIn across
     * the SDK.
     */
    override suspend fun backgroundRun() {
        val end = endSession() ?: return
        // Android 15+ blocks network requests started after this job finishes, so send the end now
        // rather than after the queue's batching delay. Flushing is bounded to once per session.
        // On timeout the end stays queued and is sent on the next app open.
        val sent = withTimeoutOrNull(END_SESSION_SEND_TIMEOUT_MS) { operationRepo.enqueueAndWait(end, flush = true) }
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

        session.isValid = false
        val end =
            if (session.usesSessionsApi) {
                // An app killed while focused has no unfocus after its last focus.
                session.toUpdateOperation(config?.appId, activeDuration, endTime = maxOf(session.unfocusTime ?: 0L, session.focusTime))
            } else {
                null
            }
        sessionLifeCycleNotifier.fire { it.onSessionEnded(activeDuration) }
        session.activeDuration = 0L
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
        runOnSerialIO {
            handleOnFocus(firedOnSubscribe, focusTimeMs, focusElapsedMs)
        }
    }

    private fun handleOnFocus(
        firedOnSubscribe: Boolean,
        focusTimeMs: Long,
        focusElapsedMs: Long,
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

        if (!session.isValid) {
            // As the old session was made inactive, we need to create a new session
            shouldFireOnSubscribe = firedOnSubscribe
            session.sessionId = UUID.randomUUID().toString()
            session.startTime = focusTimeMs
            session.focusTime = session.startTime
            session.focusElapsedRealtime = focusElapsedMs
            session.usesSessionsApi = featureManager.isEnabled(FeatureFlag.SDK_SESSIONS_V2_API_CUTOVER)
            session.onesignalId =
                identityModelStore.model
                    .takeIf { it.hasProperty(IdentityConstants.ONESIGNAL_ID) }
                    ?.onesignalId
            session.subscriptionId = config?.pushSubscriptionId
            session.serverSessionId = null
            session.isValid = true
            lastHeartbeatDuration = 0L
            Logging.debug("SessionService: New session started at ${session.startTime}")
            if (session.usesSessionsApi) session.toCreateOperation(config?.appId)?.let { operationRepo.enqueue(it) }
            sessionLifeCycleNotifier.fire { it.onSessionStarted() }
        } else {
            // existing session: just remember the focus time so we can calculate the active time
            // when onUnfocused is called.
            session.focusTime = focusTimeMs
            session.focusElapsedRealtime = focusElapsedMs
            sessionLifeCycleNotifier.fire { it.onSessionActive() }
        }
        startHeartbeat(session)
    }

    private fun startHeartbeat(session: SessionModel) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        if (!session.usesSessionsApi) return

        heartbeatJob =
            launchHeartbeat {
                while (true) {
                    val duration = session.activeDurationAt(time.elapsedRealtimeMillis)
                    val remaining = lastHeartbeatDuration + HEARTBEAT_INTERVAL_MS - duration
                    if (remaining > 0) {
                        // Rechecked after waking since delay and elapsedRealtime can disagree.
                        delay(remaining)
                        continue
                    }
                    Logging.debug("SessionService: heartbeat with activeDuration: $duration")
                    lastHeartbeatDuration = duration
                    session.toUpdateOperation(config?.appId, duration)?.let { operationRepo.enqueue(it) }
                }
            }
    }

    override fun onUnfocused() {
        // Capture on the caller's thread so activeDuration is unaffected by dispatcher latency.
        val unfocusTimeMs = time.currentTimeMillis
        val unfocusElapsedMs = time.elapsedRealtimeMillis
        runOnSerialIO {
            handleOnUnfocused(unfocusTimeMs, unfocusElapsedMs)
        }
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
        heartbeatJob?.cancel()
        heartbeatJob = null
        // capture the amount of time the app was focused
        val dt =
            if (session.usesSessionsApi) {
                // elapsedRealtime resets on reboot; drop the interval rather than count a negative one.
                (unfocusElapsedMs - session.focusElapsedRealtime).coerceAtLeast(0L)
            } else {
                unfocusTimeMs - session.focusTime
            }
        session.activeDuration += dt
        session.unfocusTime = unfocusTimeMs
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

        // Hardcoded until the interval is remotely configured.
        internal const val HEARTBEAT_INTERVAL_MS = 30 * 60 * 1000L
    }
}

/** [SessionModel.activeDuration] including the current focus, which hasn't been added yet. */
private fun SessionModel.activeDurationAt(elapsedRealtimeMs: Long): Long =
    activeDuration + (elapsedRealtimeMs - focusElapsedRealtime).coerceAtLeast(0L)

private fun SessionModel.toCreateOperation(appId: String?): CreateSessionOperation? =
    withSessionIds(appId, "creating") { resolvedAppId, onesignalId, subscriptionId ->
        CreateSessionOperation(
            appId = resolvedAppId,
            localSessionId = localSessionId,
            onesignalId = onesignalId,
            subscriptionId = subscriptionId,
            startTime = startTime,
        )
    }

private fun SessionModel.toUpdateOperation(
    appId: String?,
    activeDuration: Long,
    endTime: Long? = null,
): UpdateSessionOperation? =
    withSessionIds(appId, if (endTime != null) "ending" else "updating") { resolvedAppId, onesignalId, subscriptionId ->
        UpdateSessionOperation(
            appId = resolvedAppId,
            sessionId = serverSessionId ?: localSessionId,
            onesignalId = onesignalId,
            subscriptionId = subscriptionId,
            activeDuration = activeDuration,
            endTime = endTime,
        )
    }

private fun <T> SessionModel.withSessionIds(
    appId: String?,
    action: String,
    block: (appId: String, onesignalId: String, subscriptionId: String) -> T,
): T? {
    val onesignalId = onesignalId
    val subscriptionId = subscriptionId
    if (appId == null || onesignalId == null || subscriptionId == null) {
        Logging.warn(
            "SessionService: not $action session $sessionId, missing appId: ${appId == null}, " +
                "onesignalId: ${onesignalId == null}, subscriptionId: ${subscriptionId == null}",
        )
        return null
    }
    return block(appId, onesignalId, subscriptionId)
}
