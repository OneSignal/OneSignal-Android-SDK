package com.onesignal.core.internal.session

import com.onesignal.core.internal.application.AppEntryAction
import com.onesignal.core.internal.common.events.IEventNotifier
import com.onesignal.core.internal.influence.Influence

internal interface ISessionService : IEventNotifier<ISessionLifecycleHandler> {
    val startTime: Long
    val influences: List<Influence>
    val sessionInfluences: List<Influence>

    fun onNotificationReceived(notificationId: String)
    fun onInAppMessageReceived(messageId: String)
    fun onDirectInfluenceFromNotificationOpen(entryAction: AppEntryAction, notificationId: String?)
    fun directInfluenceFromIAMClick(messageId: String)
    fun directInfluenceFromIAMClickFinished()
}
