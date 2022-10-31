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
    private val _time: ITime
) : ISessionService, IStartableService, IBackgroundService, IApplicationLifecycleHandler {

    override val startTime: Long
        get() = _session!!.startTime

    /**
     * Run in the background when the session would time out, only if a session is currently active.
     */
    override val scheduleBackgroundRunIn: Long?
        get() = if (_session!!.isValid) _config!!.sessionFocusTimeout else null

    private val _sessionLifeCycleNotifier: EventProducer<ISessionLifecycleHandler> = EventProducer()
    private var _session: SessionModel? = null
    private var _config: ConfigModel? = null

    override fun start() {
        _session = _sessionModelStore.model
        _config = _configModelStore.model
        _applicationService.addApplicationLifecycleHandler(this)
    }

    override suspend fun backgroundRun() {
        Logging.log(LogLevel.DEBUG, "SessionService.run()")

        if (!_session!!.isValid) {
            return
        }

        // end the session
        Logging.debug("SessionService: Session ended. activeDuration: ${_session!!.activeDuration}")
        _session!!.isValid = false
        _sessionLifeCycleNotifier.fire { it.onSessionEnded(_session!!.activeDuration) }
    }

    override fun onFocus() {
        Logging.log(LogLevel.DEBUG, "SessionService.onFocus()")

        if (!_session!!.isValid) {
            // As the old session was made inactive, we need to create a new session
            _session!!.sessionId = UUID.randomUUID().toString()
            _session!!.startTime = _time.currentTimeMillis
            _session!!.focusTime = _session!!.startTime
            _session!!.activeDuration = 0L
            _session!!.isValid = true

            Logging.debug("SessionService: New session started at ${_session!!.startTime}")
            _sessionLifeCycleNotifier.fire { it.onSessionStarted() }
        } else {
            // existing session: just remember the focus time so we can calculate the active time
            // when onUnfocused is called.
            _session!!.focusTime = _time.currentTimeMillis
            _sessionLifeCycleNotifier.fire { it.onSessionActive() }
        }
    }

    override fun onUnfocused() {
        Logging.log(LogLevel.DEBUG, "SessionService.onUnfocused()")

        // capture the amount of time the app was focused
        val dt = _time.currentTimeMillis - _session!!.focusTime
        _session!!.activeDuration += dt
    }

    override fun subscribe(handler: ISessionLifecycleHandler) = _sessionLifeCycleNotifier.subscribe(handler)

    override fun unsubscribe(handler: ISessionLifecycleHandler) = _sessionLifeCycleNotifier.unsubscribe(handler)
}
