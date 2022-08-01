package com.onesignal.onesignal.core.internal.session

import com.onesignal.onesignal.core.internal.common.events.IEventNotifier

interface ISessionService : IEventNotifier<ISessionLifecycleHandler> {

}