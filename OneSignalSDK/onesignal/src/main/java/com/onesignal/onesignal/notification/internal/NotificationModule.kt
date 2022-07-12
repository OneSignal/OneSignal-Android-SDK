package com.onesignal.onesignal.notification.internal

import com.onesignal.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.onesignal.notification.internal.badges.BadgeCountUpdater
import com.onesignal.onesignal.notification.internal.common.NotificationQueryHelper
import com.onesignal.onesignal.notification.internal.data.INotificationDataController
import com.onesignal.onesignal.notification.internal.data.NotificationDataController
import com.onesignal.onesignal.notification.internal.data.NotificationSummaryManager
import com.onesignal.onesignal.notification.internal.generation.GenerateNotification
import com.onesignal.onesignal.notification.internal.generation.NotificationChannelManager
import com.onesignal.onesignal.notification.internal.generation.NotificationLimitManager
import com.onesignal.onesignal.notification.internal.generation.NotificationOpenedProcessor
import com.onesignal.onesignal.notification.internal.receipt.IReceiveReceiptService
import com.onesignal.onesignal.notification.internal.receipt.ReceiveReceiptService
import com.onesignal.onesignal.notification.internal.work.NotificationBundleProcessor
import com.onesignal.onesignal.notification.internal.work.NotificationGenerationProcessor
import com.onesignal.onesignal.notification.internal.generation.IGenerateNotification
import com.onesignal.onesignal.notification.internal.work.*
import com.onesignal.onesignal.notification.internal.work.INotificationBundleProcessor
import com.onesignal.onesignal.notification.internal.work.NotificationRestoreProcessor

object NotificationModule {
    fun register(builder: ServiceBuilder) {
        builder.register<NotificationQueryHelper>().provides<NotificationQueryHelper>()
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
        builder.register<NotificationsManager>().provides<NotificationsManager>()
    }
}