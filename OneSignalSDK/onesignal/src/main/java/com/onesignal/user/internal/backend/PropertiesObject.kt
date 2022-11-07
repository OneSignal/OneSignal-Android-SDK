package com.onesignal.user.internal.backend

class PropertiesObject(
    val tags: Map<String, String>? = null,
    val language: String? = null,
    val timezoneId: UInt? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val locationType: Int? = null,
    val locationBackground: Boolean? = null,
    val locationTimestamp: Long? = null
)
