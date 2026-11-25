package com.onesignal.example.data.model

data class CompositeLoginRequest(
    val externalId: String,
    val jwt: String? = null,
    val email: String? = null,
    val emailEnabled: Boolean? = null,
    val sms: String? = null,
    val smsEnabled: Boolean? = null,
    val tags: Map<String, String> = emptyMap(),
    val aliases: Map<String, String> = emptyMap(),
)
