package com.onesignal.onesignal.notification.internal

import com.onesignal.onesignal.core.internal.service.IBootstrapService
import com.onesignal.onesignal.core.internal.service.IStartableService
import com.onesignal.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.onesignal.notification.INotificationsManager
import com.onesignal.onesignal.notification.internal.badges.BadgeCountUpdater
import com.onesignal.onesignal.notification.internal.common.INotificationQueryHelper
import com.onesignal.onesignal.notification.internal.common.NotificationQueryHelper
import com.onesignal.onesignal.notification.internal.data.INotificationDataController
import com.onesignal.onesignal.notification.internal.data.NotificationDataController
import com.onesignal.onesignal.notification.internal.data.NotificationSummaryManager
import com.onesignal.onesignal.notification.internal.receivereceipt.IReceiveReceiptService
import com.onesignal.onesignal.notification.internal.receivereceipt.ReceiveReceiptService
import com.onesignal.onesignal.notification.internal.bundle.NotificationBundleProcessor
import com.onesignal.onesignal.notification.internal.permissions.NotificationPermissionController
import com.onesignal.onesignal.notification.internal.restoration.NotificationRestoreWorkManager
import com.onesignal.onesignal.notification.internal.bundle.INotificationBundleProcessor
import com.onesignal.onesignal.notification.internal.generation.*
import com.onesignal.onesignal.notification.internal.generation.GenerateNotification
import com.onesignal.onesignal.notification.internal.channels.NotificationChannelManager
import com.onesignal.onesignal.notification.internal.generation.NotificationGenerationProcessor
import com.onesignal.onesignal.notification.internal.generation.NotificationLimitManager
import com.onesignal.onesignal.notification.internal.actions.NotificationOpenedProcessor
import com.onesignal.onesignal.notification.internal.restoration.NotificationRestoreProcessor

object NotificationModule {
    fun register(builder: ServiceBuilder) {
        builder.register<NotificationRestoreWorkManager>().provides<NotificationRestoreWorkManager>()
        builder.register<NotificationQueryHelper>().provides<INotificationQueryHelper>()
        builder.register<BadgeCountUpdater>().provides<BadgeCountUpdater>()
        builder.register<NotificationDataController>().provides<INotificationDataController>()
        builder.register<NotificationGenerationWorkManager>().provides<INotificationGenerationWorkManager>()
        builder.register<NotificationBundleProcessor>().provides<INotificationBundleProcessor>()
        builder.register<ReceiveReceiptService>().provides<IReceiveReceiptService>()
        builder.register<NotificationChannelManager>().provides<NotificationChannelManager>()
        builder.register<NotificationLimitManager>().provides<NotificationLimitManager>()
        builder.register<GenerateNotification>().provides<IGenerateNotification>()
        builder.register<NotificationGenerationProcessor>().provides<INotificationGenerationProcessor>()
        builder.register<NotificationRestoreProcessor>().provides<NotificationRestoreProcessor>()
        builder.register<NotificationSummaryManager>().provides<NotificationSummaryManager>()
        builder.register<NotificationOpenedProcessor>().provides<NotificationOpenedProcessor>()
        builder.register<NotificationPermissionController>()
               .provides<NotificationPermissionController>()
               .provides<IBootstrapService>()
        builder.register<NotificationsManager>()
               .provides<INotificationsManager>()
               .provides<IStartableService>()
    }
}