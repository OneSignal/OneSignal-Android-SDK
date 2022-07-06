package com.onesignal.onesignal.internal.session

import com.onesignal.onesignal.internal.common.events.IEventNotifier

interface ISessionService : IEventNotifier<ISessionLifecycleHandler> {

}