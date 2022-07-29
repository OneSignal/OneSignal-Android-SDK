package com.onesignal.onesignal.notification.internal

import com.onesignal.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.onesignal.notification.INotificationsManager

object NotificationModule {
    fun register(builder: ServiceBuilder) {
        builder.register<NotificationsManager>()
               .provides<INotificationsManager>()
    }
}