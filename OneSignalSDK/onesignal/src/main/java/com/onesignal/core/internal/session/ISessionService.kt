package com.onesignal.core.internal.session

import com.onesignal.core.internal.common.events.IEventNotifier

internal interface ISessionService : IEventNotifier<ISessionLifecycleHandler> {
    val startTime: Long
}
