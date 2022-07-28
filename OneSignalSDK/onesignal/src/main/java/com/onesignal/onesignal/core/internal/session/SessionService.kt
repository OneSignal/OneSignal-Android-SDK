package com.onesignal.onesignal.core.internal.session

import com.onesignal.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.common.events.EventProducer
import com.onesignal.onesignal.core.internal.common.events.IEventNotifier
import com.onesignal.onesignal.core.internal.logging.LogLevel
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.models.ConfigModel
import com.onesignal.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.onesignal.core.internal.models.SessionModel
import com.onesignal.onesignal.core.internal.models.SessionModelStore
import com.onesignal.onesignal.core.internal.service.IStartableService
import java.util.*

internal class SessionService(
    private val _applicationService: IApplicationService,
    private val _configModelStore: ConfigModelStore,
    private val _sessionModelStore: SessionModelStore) : ISessionService, IStartableService, IApplicationLifecycleHandler, IEventNotifier<ISessionLifecycleHandler>  {

    private val _sessionLifeCycleNotifier: EventProducer<ISessionLifecycleHandler> = EventProducer()
    private var _focusOutTime: Date = Calendar.getInstance().time
    private var _session: SessionModel? = null
    private var _config: ConfigModel? = null

    override suspend fun start() {
        _session = _sessionModelStore.get()
        _config = _configModelStore.get()
        _applicationService.addApplicationLifecycleHandler(this)
    }

    override fun onFocus() {
        Logging.log(LogLevel.DEBUG, "SessionService.onFocus()")
        val now: Date = Calendar.getInstance().time

        var dt = now.time - _focusOutTime.time;

        if (dt > (_config!!.sessionFocusTimeout * 60 * 1000)) {
            Logging.debug("Session timeout reached");
            createNewSession();
        }
        else if (dt < 0) {
            // user is messing with system clock
            Logging.debug("System clock changed to earlier than focus out time");
            createNewSession();
        }
        else { // just add to the unfocused duration
            _session!!.unfocusedDuration += dt;
        }
    }

    override fun onUnfocused() {
        Logging.log(LogLevel.DEBUG, "SessionService.onUnfocused()")
        _focusOutTime = Calendar.getInstance().time;
    }

    private fun createNewSession() {
        // no reason to maintain old session models, just overwrite
        _session!!.id = UUID.randomUUID().toString()
        _session!!.startTime = Calendar.getInstance().time
        _session!!.unfocusedDuration = 0.0;

        Logging.debug("New session started at ${_session!!.startTime}");
        _sessionLifeCycleNotifier.fire { it.sessionStarted() }
    }

    override fun subscribe(handler: ISessionLifecycleHandler) = _sessionLifeCycleNotifier.subscribe(handler)

    override fun unsubscribe(handler: ISessionLifecycleHandler) = _sessionLifeCycleNotifier.unsubscribe(handler)
}
