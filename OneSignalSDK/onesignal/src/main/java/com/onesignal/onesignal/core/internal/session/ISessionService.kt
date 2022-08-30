package com.onesignal.onesignal.core.internal.session

import com.onesignal.onesignal.core.internal.AppEntryAction
import com.onesignal.onesignal.core.internal.common.events.IEventNotifier
import com.onesignal.onesignal.core.internal.influence.Influence

internal interface ISessionService : IEventNotifier<ISessionLifecycleHandler> {
    val influences: List<Influence>
    val sessionInfluences: List<Influence>

    fun onNotificationReceived(notificationId: String)
    fun onInAppMessageReceived(messageId: String)
    fun onDirectInfluenceFromNotificationOpen(entryAction: AppEntryAction, notificationId: String?)
    fun directInfluenceFromIAMClick(messageId: String)
    fun directInfluenceFromIAMClickFinished()
}
