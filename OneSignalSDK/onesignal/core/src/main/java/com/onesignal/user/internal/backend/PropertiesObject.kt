package com.onesignal.user.internal.backend

class PropertiesObject(
    val tags: Map<String, String?>? = null,
    val language: String? = null,
    val timezoneId: String? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val hasAtLeastOnePropertySet: Boolean
        get() = tags != null || language != null || timezoneId != null || country != null || latitude != null || longitude != null
}
