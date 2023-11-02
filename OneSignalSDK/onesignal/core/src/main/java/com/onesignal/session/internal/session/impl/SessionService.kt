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

    override fun start() {
        session = _sessionModelStore.model
        config = _configModelStore.model
        _applicationService.addApplicationLifecycleHandler(this)
    }

    override suspend fun backgroundRun() {
        Logging.log(LogLevel.DEBUG, "SessionService.backgroundRun()")

        if (!session!!.isValid) {
            return
        }

        // end the session
        Logging.debug("SessionService: Session ended. activeDuration: ${session!!.activeDuration}")
        session!!.isValid = false
        sessionLifeCycleNotifier.fire { it.onSessionEnded(session!!.activeDuration) }
    }

    override fun onFocus() {
        Logging.log(LogLevel.DEBUG, "SessionService.onFocus()")

        if (!session!!.isValid) {
            // As the old session was made inactive, we need to create a new session
            session!!.sessionId = UUID.randomUUID().toString()
            session!!.startTime = _time.currentTimeMillis
            session!!.focusTime = session!!.startTime
            session!!.activeDuration = 0L
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
        Logging.log(LogLevel.DEBUG, "SessionService.onUnfocused()")

        // capture the amount of time the app was focused
        val dt = _time.currentTimeMillis - session!!.focusTime
        session!!.activeDuration += dt
    }

    override fun subscribe(handler: ISessionLifecycleHandler) = sessionLifeCycleNotifier.subscribe(handler)

    override fun unsubscribe(handler: ISessionLifecycleHandler) = sessionLifeCycleNotifier.unsubscribe(handler)

    override val hasSubscribers: Boolean
        get() = sessionLifeCycleNotifier.hasSubscribers
}
