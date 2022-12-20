package com.onesignal.session.internal.session

import com.onesignal.common.events.IEventNotifier

/**
 * The session service provides access to the user session.  It extends [IEventNotifier]
 * allowing users of this service to subscribe to session lifecycle events.
 *
 * @See [ISessionLifecycleHandler]
 */
interface ISessionService : IEventNotifier<ISessionLifecycleHandler> {

    /**
     * When the current session was started, in Unix time milliseconds.
     */
    val startTime: Long
}
