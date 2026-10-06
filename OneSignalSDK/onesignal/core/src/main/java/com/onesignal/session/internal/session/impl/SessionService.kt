package com.onesignal.session.internal.session.impl

import com.onesignal.common.IDManager
import com.onesignal.common.events.EventProducer
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.Model
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.threading.runOnSerialIO
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.background.IBackgroundService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.features.IFeatureManager
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
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.identity.IdentityModelStore
import java.util.UUID
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
 */
internal class SessionService(
    private val _applicationService: IApplicationService,
    private val _configModelStore: ConfigModelStore,
    private val _sessionModelStore: SessionModelStore,
    private val _time: ITime,
    private val featureManager: IFeatureManager,
    private val identityModelStore: IdentityModelStore,
) : ISessionService, IBootstrapService, IStartableService, IBackgroundService, IApplicationLifecycleHandler {
    override val startTime: Long
        // Pre-bootstrap default returns "now" so call sites computing `_time.currentTimeMillis - startTime`
        // (e.g. IAM session-duration / SESSION_TIME triggers) see ~0ms elapsed instead of ~58 years
        // (which is what `0L` / Jan 1970 would produce).
        get() = session?.startTime ?: _time.currentTimeMillis

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

    override fun bootstrap() {
        session = _sessionModelStore.model
        config = _configModelStore.model
    }

    override fun start() {
        _applicationService.addApplicationLifecycleHandler(this)
        identityModelStore.subscribe(PinnedLocalIdTranslator(IdentityConstants.ONESIGNAL_ID, SessionModel::onesignalId))
        _configModelStore.subscribe(PinnedLocalIdTranslator(ConfigModel::pushSubscriptionId.name, SessionModel::subscriptionId))
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

    /** NOTE: This triggers more often than scheduleBackgroundRunIn defined above,
     * as it runs on the lowest IBackgroundService.scheduleBackgroundRunIn across
     * the SDK.
     */
    override suspend fun backgroundRun() {
        endSession()
    }

    private fun endSession() {
        // Defensive: if bootstrap() has not run yet, there is no session state to end.
        // This can happen when SyncJobService races with
        // an in-flight initWithContext that has not yet reached bootstrapServices().
        val session = this.session ?: return
        if (!session.isValid) return
        val activeDuration = session.activeDuration
        Logging.debug("SessionService.backgroundRun: Session ended. activeDuration: $activeDuration")

        session.isValid = false
        sessionLifeCycleNotifier.fire { it.onSessionEnded(activeDuration) }
        session.activeDuration = 0L
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
        val focusTimeMs = _time.currentTimeMillis
        val focusElapsedMs = _time.elapsedRealtimeMillis
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
            endSession()
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
            Logging.debug("SessionService: New session started at ${session.startTime}")
            sessionLifeCycleNotifier.fire { it.onSessionStarted() }
        } else {
            // existing session: just remember the focus time so we can calculate the active time
            // when onUnfocused is called.
            session.focusTime = focusTimeMs
            session.focusElapsedRealtime = focusElapsedMs
            sessionLifeCycleNotifier.fire { it.onSessionActive() }
        }
    }

    override fun onUnfocused() {
        // Capture on the caller's thread so activeDuration is unaffected by dispatcher latency.
        val unfocusTimeMs = _time.currentTimeMillis
        val unfocusElapsedMs = _time.elapsedRealtimeMillis
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
        // capture the amount of time the app was focused
        val dt =
            if (session.usesSessionsApi) {
                // elapsedRealtime resets on reboot; drop the interval rather than count a negative one.
                (unfocusElapsedMs - session.focusElapsedRealtime).coerceAtLeast(0L)
            } else {
                unfocusTimeMs - session.focusTime
            }
        session.activeDuration += dt
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
}
