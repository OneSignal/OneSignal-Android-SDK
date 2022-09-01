package com.onesignal.core.internal.session

import com.onesignal.core.internal.influence.Influence

interface ISessionLifecycleHandler {
    fun sessionStarted()
    fun sessionEnding(influences: List<Influence>)
}
