package com.onesignal.example.data.model

/**
 * Runtime switches for [com.onesignal.example.notification.DemoNotificationServiceExtension].
 *
 * Every switch defaults to false. A fresh install behaves like a demo with no extension
 * registered at all, so the notifications the demo sends stay usable as a manual QA baseline.
 *
 * Only [enabled] has a UI toggle. Flip the others by changing the defaults in
 * SharedPreferenceUtil.getNotificationExtensionOptions (or by calling
 * cacheNotificationExtensionOptions) and rebuilding.
 */
data class NotificationExtensionOptions(
    val enabled: Boolean = false,
    val logDetails: Boolean = false,
    val applyExtender: Boolean = false,
    val forceHighImportanceChannel: Boolean = false,
    val delayDisplay: Boolean = false,
    val discard: Boolean = false,
)
