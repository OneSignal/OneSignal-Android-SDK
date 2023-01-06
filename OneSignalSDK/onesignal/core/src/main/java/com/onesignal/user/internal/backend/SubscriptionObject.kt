package com.onesignal.user.internal.backend

class SubscriptionObject(
    val id: String,
    val type: SubscriptionObjectType,
    val token: String? = null,
    val enabled: Boolean? = null,
    val notificationTypes: Int? = null,
    val sdk: String? = null,
    val deviceModel: String? = null,
    val deviceOS: String? = null,
    val rooted: Boolean? = null,
    val netType: Int? = null,
    val carrier: String? = null,
    val appVersion: String? = null
)
