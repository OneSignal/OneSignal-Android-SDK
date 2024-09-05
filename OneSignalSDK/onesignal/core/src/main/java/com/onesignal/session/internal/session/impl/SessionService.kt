package com.onesignal.session.internal.session.impl

import com.onesignal.common.events.EventProducer
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.background.IBackgroundService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.ISessionLifecycleHandler
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.session.internal.session.SessionModel
import com.onesignal.session.internal.session.SessionModelStore
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
 */
internal class SessionService(
    private val _applicationService: IApplicationService,
    private val _configModelStore: ConfigModelStore,
    private val _sessionModelStore: SessionModelStore,
    private val _time: ITime,
) : ISessionService, IStartableService, IBackgroundService, IApplicationLifecycleHandler {
    override val startTime: Long
        get() = session!!.startTime

    /**
     * Run in the background when the session would time out, only if a session is currently active.
     */
    override val scheduleBackgroundRunIn: Long?
        get() = if (session!!.isValid) config!!.sessionFocusTimeout else null

    private val sessionLifeCycleNotifier: EventProducer<ISessionLifecycleHandler> = EventProducer()
    private var session: SessionModel? = null
    private var config: ConfigModel? = null
    private var shouldFireOnSubscribe = false

    // True if app has been foregrounded at least once since the app started
    private var hasFocused = false

    override fun start() {
        session = _sessionModelStore.model
        config = _configModelStore.model
        _applicationService.addApplicationLifecycleHandler(this)
    }

    /** NOTE: This triggers more often than scheduleBackgroundRunIn defined above,
     * as it runs on the lowest IBackgroundService.scheduleBackgroundRunIn across
     * the SDK.
     */
    override suspend fun backgroundRun() {
        endSession()
    }

    private fun endSession() {
        if (!session!!.isValid) return
        val activeDuration = session!!.activeDuration
        Logging.debug("SessionService.backgroundRun: Session ended. activeDuration: $activeDuration")

        session!!.isValid = false
        sessionLifeCycleNotifier.fire { it.onSessionEnded(activeDuration) }
        session!!.activeDuration = 0L
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
        Logging.log(LogLevel.DEBUG, "SessionService.onFocus() - fired from start: $firedOnSubscribe")

        // Treat app cold starts as a new session, we attempt to end any previous session to do this.
        if (!hasFocused) {
            hasFocused = true
            endSession()
        }

        if (!session!!.isValid) {
            // As the old session was made inactive, we need to create a new session
            shouldFireOnSubscribe = firedOnSubscribe
            session!!.sessionId = UUID.randomUUID().toString()
            session!!.startTime = _time.currentTimeMillis
            session!!.focusTime = session!!.startTime
            session!!.isValid = true
            Logging.debug("SessionService: New session started at ${session!!.startTime}")
            sessionLifeCycleNotifier.fire { it.onSessionStarted() }
        } else {
            // existing session: just remember the focus time so we can calculate the active time
            // when onUnfocused is called.
            session!!.focusTime = _time.currentTimeMillis
            sessionLifeCycleNotifier.fire { it.onSessionActive() }
        }
    }

    override fun onUnfocused() {
        // capture the amount of time the app was focused
        val dt = _time.currentTimeMillis - session!!.focusTime
        session!!.activeDuration += dt
        Logging.log(LogLevel.DEBUG, "SessionService.onUnfocused adding time $dt for total: ${session!!.activeDuration}")
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
