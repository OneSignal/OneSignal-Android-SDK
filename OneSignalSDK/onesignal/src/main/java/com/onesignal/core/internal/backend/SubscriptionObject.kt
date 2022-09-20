package com.onesignal.core.internal.backend

internal class SubscriptionObject(
    val id: String,
    val type: SubscriptionObjectType,
    val token: String? = null,
    val enabled: Boolean? = null,
    val notificationTypes: Int? = null,
    val sdk: String? = null,
    val deviceModel: String? = null,
    val deviceOS: String? = null,
    val rooted: Boolean? = null,
    val testType: Int? = null,
    val appVersion: String? = null,
    val netType: Int? = null,
    val carrier: String? = null,
    val webAuth: String? = null,
    val webP256: String? = null
)
