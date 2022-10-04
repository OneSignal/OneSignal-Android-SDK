package com.onesignal.core.internal.session

import com.onesignal.core.debug.LogLevel
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.common.events.EventProducer
import com.onesignal.core.internal.common.events.IEventNotifier
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.models.ConfigModel
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.models.SessionModel
import com.onesignal.core.internal.models.SessionModelStore
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import java.util.UUID

internal class SessionService(
    private val _applicationService: IApplicationService,
    private val _configModelStore: ConfigModelStore,
    private val _sessionModelStore: SessionModelStore,
    private val _time: ITime
) : ISessionService, IStartableService, IApplicationLifecycleHandler, IEventNotifier<ISessionLifecycleHandler> {

    override val startTime: Long
        get() = _session!!.startTime

    private val _sessionLifeCycleNotifier: EventProducer<ISessionLifecycleHandler> = EventProducer()
    private var _focusOutTime: Long = 0
    private var _session: SessionModel? = null
    private var _config: ConfigModel? = null

    override fun start() {
        _session = _sessionModelStore.get()
        _config = _configModelStore.get()
        _applicationService.addApplicationLifecycleHandler(this)
        createNewSession()
    }

    override fun onFocus() {
        Logging.log(LogLevel.DEBUG, "SessionService.onFocus()")

        val now = _time.currentTimeMillis
        var dt = now - _focusOutTime

        if (dt > (_config!!.sessionFocusTimeout * 60 * 1000)) {
            Logging.debug("SessionService: Session timeout reached")
            createNewSession()
        } else if (dt < 0) {
            // user is messing with system clock
            Logging.debug("SessionService: System clock changed to earlier than focus out time")
            createNewSession()
        } else { // just add to the unfocused duration
            _session!!.unfocusedDuration += dt
        }
    }

    override fun onUnfocused() {
        Logging.log(LogLevel.DEBUG, "SessionService.onUnfocused()")
        _focusOutTime = _time.currentTimeMillis
    }

    private fun createNewSession() {
        // no reason to maintain old session models, just overwrite
        _session!!.sessionId = UUID.randomUUID().toString()
        _session!!.startTime = _time.currentTimeMillis
        _session!!.unfocusedDuration = 0.0

        Logging.debug("SessionService: New session started at ${_session!!.startTime}")
        _sessionLifeCycleNotifier.fire { it.sessionStarted() }
    }

    override fun subscribe(handler: ISessionLifecycleHandler) = _sessionLifeCycleNotifier.subscribe(handler)

    override fun unsubscribe(handler: ISessionLifecycleHandler) = _sessionLifeCycleNotifier.unsubscribe(handler)
}
