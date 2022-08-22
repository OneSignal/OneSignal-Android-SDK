package com.onesignal.onesignal.core.internal.session

import com.onesignal.onesignal.core.internal.influence.Influence

interface ISessionLifecycleHandler  {
    fun sessionStarted()
    fun sessionEnding(influences: List<Influence>)
}
